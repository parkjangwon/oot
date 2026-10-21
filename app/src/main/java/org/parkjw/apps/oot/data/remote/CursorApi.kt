package org.parkjw.apps.oot.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import org.parkjw.apps.oot.data.model.ProviderAccount
import org.parkjw.apps.oot.data.model.ProviderType
import org.parkjw.apps.oot.data.model.TokenStatus
import java.util.Locale

class CursorApi(
    private val client: OkHttpClient,
    private val json: Json
) {
    suspend fun fetchStatus(account: ProviderAccount): TokenStatus = withContext(Dispatchers.IO) {
        val credential = account.activeCredential()
        if (credential.isBlank()) {
            return@withContext TokenStatus(
                accountId = account.id,
                provider = ProviderType.CURSOR,
                accountName = account.name,
                remainingFormatted = "인증 토큰 없음",
                statusSummary = "로그인 필요",
                isError = true,
                errorMessage = "Cursor OAuth 로그인이 필요합니다."
            )
        }

        val baseUrl = account.customEndpoint?.takeIf { it.isNotBlank() } ?: ProviderType.CURSOR.baseUrl
        val usageUrl = "$baseUrl/usage"
        val authHeader = if (credential.startsWith("Bearer ", ignoreCase = true)) credential else "Bearer $credential"

        try {
            val request = Request.Builder()
                .url(usageUrl)
                .addHeader("Authorization", authHeader)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    if (response.code == 401 || response.code == 403) {
                        return@withContext TokenStatus(
                            accountId = account.id,
                            provider = ProviderType.CURSOR,
                            accountName = account.name,
                            remainingFormatted = "세션 만료 (401)",
                            statusSummary = "Cursor 인증 실패",
                            isError = true,
                            errorMessage = "인증 토큰이 만료되었습니다. 다시 로그인해주세요."
                        )
                    }
                    return@withContext TokenStatus(
                        accountId = account.id,
                        provider = ProviderType.CURSOR,
                        accountName = account.name,
                        remainingFormatted = "상태 코드: ${response.code}",
                        statusSummary = "HTTP ${response.code}",
                        isError = true,
                        errorMessage = "서버 응답 오류: ${response.code}"
                    )
                }

                val body = response.body?.string().orEmpty()
                val root = try { json.parseToJsonElement(body).jsonObject } catch (e: Exception) { null }

                val numFastRequests = root?.get("numFastRequests")?.jsonPrimitive?.intOrNull
                    ?: root?.get("fast_requests")?.jsonPrimitive?.intOrNull ?: 0
                val maxFastRequests = root?.get("maxFastRequests")?.jsonPrimitive?.intOrNull
                    ?: root?.get("limit")?.jsonPrimitive?.intOrNull ?: 500

                val remaining = (maxFastRequests - numFastRequests).coerceAtLeast(0)
                val percent = ((numFastRequests.toDouble() / maxFastRequests.toDouble()) * 100).toFloat().coerceIn(0f, 100f)

                TokenStatus(
                    accountId = account.id,
                    provider = ProviderType.CURSOR,
                    accountName = account.name,
                    remainingFormatted = String.format(Locale.US, "빠른 요청 %d회 남음", remaining),
                    totalLimitFormatted = String.format(Locale.US, "월 한도: %d회 (사용: %d)", maxFastRequests, numFastRequests),
                    usagePercent = percent,
                    statusSummary = String.format(Locale.US, "%d회 남음", remaining),
                    isWarning = percent > 80f,
                    isCritical = percent > 95f
                )
            }
        } catch (e: Exception) {
            TokenStatus(
                accountId = account.id,
                provider = ProviderType.CURSOR,
                accountName = account.name,
                remainingFormatted = "네트워크 오류",
                statusSummary = "연결 실패",
                isError = true,
                errorMessage = e.localizedMessage ?: "연결 실패"
            )
        }
    }
}
