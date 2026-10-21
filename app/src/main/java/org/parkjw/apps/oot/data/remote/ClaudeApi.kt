package org.parkjw.apps.oot.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.parkjw.apps.oot.data.model.ProviderAccount
import org.parkjw.apps.oot.data.model.ProviderType
import org.parkjw.apps.oot.data.model.TokenStatus
import java.util.Locale

class ClaudeApi(
    private val client: OkHttpClient
) {
    suspend fun fetchStatus(account: ProviderAccount): TokenStatus = withContext(Dispatchers.IO) {
        val baseUrl = account.customEndpoint?.takeIf { it.isNotBlank() } ?: ProviderType.CLAUDE.baseUrl
        val endpoint = "$baseUrl/messages/count_tokens"

        // Minimal payload to check auth and read rate-limit headers
        val jsonPayload = """{"model":"claude-3-5-sonnet-20241022","messages":[{"role":"user","content":"ping"}]}"""
        val requestBody = jsonPayload.toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("x-api-key", account.apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .post(requestBody)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful && response.code == 401) {
                    return@withContext TokenStatus(
                        accountId = account.id,
                        provider = ProviderType.CLAUDE,
                        accountName = account.name,
                        remainingFormatted = "인증 실패",
                        statusSummary = "Claude 인증 실패",
                        isError = true,
                        errorMessage = "유효하지 않은 Anthropic API Key입니다."
                    )
                }

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
                        totalLimitFormatted = String.format(Locale.US, "한도: %,d", tokensLimit),
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
                        statusSummary = "$requestsRemaining req 남음",
                        resetTimeFormatted = tokensReset?.let { "리셋: $it" }
                    )
                }

                TokenStatus(
                    accountId = account.id,
                    provider = ProviderType.CLAUDE,
                    accountName = account.name,
                    remainingFormatted = if (response.isSuccessful) "정상 연결됨" else "HTTP ${response.code}",
                    statusSummary = if (response.isSuccessful) "Claude 정상" else "오류 (${response.code})",
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
                statusSummary = "연결 실패",
                isError = true,
                errorMessage = e.localizedMessage ?: "연결 실패"
            )
        }
    }
}
