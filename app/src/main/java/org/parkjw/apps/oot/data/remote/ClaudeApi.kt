package org.parkjw.apps.oot.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.parkjw.apps.oot.R
import org.parkjw.apps.oot.data.model.AuthType
import org.parkjw.apps.oot.data.model.ProviderAccount
import org.parkjw.apps.oot.data.model.ProviderType
import org.parkjw.apps.oot.data.model.Remaining
import org.parkjw.apps.oot.data.model.TokenStatus
import org.parkjw.apps.oot.data.model.UsageWindow
import org.parkjw.apps.oot.data.model.VendorError
import org.parkjw.apps.oot.data.oauth.OAuthManager
import org.parkjw.apps.oot.i18n.AppText
import java.io.IOException
import java.time.Instant
import java.util.Locale

class ClaudeApi(
    private val client: OkHttpClient,
    private val json: Json,
    private val oauthManager: OAuthManager,
    private val onAccountRefreshed: (ProviderAccount) -> Unit
) {
    // accountId -> last successful usage status, reused when /api/oauth/usage returns 429
    private val lastGoodUsage = HashMap<String, TokenStatus>()

    suspend fun fetchStatus(account: ProviderAccount): TokenStatus = withContext(Dispatchers.IO) {
        val credential = account.activeCredential().trim()
        if (credential.isBlank()) {
            return@withContext TokenStatus(
                accountId = account.id,
                provider = ProviderType.CLAUDE,
                accountName = account.name,
                remainingFormatted = AppText.get(R.string.data_no_token_key),
                statusSummary = AppText.get(R.string.data_key_needed),
                isError = true,
                errorMessage = AppText.get(R.string.data_claude_key_required)
            )
        }

        val baseUrl = resolveBaseUrl(account.customEndpoint, ProviderType.CLAUDE.baseUrl)
            ?: return@withContext customEndpointErrorStatus(account)
        val isOAuth = account.authType == AuthType.OAUTH || credential.startsWith("Bearer ", ignoreCase = true) || credential.startsWith("sk-ant-oat", ignoreCase = true)
        val cleanToken = credential.removePrefix("Bearer ").trim()

        if (isOAuth) {
            return@withContext fetchUsageStatus(account, cleanToken, allowRefresh = true)
        }

        // API-key accounts: GET /v1/models
        val modelsUrl = "$baseUrl/models"
        val modelsReq = Request.Builder()
            .url(modelsUrl)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("x-api-key", cleanToken)
            .addHeader("User-Agent", USER_AGENT)
            .build()

        val response = try {
            client.newCall(modelsReq).execute()
        } catch (e: IOException) {
            Log.w(TAG, "models request failed endpoint=/v1/models error=IOException")
            return@withContext errorStatus(account, claudeErrorText(VendorError.Network))
        } catch (e: Exception) {
            Log.w(TAG, "models request failed endpoint=/v1/models error=${e.javaClass.simpleName}")
            return@withContext errorStatus(account, claudeErrorText(VendorError.Network))
        }

        response.use { resp ->
            Log.i(TAG, "models response endpoint=/v1/models http=${resp.code}")
            when {
                resp.code == 401 -> errorStatus(account, claudeErrorText(VendorError.Unauthorized))
                resp.isSuccessful -> {
                    val tokensLimit = resp.header("anthropic-ratelimit-tokens-limit")?.toLongOrNull()
                    val tokensRemaining = resp.header("anthropic-ratelimit-tokens-remaining")?.toLongOrNull()
                    val tokensReset = resp.header("anthropic-ratelimit-tokens-reset")

                    if (tokensLimit != null && tokensRemaining != null && tokensLimit > 0) {
                        val usedTokens = tokensLimit - tokensRemaining
                        val percent = ((usedTokens.toDouble() / tokensLimit.toDouble()) * 100).toFloat().coerceIn(0f, 100f)

                        return@use TokenStatus(
                            accountId = account.id,
                            provider = ProviderType.CLAUDE,
                            accountName = account.name,
                            remainingFormatted = AppText.get(
                                R.string.data_tokens_left,
                                String.format(Locale.US, "%,d", tokensRemaining)
                            ),
                            totalLimitFormatted = AppText.get(
                                R.string.data_limit_tokens_per_min,
                                String.format(Locale.US, "%,d", tokensLimit)
                            ),
                            usagePercent = percent,
                            resetTimeFormatted = tokensReset?.let { AppText.get(R.string.data_reset_value, it) },
                            statusSummary = AppText.get(
                                R.string.data_k_left,
                                String.format(Locale.US, "%,d", tokensRemaining / 1000)
                            ),
                            isWarning = percent > 80f,
                            isCritical = percent > 95f
                        )
                    }

                    TokenStatus(
                        accountId = account.id,
                        provider = ProviderType.CLAUDE,
                        accountName = account.name,
                        remainingFormatted = AppText.get(R.string.data_claude_connected),
                        totalLimitFormatted = AppText.get(R.string.data_working_normal),
                        statusSummary = AppText.get(R.string.data_status_ok),
                        isWarning = false
                    )
                }
                else -> {
                    val error = mapClaudeHttpError(resp.code)
                    Log.w(TAG, "models http error code=${resp.code} type=${error.javaClass.simpleName}")
                    errorStatus(account, claudeErrorText(error))
                }
            }
        }
    }

    private suspend fun fetchUsageStatus(account: ProviderAccount, token: String, allowRefresh: Boolean): TokenStatus {
        val request = Request.Builder()
            .url(USAGE_URL)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("anthropic-beta", "oauth-2025-04-20")
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Content-Type", "application/json")
            .build()

        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            Log.w(TAG, "usage request failed endpoint=/api/oauth/usage error=IOException")
            return errorStatus(account, claudeErrorText(VendorError.Network))
        } catch (e: Exception) {
            Log.w(TAG, "usage request failed endpoint=/api/oauth/usage error=${e.javaClass.simpleName}")
            return errorStatus(account, claudeErrorText(VendorError.Network))
        }

        response.use { resp ->
            Log.i(TAG, "usage response endpoint=/api/oauth/usage http=${resp.code} bytes=${resp.body?.contentLength() ?: -1L}")
            when {
                resp.isSuccessful -> {
                    val body = resp.body?.string().orEmpty()
                    val windows = parseClaudeUsage(body, json)
                    if (windows == null) {
                        Log.w(TAG, "usage parse failed endpoint=/api/oauth/usage error=Parse bytes=${body.toByteArray().size}")
                        return errorStatus(account, claudeErrorText(VendorError.Parse))
                    }
                    Log.i(TAG, "usage parsed ${claudeLogSummary("/api/oauth/usage", resp.code, windows)}")
                    val status = successStatus(account, windows)
                    lastGoodUsage[account.id] = status
                    return status
                }
                resp.code == 401 -> {
                    if (!allowRefresh) {
                        Log.w(TAG, "usage 401 after refresh, giving up")
                        return errorStatus(account, claudeErrorText(VendorError.Unauthorized))
                    }
                    Log.w(TAG, "usage 401, attempting token refresh")
                    val refreshed = try {
                        oauthManager.refreshTokenIfNeeded(account, force = true)
                    } catch (e: Exception) {
                        Log.w(TAG, "token refresh attempt threw error=${e.javaClass.simpleName}")
                        account
                    }
                    if (refreshed == account || refreshed.refreshToken.isNullOrBlank()) {
                        Log.w(TAG, "token refresh failed -> Unauthorized")
                        return errorStatus(account, claudeErrorText(VendorError.Unauthorized))
                    }
                    onAccountRefreshed(refreshed)
                    Log.i(TAG, "token refresh ok, retrying usage once")
                    return fetchUsageStatus(
                        refreshed,
                        refreshed.activeCredential().trim().removePrefix("Bearer ").trim(),
                        allowRefresh = false
                    )
                }
                resp.code == 429 -> {
                    Log.w(TAG, "usage 429 rate limited, cached=${lastGoodUsage.containsKey(account.id)}")
                    return claudeRateLimitedStatus(
                        account,
                        lastGoodUsage[account.id],
                        resp.header("Retry-After"),
                    )
                }
                else -> {
                    val error = mapClaudeHttpError(resp.code)
                    Log.w(TAG, "usage http error code=${resp.code} type=${error.javaClass.simpleName}")
                    return errorStatus(account, claudeErrorText(error))
                }
            }
        }
    }

    private fun successStatus(account: ProviderAccount, windows: List<ClaudeWindowUsage>): TokenStatus {
        val fiveHour = windows.firstOrNull { it.id == "five_hour" }
        val weekly = windows.firstOrNull { it.id == "seven_day" }
        val fiveHourPct = fiveHour?.utilization ?: 0.0
        val weeklyPct = weekly?.utilization ?: 0.0
        val fiveHourRemaining = (100.0 - fiveHourPct).coerceIn(0.0, 100.0)
        val weeklyRemaining = (100.0 - weeklyPct).coerceIn(0.0, 100.0)
        val fiveHourResetText = formatReset(fiveHour?.resetsAt)
        val weeklyResetText = formatReset(weekly?.resetsAt)

        val remainingText = AppText.get(
            R.string.data_claude_usage_left,
            String.format(Locale.US, "%.0f", fiveHourRemaining),
            String.format(Locale.US, "%.0f", weeklyRemaining)
        )
        val limitText = buildString {
            append(AppText.get(R.string.data_used_5h, String.format(Locale.US, "%.0f", fiveHourPct)))
            if (fiveHourResetText.isNotBlank()) append(" ($fiveHourResetText)")
            append(" | ")
            append(AppText.get(R.string.data_used_weekly, String.format(Locale.US, "%.0f", weeklyPct)))
            if (weeklyResetText.isNotBlank()) append(" ($weeklyResetText)")
        }

        return TokenStatus(
            accountId = account.id,
            provider = ProviderType.CLAUDE,
            accountName = account.name,
            remainingFormatted = remainingText,
            totalLimitFormatted = limitText,
            usagePercent = fiveHourPct.toFloat(),
            resetTimeFormatted = if (fiveHourResetText.isNotBlank()) "5h $fiveHourResetText" else null,
            statusSummary = AppText.get(
                R.string.data_claude_summary,
                String.format(Locale.US, "%.0f", fiveHourRemaining),
                String.format(Locale.US, "%.0f", weeklyRemaining)
            ),
            isWarning = fiveHourPct > 80.0 || weeklyPct > 80.0,
            isCritical = fiveHourPct > 95.0 || weeklyPct > 95.0,
            windows = listOf(
                UsageWindow("five_hour", "5h", Remaining.Percent(fiveHourRemaining), fiveHour?.resetsAt),
                UsageWindow("seven_day", "7d", Remaining.Percent(weeklyRemaining), weekly?.resetsAt)
            )
        )
    }

    private fun errorStatus(account: ProviderAccount, text: String): TokenStatus = TokenStatus(
        accountId = account.id,
        provider = ProviderType.CLAUDE,
        accountName = account.name,
        remainingFormatted = text,
        statusSummary = AppText.get(R.string.status_needs_check),
        isError = true,
        errorMessage = text
    )

    private fun formatReset(resetsAt: Instant?): String {
        if (resetsAt == null) return ""
        val diffMs = resetsAt.toEpochMilli() - System.currentTimeMillis()
        if (diffMs <= 0) return AppText.get(R.string.time_reset_soon)
        val hours = diffMs / (1000 * 60 * 60)
        val days = hours / 24
        val remHours = hours % 24
        return if (days > 0) {
            AppText.get(R.string.time_in_days_hours, days, remHours)
        } else {
            AppText.get(R.string.time_in_hours, hours)
        }
    }

    companion object {
        private const val TAG = "oot.Claude"
        private const val USAGE_URL = "https://api.anthropic.com/api/oauth/usage"

        // Current Claude Code style User-Agent; the old "claude-code/0.2.29" was outdated.
        const val USER_AGENT = "claude-cli/2.0.28 (external, cli)"
    }
}
