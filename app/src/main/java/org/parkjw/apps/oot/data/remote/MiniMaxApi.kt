package org.parkjw.apps.oot.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.parkjw.apps.oot.R
import org.parkjw.apps.oot.data.model.ProviderAccount
import org.parkjw.apps.oot.data.model.ProviderType
import org.parkjw.apps.oot.data.model.TokenStatus
import org.parkjw.apps.oot.i18n.AppText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

class MiniMaxApi(
    private val client: OkHttpClient,
    private val json: Json
) {
    // A plan key works in one region only; remember the base that answered so later syncs skip the retry.
    private val workingBaseByAccount = ConcurrentHashMap<String, String>()

    suspend fun fetchStatus(account: ProviderAccount): TokenStatus = withContext(Dispatchers.IO) {
        val credential = account.activeCredential().trim().removePrefix("Bearer ").trim()
        if (credential.isBlank()) {
            return@withContext errorStatus(
                account,
                remainingFormatted = AppText.get(R.string.data_no_api_key),
                message = AppText.get(R.string.data_minimax_key_required)
            )
        }

        val customBase = resolveBaseUrl(account.customEndpoint, ProviderType.MINIMAX.baseUrl)
            ?: return@withContext customEndpointErrorStatus(account)
        val bases = candidateBases(account, customBase)
        var lastAuthCode = 401

        for ((index, base) in bases.withIndex()) {
            val isLast = index == bases.lastIndex
            when (val attempt = attempt(base, credential)) {
                is Attempt.Ok -> {
                    if (attempt.windows.isEmpty()) {
                        return@withContext errorStatus(
                            account,
                            remainingFormatted = AppText.get(R.string.data_no_usage_info),
                            message = AppText.get(R.string.data_minimax_no_usage)
                        )
                    }
                    workingBaseByAccount[account.id] = base
                    return@withContext successStatus(account, attempt.windows)
                }
                is Attempt.Auth -> {
                    lastAuthCode = attempt.code
                    if (isLast) return@withContext authErrorStatus(account, lastAuthCode)
                }
                is Attempt.ApiError -> {
                    if (isAuthError(attempt.error.statusCode) && !isLast) {
                        lastAuthCode = 401
                    } else if (isAuthError(attempt.error.statusCode)) {
                        return@withContext authErrorStatus(account, lastAuthCode)
                    } else {
                        return@withContext errorStatus(
                            account,
                            remainingFormatted = AppText.get(R.string.data_error_code, attempt.error.statusCode),
                            message = redact(attempt.error.statusMessage, credential)
                                .ifBlank { AppText.get(R.string.data_error_generic) }
                        )
                    }
                }
                Attempt.Invalid -> return@withContext errorStatus(
                    account,
                    remainingFormatted = AppText.get(R.string.data_fetch_failed),
                    message = AppText.get(R.string.data_minimax_parse_failed)
                )
                is Attempt.RateLimited -> return@withContext rateLimitedTokenStatus(
                    account,
                    ProviderType.MINIMAX,
                    attempt.retryAfter
                )
                is Attempt.Http -> return@withContext errorStatus(
                    account,
                    remainingFormatted = "HTTP ${attempt.code}",
                    message = AppText.get(R.string.data_minimax_server_error, attempt.code)
                )
                is Attempt.Network -> return@withContext errorStatus(
                    account,
                    remainingFormatted = AppText.get(R.string.data_error_network),
                    message = redact(attempt.message, credential)
                        .ifBlank { AppText.get(R.string.data_connection_failed) }
                )
            }
        }

        authErrorStatus(account, lastAuthCode)
    }

    private fun candidateBases(account: ProviderAccount, customBase: String): List<String> {
        if (!account.customEndpoint.isNullOrBlank()) return listOf(customBase)
        return when (workingBaseByAccount[account.id]) {
            CN_BASE -> listOf(CN_BASE, GLOBAL_BASE)
            else -> listOf(GLOBAL_BASE, CN_BASE)
        }
    }

    private fun attempt(base: String, credential: String): Attempt {
        val request = Request.Builder()
            .url("$base/v1/token_plan/remains")
            .addHeader("Authorization", "Bearer $credential")
            .addHeader("Accept", "application/json")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                when {
                    response.isSuccessful -> when (val result = parseMiniMaxUsage(body, json)) {
                        is MiniMaxUsageResult.Success -> Attempt.Ok(result.windows)
                        is MiniMaxUsageResult.ApiError ->
                            if (isAuthError(result.statusCode)) Attempt.Auth(401)
                            else Attempt.ApiError(result)
                        MiniMaxUsageResult.InvalidResponse -> Attempt.Invalid
                    }
                    response.code == 429 -> Attempt.RateLimited(response.header("Retry-After"))
                    response.code == 401 || response.code == 403 -> Attempt.Auth(response.code)
                    else -> Attempt.Http(response.code)
                }
            }
        } catch (e: Exception) {
            Attempt.Network(e.localizedMessage)
        }
    }

    private fun successStatus(account: ProviderAccount, windows: List<MiniMaxWindowUsage>): TokenStatus {
        val first = windows.first()
        val maxUsed = windows.maxOf { it.percentUsed }
        val limitText = windows.joinToString(" | ") { w ->
            val reset = w.resetsAt?.let { formatRelativeTime(it.toEpochMilli()) }
            AppText.get(R.string.data_window_used, w.label, w.percentUsed.roundToInt()) +
                (reset?.let { " ($it)" } ?: "")
        }

        return TokenStatus(
            accountId = account.id,
            provider = ProviderType.MINIMAX,
            accountName = account.name,
            remainingFormatted = windows.joinToString(" | ") {
                AppText.get(R.string.data_window_left, it.label, it.percentLeft.roundToInt())
            },
            totalLimitFormatted = limitText,
            usagePercent = first.percentUsed.toFloat(),
            resetTimeFormatted = first.resetsAt?.let { "${first.label} ${formatRelativeTime(it.toEpochMilli())}" },
            statusSummary = windows.joinToString(" | ") { "${it.label} ${it.percentLeft.roundToInt()}%" },
            isWarning = maxUsed > 80,
            isCritical = maxUsed > 95,
            windows = windows.map { it.toUsageWindow() }
        )
    }

    private fun authErrorStatus(account: ProviderAccount, code: Int): TokenStatus = TokenStatus(
        accountId = account.id,
        provider = ProviderType.MINIMAX,
        accountName = account.name,
        remainingFormatted = AppText.get(R.string.data_auth_failed_code, code),
        statusSummary = AppText.get(R.string.data_minimax_auth_failed),
        isError = true,
        errorMessage = AppText.get(R.string.data_minimax_key_invalid)
    )

    private fun errorStatus(
        account: ProviderAccount,
        remainingFormatted: String,
        message: String?
    ): TokenStatus = TokenStatus(
        accountId = account.id,
        provider = ProviderType.MINIMAX,
        accountName = account.name,
        remainingFormatted = remainingFormatted,
        statusSummary = AppText.get(R.string.status_needs_check),
        isError = true,
        errorMessage = message
    )

    // Never let the API key reach a status or log line.
    private fun redact(text: String?, credential: String): String =
        text.orEmpty().replace(credential, "***")

    private fun formatRelativeTime(timestampMs: Long): String {
        val now = System.currentTimeMillis()
        val diffMs = timestampMs - now
        return if (diffMs > 0) {
            val totalMinutes = diffMs / (1000 * 60)
            val hours = totalMinutes / 60
            val minutes = totalMinutes % 60
            val days = hours / 24

            when {
                days > 0 -> AppText.get(R.string.data_reset_in_days_hours, days, hours % 24)
                hours > 0 -> AppText.get(R.string.data_reset_in_hours_minutes, hours, minutes)
                else -> AppText.get(R.string.data_reset_in_minutes, minutes)
            }
        } else {
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            AppText.get(R.string.data_reset_at, sdf.format(Date(timestampMs)))
        }
    }

    private sealed interface Attempt {
        data class Ok(val windows: List<MiniMaxWindowUsage>) : Attempt
        data class Auth(val code: Int) : Attempt
        data class ApiError(val error: MiniMaxUsageResult.ApiError) : Attempt
        data object Invalid : Attempt
        data class RateLimited(val retryAfter: String?) : Attempt
        data class Http(val code: Int) : Attempt
        data class Network(val message: String?) : Attempt
    }

    private companion object {
        const val GLOBAL_BASE = "https://api.minimax.io"
        const val CN_BASE = "https://api.minimax.cn"

        // base_resp authentication errors: MiniMax reports a wrong-region key with HTTP 200.
        val AUTH_ERROR_CODES = setOf(1004, 2049)

        fun isAuthError(statusCode: Int): Boolean = statusCode in AUTH_ERROR_CODES
    }
}
