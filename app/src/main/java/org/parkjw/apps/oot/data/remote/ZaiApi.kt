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

class ZaiApi(
    private val client: OkHttpClient,
    private val json: Json
) {
    suspend fun fetchStatus(account: ProviderAccount): TokenStatus = withContext(Dispatchers.IO) {
        val baseUrl = account.customEndpoint?.takeIf { it.isNotBlank() } ?: ProviderType.ZAI.baseUrl
        val usageUrl = "$baseUrl/user/usage"
        val subscriptionUrl = "$baseUrl/subscription"

        val authHeader = if (account.apiKey.startsWith("Bearer ", ignoreCase = true)) {
            account.apiKey
        } else {
            "Bearer ${account.apiKey}"
        }

        try {
            val request = Request.Builder()
                .url(usageUrl)
                .addHeader("Authorization", authHeader)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    val root = try { json.parseToJsonElement(body).jsonObject } catch (e: Exception) { null }

                    val tokensLeft = root?.get("remaining_tokens")?.jsonPrimitive?.doubleOrNull
                    val totalTokens = root?.get("total_tokens")?.jsonPrimitive?.doubleOrNull
                    val planName = root?.get("plan")?.jsonPrimitive?.content ?: "코딩 플랜"

                    if (tokensLeft != null && totalTokens != null && totalTokens > 0) {
                        val used = totalTokens - tokensLeft
                        val percent = ((used / totalTokens) * 100).toFloat().coerceIn(0f, 100f)

                        return@withContext TokenStatus(
                            accountId = account.id,
                            provider = ProviderType.ZAI,
                            accountName = account.name,
                            remainingFormatted = String.format(Locale.US, "%,.0f 토큰 남음", tokensLeft),
                            totalLimitFormatted = String.format(Locale.US, "%s (총 %,.0f)", planName, totalTokens),
                            usagePercent = percent,
                            statusSummary = String.format(Locale.US, "%,.0fk 남음", tokensLeft / 1000.0),
                            isWarning = percent > 80f,
                            isCritical = percent > 95f
                        )
                    }

                    return@withContext TokenStatus(
                        accountId = account.id,
                        provider = ProviderType.ZAI,
                        accountName = account.name,
                        remainingFormatted = "$planName 활성화됨",
                        statusSummary = "ZAI 정상",
                        isWarning = false
                    )
                }

                if (response.code == 401 || response.code == 403) {
                    return@withContext TokenStatus(
                        accountId = account.id,
                        provider = ProviderType.ZAI,
                        accountName = account.name,
                        remainingFormatted = "인증 실패 (${response.code})",
                        statusSummary = "ZAI 인증 실패",
                        isError = true,
                        errorMessage = "API 키가 올바르지 않거나 만료되었습니다."
                    )
                }

                TokenStatus(
                    accountId = account.id,
                    provider = ProviderType.ZAI,
                    accountName = account.name,
                    remainingFormatted = "상태 코드: ${response.code}",
                    statusSummary = "HTTP ${response.code}",
                    isError = true,
                    errorMessage = "ZAI 서버 응답 오류 (${response.code})"
                )
            }
        } catch (e: Exception) {
            TokenStatus(
                accountId = account.id,
                provider = ProviderType.ZAI,
                accountName = account.name,
                remainingFormatted = "네트워크 오류",
                statusSummary = "연결 실패",
                isError = true,
                errorMessage = e.localizedMessage ?: "연결 실패"
            )
        }
    }
}
