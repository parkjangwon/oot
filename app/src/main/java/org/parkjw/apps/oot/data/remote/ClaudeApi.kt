package org.parkjw.apps.oot.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.parkjw.apps.oot.data.model.AuthType
import org.parkjw.apps.oot.data.model.ProviderAccount
import org.parkjw.apps.oot.data.model.ProviderType
import org.parkjw.apps.oot.data.model.TokenStatus
import java.util.Locale

class ClaudeApi(
    private val client: OkHttpClient
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

        // 1. Primary check: GET /v1/models (Works reliably for both OAuth & API Key)
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
                        errorMessage = "유효하지 않거나 만료된 Claude 인증 토큰입니다."
                    )
                }

                if (response.isSuccessful) {
                    val tokensLimit = response.header("anthropic-ratelimit-tokens-limit")?.toLongOrNull()
                    val tokensRemaining = response.header("anthropic-ratelimit-tokens-remaining")?.toLongOrNull()
                    val tokensReset = response.header("anthropic-ratelimit-tokens-reset")
                    val requestsRemaining = response.header("anthropic-ratelimit-requests-remaining")

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

                    if (requestsRemaining != null) {
                        return@withContext TokenStatus(
                            accountId = account.id,
                            provider = ProviderType.CLAUDE,
                            accountName = account.name,
                            remainingFormatted = "남은 요청: $requestsRemaining",
                            totalLimitFormatted = "Claude API 정상 연동",
                            statusSummary = "$requestsRemaining req 남음",
                            resetTimeFormatted = tokensReset?.let { "리셋: $it" },
                            isWarning = false
                        )
                    }

                    // Successfully authenticated via OAuth or API Key
                    val planName = if (isOAuth) "Claude Pro/Max" else "Anthropic API"
                    return@withContext TokenStatus(
                        accountId = account.id,
                        provider = ProviderType.CLAUDE,
                        accountName = account.name,
                        remainingFormatted = "구독 연동 완료 ($planName)",
                        totalLimitFormatted = "정상 작동 중",
                        statusSummary = "Claude 정상",
                        isWarning = false
                    )
                }
            }
        } catch (e: Exception) {
            // Ignore and try count_tokens fallback
        }

        // 2. Secondary check for Console API Keys: POST /v1/messages/count_tokens
        val countTokensUrl = "$baseUrl/messages/count_tokens"
        val jsonPayload = """{"model":"claude-3-5-sonnet-20241022","messages":[{"role":"user","content":"hi"}]}"""
        val countTokensReqBuilder = Request.Builder()
            .url(countTokensUrl)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("anthropic-beta", "token-counting-2024-11-01,oauth-2025-04-20,claude-code-20250219")
            .post(jsonPayload.toRequestBody("application/json".toMediaType()))

        if (isOAuth) {
            countTokensReqBuilder.addHeader("Authorization", authBearer)
            countTokensReqBuilder.addHeader("User-Agent", "claude-cli")
            countTokensReqBuilder.addHeader("x-app", "cli")
        } else {
            countTokensReqBuilder.addHeader("x-api-key", cleanToken)
        }

        try {
            client.newCall(countTokensReqBuilder.build()).execute().use { response ->
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

                TokenStatus(
                    accountId = account.id,
                    provider = ProviderType.CLAUDE,
                    accountName = account.name,
                    remainingFormatted = if (response.isSuccessful) "Claude 연동 완료" else "상태: HTTP ${response.code}",
                    totalLimitFormatted = "정상 작동 중",
                    statusSummary = if (response.isSuccessful) "Claude 정상" else "응답 ${response.code}",
                    isError = !response.isSuccessful,
                    errorMessage = if (!response.isSuccessful) "HTTP ${response.code}" else null
                )
            }
        } catch (e: Exception) {
            TokenStatus(
                accountId = account.id,
                provider = ProviderType.CLAUDE,
                accountName = account.name,
                remainingFormatted = "네트워크 오류",
                statusSummary = "통신 실패",
                isError = true,
                errorMessage = e.localizedMessage ?: "통신 실패"
            )
        }
    }
}
