package org.parkjw.apps.oot.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.parkjw.apps.oot.data.model.AuthType
import org.parkjw.apps.oot.data.model.ProviderAccount
import org.parkjw.apps.oot.data.model.ProviderType
import org.parkjw.apps.oot.data.model.Remaining
import org.parkjw.apps.oot.data.model.TokenStatus
import org.parkjw.apps.oot.data.model.UsageWindow
import org.parkjw.apps.oot.data.model.VendorError
import org.parkjw.apps.oot.data.oauth.OAuthManager
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
                remainingFormatted = "토큰/키 없음",
                statusSummary = "키 필요",
                isError = true,
                errorMessage = "Claude API 키 또는 OAuth 인증이 필요합니다."
            )
        }

        val baseUrl = account.customEndpoint?.takeIf { it.isNotBlank() } ?: ProviderType.CLAUDE.baseUrl
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
                            remainingFormatted = String.format(Locale.US, "%,d 토큰 남음", tokensRemaining),
                            totalLimitFormatted = String.format(Locale.US, "한도: %,d 토큰/분", tokensLimit),
                            usagePercent = percent,
                            resetTimeFormatted = tokensReset?.let { "리셋: $it" },
                            statusSummary = String.format(Locale.US, "%,dk 남음", tokensRemaining / 1000),
                            isWarning = percent > 80f,
                            isCritical = percent > 95f
                        )
                    }

                    TokenStatus(
                        accountId = account.id,
                        provider = ProviderType.CLAUDE,
                        accountName = account.name,
                        remainingFormatted = "연결 정상 (Anthropic API)",
                        totalLimitFormatted = "정상 작동 중",
                        statusSummary = "정상",
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

        val remainingText = String.format(Locale.US, "5시간: %.0f%% 남음 | 주간: %.0f%% 남음", fiveHourRemaining, weeklyRemaining)
        val limitText = buildString {
            append(String.format(Locale.US, "5h 사용: %.0f%%", fiveHourPct))
            if (fiveHourResetText.isNotBlank()) append(" ($fiveHourResetText)")
            append(String.format(Locale.US, " | 주간 사용: %.0f%%", weeklyPct))
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
            statusSummary = String.format(Locale.US, "5h %.0f%% | 주 %.0f%%", fiveHourRemaining, weeklyRemaining),
            isWarning = fiveHourPct > 80.0 || weeklyPct > 80.0,
            isCritical = fiveHourPct > 95.0 || weeklyPct > 95.0,
            windows = listOf(
                UsageWindow("five_hour", "5시간", Remaining.Percent(fiveHourRemaining), fiveHour?.resetsAt),
                UsageWindow("seven_day", "주간", Remaining.Percent(weeklyRemaining), weekly?.resetsAt)
            )
        )
    }

    private fun errorStatus(account: ProviderAccount, text: String): TokenStatus = TokenStatus(
        accountId = account.id,
        provider = ProviderType.CLAUDE,
        accountName = account.name,
        remainingFormatted = text,
        statusSummary = "확인 필요",
        isError = true,
        errorMessage = text
    )

    private fun formatReset(resetsAt: Instant?): String {
        if (resetsAt == null) return ""
        val diffMs = resetsAt.toEpochMilli() - System.currentTimeMillis()
        if (diffMs <= 0) return "곧 리셋"
        val hours = diffMs / (1000 * 60 * 60)
        val days = hours / 24
        val remHours = hours % 24
        return if (days > 0) "${days}일 ${remHours}시간 후" else "${hours}시간 후"
    }

    companion object {
        private const val TAG = "oot.Claude"
        private const val USAGE_URL = "https://api.anthropic.com/api/oauth/usage"

        // Current Claude Code style User-Agent; the old "claude-code/0.2.29" was outdated.
        const val USER_AGENT = "claude-cli/2.0.28 (external, cli)"
    }
}
