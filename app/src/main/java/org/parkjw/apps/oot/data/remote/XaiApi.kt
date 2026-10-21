package org.parkjw.apps.oot.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import org.parkjw.apps.oot.data.model.ProviderAccount
import org.parkjw.apps.oot.data.model.ProviderType
import org.parkjw.apps.oot.data.model.TokenStatus
import java.util.Locale

class XaiApi(
    private val client: OkHttpClient,
    private val json: Json
) {
    suspend fun fetchStatus(account: ProviderAccount): TokenStatus = withContext(Dispatchers.IO) {
        val credential = account.activeCredential().trim().removePrefix("Bearer ").trim()
        if (credential.isBlank()) {
            return@withContext TokenStatus(
                accountId = account.id,
                provider = ProviderType.XAI,
                accountName = account.name,
                remainingFormatted = "API 키 없음",
                statusSummary = "키 필요",
                isError = true,
                errorMessage = "xAI API 키를 등록해주세요."
            )
        }

        val baseUrl = account.customEndpoint?.takeIf { it.isNotBlank() } ?: ProviderType.XAI.baseUrl
        val keyUrl = "$baseUrl/api-key"
        val modelsUrl = "$baseUrl/models"
        val authHeader = "Bearer $credential"

        try {
            // First try xAI api-key info endpoint
            val request = Request.Builder()
                .url(keyUrl)
                .addHeader("Authorization", authHeader)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    val root = try { json.parseToJsonElement(body).jsonObject } catch (e: Exception) { null }

                    val name = root?.get("name")?.jsonPrimitive?.content ?: account.name
                    val tier = root?.get("tier")?.jsonPrimitive?.content ?: "xAI API"

                    return@withContext TokenStatus(
                        accountId = account.id,
                        provider = ProviderType.XAI,
                        accountName = account.name,
                        remainingFormatted = "구독 활성 ($tier)",
                        totalLimitFormatted = "정상 작동 중",
                        usagePercent = null,
                        statusSummary = "xAI 정상",
                        isWarning = false
                    )
                }
            }

            // Fallback: ping models endpoint to verify auth and read ratelimit headers
            val pingRequest = Request.Builder()
                .url(modelsUrl)
                .addHeader("Authorization", authHeader)
                .build()

            client.newCall(pingRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext TokenStatus(
                        accountId = account.id,
                        provider = ProviderType.XAI,
                        accountName = account.name,
                        remainingFormatted = if (response.code == 401) "인증 실패 (401)" else "HTTP ${response.code}",
                        statusSummary = "오류 (${response.code})",
                        isError = true,
                        errorMessage = if (response.code == 401) "유효하지 않은 xAI API 키입니다." else "상태 코드: ${response.code}"
                    )
                }

                val limitTokens = response.header("x-ratelimit-limit-tokens")?.toLongOrNull()
                val remainingTokens = response.header("x-ratelimit-remaining-tokens")?.toLongOrNull()
                val resetTokens = response.header("x-ratelimit-reset-tokens")

                val limitRequests = response.header("x-ratelimit-limit-requests")
                val remainingRequests = response.header("x-ratelimit-remaining-requests")
                val resetRequests = response.header("x-ratelimit-reset-requests")

                if (limitTokens != null && remainingTokens != null && limitTokens > 0) {
                    val usedTokens = limitTokens - remainingTokens
                    val percent = ((usedTokens.toDouble() / limitTokens.toDouble()) * 100).toFloat().coerceIn(0f, 100f)

                    return@withContext TokenStatus(
                        accountId = account.id,
                        provider = ProviderType.XAI,
                        accountName = account.name,
                        remainingFormatted = String.format(Locale.US, "%,d 토큰 남음", remainingTokens),
                        totalLimitFormatted = String.format(Locale.US, "한도: %,d 토큰/분", limitTokens),
                        usagePercent = percent,
                        resetTimeFormatted = resetTokens?.let { "리셋: $it" },
                        statusSummary = String.format(Locale.US, "%,dk 남음", remainingTokens / 1000),
                        isWarning = percent > 80f,
                        isCritical = percent > 95f
                    )
                }

                val summary = if (remainingRequests != null) "남은 요청: $remainingRequests" else "xAI API 정상"
                val limitInfo = limitRequests?.let { "한도: $it req" } ?: "Grok 모델 연동됨"

                TokenStatus(
                    accountId = account.id,
                    provider = ProviderType.XAI,
                    accountName = account.name,
                    remainingFormatted = summary,
                    totalLimitFormatted = limitInfo,
                    resetTimeFormatted = resetRequests?.let { "리셋: $it" },
                    statusSummary = summary,
                    isWarning = false
                )
            }
        } catch (e: Exception) {
            TokenStatus(
                accountId = account.id,
                provider = ProviderType.XAI,
                accountName = account.name,
                remainingFormatted = "네트워크 오류",
                statusSummary = "연결 실패",
                isError = true,
                errorMessage = e.localizedMessage ?: "연결 실패"
            )
        }
    }
}
