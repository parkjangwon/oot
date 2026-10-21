package org.parkjw.apps.oot.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import org.parkjw.apps.oot.data.model.ProviderAccount
import org.parkjw.apps.oot.data.model.ProviderType
import org.parkjw.apps.oot.data.model.TokenStatus
import java.util.Locale

class OpenAiApi(
    private val client: OkHttpClient,
    private val json: Json
) {
    suspend fun fetchStatus(account: ProviderAccount): TokenStatus = withContext(Dispatchers.IO) {
        val credential = account.activeCredential()
        if (credential.isBlank()) {
            return@withContext TokenStatus(
                accountId = account.id,
                provider = ProviderType.OPENAI,
                accountName = account.name,
                remainingFormatted = "인증 토큰 없음",
                statusSummary = "토큰 필요",
                isError = true,
                errorMessage = "OAuth 로그인 또는 API 키가 필요합니다."
            )
        }

        val baseUrl = account.customEndpoint?.takeIf { it.isNotBlank() } ?: ProviderType.OPENAI.baseUrl
        val authHeader = if (credential.startsWith("Bearer ", ignoreCase = true)) credential else "Bearer $credential"

        try {
            // Check models & rate-limits
            val request = Request.Builder()
                .url("$baseUrl/models")
                .addHeader("Authorization", authHeader)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    if (response.code == 429) {
                        return@withContext rateLimitedTokenStatus(
                            account,
                            ProviderType.OPENAI,
                            response.header("Retry-After"),
                        )
                    }
                    if (response.code == 401) {
                        return@withContext TokenStatus(
                            accountId = account.id,
                            provider = ProviderType.OPENAI,
                            accountName = account.name,
                            remainingFormatted = "인증 만료/실패",
                            statusSummary = "인증 실패 (401)",
                            isError = true,
                            errorMessage = "인증이 만료되었습니다. 다시 로그인해주세요."
                        )
                    }
                    return@withContext TokenStatus(
                        accountId = account.id,
                        provider = ProviderType.OPENAI,
                        accountName = account.name,
                        remainingFormatted = "HTTP ${response.code}",
                        statusSummary = "오류 (${response.code})",
                        isError = true,
                        errorMessage = "서버 응답 코드: ${response.code}"
                    )
                }

                val limitTokens = response.header("x-ratelimit-limit-tokens")?.toLongOrNull()
                val remainingTokens = response.header("x-ratelimit-remaining-tokens")?.toLongOrNull()
                val resetTokens = response.header("x-ratelimit-reset-tokens")
                val remainingRequests = response.header("x-ratelimit-remaining-requests")

                if (limitTokens != null && remainingTokens != null && limitTokens > 0) {
                    val used = limitTokens - remainingTokens
                    val percent = ((used.toDouble() / limitTokens.toDouble()) * 100).toFloat().coerceIn(0f, 100f)

                    return@withContext TokenStatus(
                        accountId = account.id,
                        provider = ProviderType.OPENAI,
                        accountName = account.name,
                        remainingFormatted = String.format(Locale.US, "%,d 토큰 남음", remainingTokens),
                        totalLimitFormatted = String.format(Locale.US, "한도: %,d", limitTokens),
                        usagePercent = percent,
                        resetTimeFormatted = resetTokens?.let { "리셋: $it" },
                        statusSummary = String.format(Locale.US, "%,dk 남음", remainingTokens / 1000),
                        isWarning = percent > 80f,
                        isCritical = percent > 95f
                    )
                }

                val summary = if (remainingRequests != null) "$remainingRequests req 남음" else "구독 활성화됨"
                TokenStatus(
                    accountId = account.id,
                    provider = ProviderType.OPENAI,
                    accountName = account.name,
                    remainingFormatted = summary,
                    statusSummary = "OpenAI 정상",
                    resetTimeFormatted = resetTokens?.let { "리셋: $it" }
                )
            }
        } catch (e: Exception) {
            TokenStatus(
                accountId = account.id,
                provider = ProviderType.OPENAI,
                accountName = account.name,
                remainingFormatted = "네트워크 오류",
                statusSummary = "연결 실패",
                isError = true,
                errorMessage = e.localizedMessage ?: "연결 실패"
            )
        }
    }
}
