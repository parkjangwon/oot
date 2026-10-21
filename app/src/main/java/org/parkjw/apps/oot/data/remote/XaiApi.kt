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

class XaiApi(
    private val client: OkHttpClient,
    private val json: Json
) {
    suspend fun fetchStatus(account: ProviderAccount): TokenStatus = withContext(Dispatchers.IO) {
        val baseUrl = account.customEndpoint?.takeIf { it.isNotBlank() } ?: ProviderType.XAI.baseUrl
        val keyUrl = "$baseUrl/api-key"
        val modelsUrl = "$baseUrl/models"

        val authHeader = if (account.apiKey.startsWith("Bearer ", ignoreCase = true)) {
            account.apiKey
        } else {
            "Bearer ${account.apiKey}"
        }

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
                    val tier = root?.get("tier")?.jsonPrimitive?.content ?: "X 통합 요금제"

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
                        remainingFormatted = if (response.code == 401) "인증 실패" else "HTTP ${response.code}",
                        statusSummary = "오류 (${response.code})",
                        isError = true,
                        errorMessage = if (response.code == 401) "API 키가 올바르지 않습니다." else "상태 코드: ${response.code}"
                    )
                }

                val limitRequests = response.header("x-ratelimit-limit-requests")
                val remainingRequests = response.header("x-ratelimit-remaining-requests")
                val resetTime = response.header("x-ratelimit-reset-requests")

                val summary = if (remainingRequests != null) {
                    "남은 요청: $remainingRequests"
                } else {
                    "구독 연동 완료"
                }

                TokenStatus(
                    accountId = account.id,
                    provider = ProviderType.XAI,
                    accountName = account.name,
                    remainingFormatted = summary,
                    totalLimitFormatted = limitRequests?.let { "한도: $it req" },
                    resetTimeFormatted = resetTime?.let { "리셋: $it" },
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
