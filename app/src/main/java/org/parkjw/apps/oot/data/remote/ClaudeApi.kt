package org.parkjw.apps.oot.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.parkjw.apps.oot.data.model.AuthType
import org.parkjw.apps.oot.data.model.ProviderAccount
import org.parkjw.apps.oot.data.model.ProviderType
import org.parkjw.apps.oot.data.model.TokenStatus
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class ClaudeApi(
    private val client: OkHttpClient,
    private val json: Json
) {
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
        val authBearer = "Bearer $cleanToken"

        // 1. For OAuth / Subscription accounts: Query Claude Official Usage Endpoint
        // Returns 5-hour rolling limit and 7-day weekly quota
        if (isOAuth) {
            val usageUrl = "https://api.anthropic.com/api/oauth/usage"
            val usageReq = Request.Builder()
                .url(usageUrl)
                .addHeader("Authorization", authBearer)
                .addHeader("anthropic-beta", "oauth-2025-04-20")
                .addHeader("User-Agent", "claude-code/0.2.29")
                .addHeader("Content-Type", "application/json")
                .build()

            try {
                client.newCall(usageReq).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string().orEmpty()
                        val root = try { json.parseToJsonElement(body).jsonObject } catch (e: Exception) { null }

                        if (root != null) {
                            val fiveHourObj = root["five_hour"]?.jsonObject
                            val sevenDayObj = root["seven_day"]?.jsonObject
                            val sonnetObj = root["seven_day_sonnet"]?.jsonObject

                            val fiveHourPct = fiveHourObj?.get("utilization")?.jsonPrimitive?.doubleOrNull ?: 0.0
                            val fiveHourReset = fiveHourObj?.get("resets_at")?.jsonPrimitive?.content
                            val fiveHourRemaining = (100.0 - fiveHourPct).coerceIn(0.0, 100.0)
                            val fiveHourResetFormatted = formatIsoReset(fiveHourReset)

                            val weeklyPct = sevenDayObj?.get("utilization")?.jsonPrimitive?.doubleOrNull ?: 0.0
                            val weeklyReset = sevenDayObj?.get("resets_at")?.jsonPrimitive?.content
                            val weeklyRemaining = (100.0 - weeklyPct).coerceIn(0.0, 100.0)
                            val weeklyResetFormatted = formatIsoReset(weeklyReset)

                            val sonnetPct = sonnetObj?.get("utilization")?.jsonPrimitive?.doubleOrNull

                            val remainingText = String.format(Locale.US, "5시간: %.0f%% 남음 | 주간: %.0f%% 남음", fiveHourRemaining, weeklyRemaining)
                            val limitText = buildString {
                                append(String.format(Locale.US, "5h 사용: %.0f%%", fiveHourPct))
                                if (fiveHourResetFormatted.isNotBlank()) append(" ($fiveHourResetFormatted)")
                                append(String.format(Locale.US, " | 주간 사용: %.0f%%", weeklyPct))
                                if (weeklyResetFormatted.isNotBlank()) append(" ($weeklyResetFormatted)")
                                if (sonnetPct != null) append(String.format(Locale.US, " [Sonnet %.0f%%]", sonnetPct))
                            }

                            val summaryText = String.format(Locale.US, "5h %.0f%% | 주 %.0f%%", fiveHourRemaining, weeklyRemaining)

                            return@withContext TokenStatus(
                                accountId = account.id,
                                provider = ProviderType.CLAUDE,
                                accountName = account.name,
                                remainingFormatted = remainingText,
                                totalLimitFormatted = limitText,
                                usagePercent = fiveHourPct.toFloat(),
                                resetTimeFormatted = if (fiveHourResetFormatted.isNotBlank()) "5h $fiveHourResetFormatted" else null,
                                statusSummary = summaryText,
                                isWarning = fiveHourPct > 80.0 || weeklyPct > 80.0,
                                isCritical = fiveHourPct > 95.0 || weeklyPct > 95.0
                            )
                        }
                    } else if (response.code == 401) {
                        return@withContext TokenStatus(
                            accountId = account.id,
                            provider = ProviderType.CLAUDE,
                            accountName = account.name,
                            remainingFormatted = "인증 실패 (401)",
                            statusSummary = "인증 오류",
                            isError = true,
                            errorMessage = "만료되었거나 유효하지 않은 Claude OAuth 토큰입니다."
                        )
                    }
                }
            } catch (e: Exception) {
                // Fall back to models check
            }
        }

        // 2. Fallback / API Key check: GET /v1/models
        val modelsUrl = "$baseUrl/models"
        val modelsReqBuilder = Request.Builder()
            .url(modelsUrl)
            .addHeader("anthropic-version", "2023-06-01")

        if (isOAuth) {
            modelsReqBuilder.addHeader("Authorization", authBearer)
            modelsReqBuilder.addHeader("anthropic-beta", "oauth-2025-04-20,claude-code-20250219")
            modelsReqBuilder.addHeader("User-Agent", "claude-cli")
            modelsReqBuilder.addHeader("x-app", "cli")
        } else {
            modelsReqBuilder.addHeader("x-api-key", cleanToken)
        }

        try {
            client.newCall(modelsReqBuilder.build()).execute().use { response ->
                if (response.code == 401) {
                    return@withContext TokenStatus(
                        accountId = account.id,
                        provider = ProviderType.CLAUDE,
                        accountName = account.name,
                        remainingFormatted = "인증 실패 (401)",
                        statusSummary = "인증 오류",
                        isError = true,
                        errorMessage = "유효하지 않은 Claude 인증 정보입니다."
                    )
                }

                if (response.isSuccessful) {
                    val tokensLimit = response.header("anthropic-ratelimit-tokens-limit")?.toLongOrNull()
                    val tokensRemaining = response.header("anthropic-ratelimit-tokens-remaining")?.toLongOrNull()
                    val tokensReset = response.header("anthropic-ratelimit-tokens-reset")

                    if (tokensLimit != null && tokensRemaining != null && tokensLimit > 0) {
                        val usedTokens = tokensLimit - tokensRemaining
                        val percent = ((usedTokens.toDouble() / tokensLimit.toDouble()) * 100).toFloat().coerceIn(0f, 100f)

                        return@withContext TokenStatus(
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

                    val planName = if (isOAuth) "Claude Pro/Max" else "Anthropic API"
                    return@withContext TokenStatus(
                        accountId = account.id,
                        provider = ProviderType.CLAUDE,
                        accountName = account.name,
                        remainingFormatted = "구독 정상 연동 ($planName)",
                        totalLimitFormatted = "정상 작동 중",
                        statusSummary = "Claude 정상",
                        isWarning = false
                    )
                }
            }
        } catch (e: Exception) {
            // ignore
        }

        TokenStatus(
            accountId = account.id,
            provider = ProviderType.CLAUDE,
            accountName = account.name,
            remainingFormatted = "Claude 연동 완료",
            totalLimitFormatted = "정상 작동 중",
            statusSummary = "Claude 정상",
            isWarning = false
        )
    }

    private fun formatIsoReset(isoStr: String?): String {
        if (isoStr.isNullOrBlank()) return ""
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val date = sdf.parse(isoStr.substringBefore(".")) ?: return ""
            val diffMs = date.time - System.currentTimeMillis()
            if (diffMs <= 0) return "곧 리셋"
            val hours = diffMs / (1000 * 60 * 60)
            val days = hours / 24
            val remHours = hours % 24
            if (days > 0) "${days}일 ${remHours}시간 후" else "${hours}시간 후"
        } catch (e: Exception) {
            ""
        }
    }
}
