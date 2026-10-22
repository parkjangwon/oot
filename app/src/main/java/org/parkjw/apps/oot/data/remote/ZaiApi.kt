package org.parkjw.apps.oot.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
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
import kotlin.math.roundToInt

class ZaiApi(
    private val client: OkHttpClient,
    private val json: Json
) {
    suspend fun fetchStatus(account: ProviderAccount): TokenStatus = withContext(Dispatchers.IO) {
        val credential = account.activeCredential().trim().removePrefix("Bearer ").trim()
        if (credential.isBlank()) {
            return@withContext TokenStatus(
                accountId = account.id,
                provider = ProviderType.ZAI,
                accountName = account.name,
                remainingFormatted = AppText.get(R.string.data_no_api_key),
                statusSummary = AppText.get(R.string.data_key_needed),
                isError = true,
                errorMessage = AppText.get(R.string.data_zai_key_required)
            )
        }

        val baseUrl = resolveBaseUrl(account.customEndpoint, ProviderType.ZAI.baseUrl)
            ?: return@withContext customEndpointErrorStatus(account)
        // Official Z.ai GLM Coding Plan monitoring endpoint
        val quotaLimitUrl = "$baseUrl/api/monitor/usage/quota/limit"
        val authHeader = "Bearer $credential"

        try {
            val request = Request.Builder()
                .url(quotaLimitUrl)
                .addHeader("Authorization", authHeader)
                .addHeader("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()

                if (response.isSuccessful) {
                    val usage = parseZaiUsage(body, json)
                    if (usage.windows.isNotEmpty()) {
                        return@withContext successStatus(account, usage)
                    }

                    // Fallback if no 5-hour/weekly limit is reported but 200 OK
                    return@withContext TokenStatus(
                        accountId = account.id,
                        provider = ProviderType.ZAI,
                        accountName = account.name,
                        remainingFormatted = AppText.get(R.string.data_zai_plan_active, usage.level),
                        totalLimitFormatted = AppText.get(R.string.data_working_normal),
                        statusSummary = AppText.get(R.string.data_zai_ok),
                        isWarning = false
                    )
                }

                if (response.code == 429) {
                    return@withContext rateLimitedTokenStatus(
                        account,
                        ProviderType.ZAI,
                        response.header("Retry-After"),
                    )
                }

                if (response.code == 401 || response.code == 403) {
                    return@withContext TokenStatus(
                        accountId = account.id,
                        provider = ProviderType.ZAI,
                        accountName = account.name,
                        remainingFormatted = AppText.get(R.string.data_auth_failed_code, response.code),
                        statusSummary = AppText.get(R.string.data_zai_auth_failed),
                        isError = true,
                        errorMessage = AppText.get(R.string.data_zai_key_invalid)
                    )
                }

                TokenStatus(
                    accountId = account.id,
                    provider = ProviderType.ZAI,
                    accountName = account.name,
                    remainingFormatted = "HTTP ${response.code}",
                    statusSummary = AppText.get(R.string.data_error_code, response.code),
                    isError = true,
                    errorMessage = AppText.get(R.string.data_zai_server_error, response.code)
                )
            }
        } catch (e: Exception) {
            TokenStatus(
                accountId = account.id,
                provider = ProviderType.ZAI,
                accountName = account.name,
                remainingFormatted = AppText.get(R.string.data_error_network),
                statusSummary = AppText.get(R.string.data_connection_failed),
                isError = true,
                errorMessage = e.localizedMessage ?: AppText.get(R.string.data_connection_failed)
            )
        }
    }

    private fun successStatus(account: ProviderAccount, usage: ZaiUsage): TokenStatus {
        val windows = usage.windows
        val first = windows.first()
        val maxUsed = windows.maxOf { it.percentUsed }
        val limitText = windows.joinToString(" | ") { w ->
            val reset = w.resetsAt?.let { formatRelativeTime(it.toEpochMilli()) }
            AppText.get(R.string.data_window_used, w.label, w.percentUsed.roundToInt()) +
                (reset?.let { " ($it)" } ?: "")
        } + " [${usage.level.uppercase()}]"

        return TokenStatus(
            accountId = account.id,
            provider = ProviderType.ZAI,
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
}
