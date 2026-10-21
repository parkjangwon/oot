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

class OpenRouterApi(
    private val client: OkHttpClient,
    private val json: Json
) {
    suspend fun fetchStatus(account: ProviderAccount): TokenStatus = withContext(Dispatchers.IO) {
        val baseUrl = account.customEndpoint?.takeIf { it.isNotBlank() } ?: ProviderType.OPENROUTER.baseUrl
        val creditsUrl = "$baseUrl/credits"
        val keyUrl = "$baseUrl/auth/key"

        val authHeader = if (account.apiKey.startsWith("Bearer ", ignoreCase = true)) {
            account.apiKey
        } else {
            "Bearer ${account.apiKey}"
        }

        try {
            // First try /auth/key to check key limits and usage
            val keyRequest = Request.Builder()
                .url(keyUrl)
                .addHeader("Authorization", authHeader)
                .build()

            client.newCall(keyRequest).execute().use { response ->
                if (!response.isSuccessful && response.code == 401) {
                    return@withContext TokenStatus(
                        accountId = account.id,
                        provider = ProviderType.OPENROUTER,
                        accountName = account.name,
                        remainingFormatted = "인증 실패",
                        statusSummary = "인증 오류 (401)",
                        isError = true,
                        errorMessage = "유효하지 않은 API 키입니다."
                    )
                }

                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    val root = json.parseToJsonElement(body).jsonObject
                    val data = root["data"]?.jsonObject

                    val limit = data?.get("limit")?.jsonPrimitive?.doubleOrNull
                    val usage = data?.get("usage")?.jsonPrimitive?.doubleOrNull ?: 0.0

                    if (limit != null && limit > 0) {
                        val remaining = (limit - usage).coerceAtLeast(0.0)
                        val percent = ((usage / limit) * 100).toFloat().coerceIn(0f, 100f)
                        return@withContext TokenStatus(
                            accountId = account.id,
                            provider = ProviderType.OPENROUTER,
                            accountName = account.name,
                            remainingFormatted = String.format(Locale.US, "$%.2f 남음", remaining),
                            totalLimitFormatted = String.format(Locale.US, "한도 $%.2f", limit),
                            usagePercent = percent,
                            statusSummary = String.format(Locale.US, "$%.2f 남음", remaining),
                            isWarning = percent > 80f,
                            isCritical = percent > 95f
                        )
                    }
                }
            }

            // Fallback to /credits (total account balance)
            val creditsRequest = Request.Builder()
                .url(creditsUrl)
                .addHeader("Authorization", authHeader)
                .build()

            client.newCall(creditsRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext TokenStatus(
                        accountId = account.id,
                        provider = ProviderType.OPENROUTER,
                        accountName = account.name,
                        remainingFormatted = "조회 실패 (${response.code})",
                        statusSummary = "오류 (${response.code})",
                        isError = true,
                        errorMessage = "HTTP ${response.code}: ${response.message}"
                    )
                }

                val body = response.body?.string().orEmpty()
                val root = json.parseToJsonElement(body).jsonObject
                val data = root["data"]?.jsonObject

                val totalCredits = data?.get("total_credits")?.jsonPrimitive?.doubleOrNull ?: 0.0
                val totalUsage = data?.get("total_usage")?.jsonPrimitive?.doubleOrNull ?: 0.0
                val remaining = (totalCredits - totalUsage).coerceAtLeast(0.0)
                val percent = if (totalCredits > 0) ((totalUsage / totalCredits) * 100).toFloat().coerceIn(0f, 100f) else null

                TokenStatus(
                    accountId = account.id,
                    provider = ProviderType.OPENROUTER,
                    accountName = account.name,
                    remainingFormatted = String.format(Locale.US, "$%.2f 크레딧", remaining),
                    totalLimitFormatted = String.format(Locale.US, "총 $%.2f", totalCredits),
                    usagePercent = percent,
                    statusSummary = String.format(Locale.US, "$%.2f 크레딧", remaining),
                    isWarning = (remaining < 2.0 && totalCredits > 0),
                    isCritical = (remaining < 0.5 && totalCredits > 0)
                )
            }
        } catch (e: Exception) {
            TokenStatus(
                accountId = account.id,
                provider = ProviderType.OPENROUTER,
                accountName = account.name,
                remainingFormatted = "네트워크 오류",
                statusSummary = "네트워크 실패",
                isError = true,
                errorMessage = e.localizedMessage ?: "연결 실패"
            )
        }
    }
}
