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
        val credential = account.activeCredential().trim().removePrefix("Bearer ").trim()
        if (credential.isBlank()) {
            return@withContext TokenStatus(
                accountId = account.id,
                provider = ProviderType.OPENROUTER,
                accountName = account.name,
                remainingFormatted = "API 키 없음",
                statusSummary = "키 필요",
                isError = true,
                errorMessage = "OpenRouter API 키를 등록해주세요."
            )
        }

        val baseUrl = account.customEndpoint?.takeIf { it.isNotBlank() } ?: ProviderType.OPENROUTER.baseUrl
        val creditsUrl = "$baseUrl/credits"
        val keyUrl = "$baseUrl/auth/key"
        val authHeader = "Bearer $credential"

        try {
            var keyLimit: Double? = null
            var keyUsage: Double = 0.0
            var keyFetched = false

            // 1. Fetch /auth/key for key-specific limit & usage
            val keyRequest = Request.Builder()
                .url(keyUrl)
                .addHeader("Authorization", authHeader)
                .build()

            client.newCall(keyRequest).execute().use { response ->
                if (!response.isSuccessful && response.code == 429) {
                    return@withContext rateLimitedTokenStatus(
                        account,
                        ProviderType.OPENROUTER,
                        response.header("Retry-After"),
                    )
                }
                if (!response.isSuccessful && response.code == 401) {
                    return@withContext TokenStatus(
                        accountId = account.id,
                        provider = ProviderType.OPENROUTER,
                        accountName = account.name,
                        remainingFormatted = "인증 실패",
                        statusSummary = "인증 오류 (401)",
                        isError = true,
                        errorMessage = "유효하지 않은 OpenRouter API 키입니다."
                    )
                }

                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    val root = json.parseToJsonElement(body).jsonObject
                    val data = root["data"]?.jsonObject

                    keyLimit = data?.get("limit")?.jsonPrimitive?.doubleOrNull
                    keyUsage = data?.get("usage")?.jsonPrimitive?.doubleOrNull ?: 0.0
                    keyFetched = true
                }
            }

            // 2. Fetch /credits for total account balance & credits
            val creditsRequest = Request.Builder()
                .url(creditsUrl)
                .addHeader("Authorization", authHeader)
                .build()

            var totalCredits = 0.0
            var totalUsage = 0.0
            var creditsFetched = false

            client.newCall(creditsRequest).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    val root = json.parseToJsonElement(body).jsonObject
                    val data = root["data"]?.jsonObject

                    totalCredits = data?.get("total_credits")?.jsonPrimitive?.doubleOrNull ?: 0.0
                    totalUsage = data?.get("total_usage")?.jsonPrimitive?.doubleOrNull ?: 0.0
                    creditsFetched = true
                }
            }

            if (!keyFetched && !creditsFetched) {
                return@withContext TokenStatus(
                    accountId = account.id,
                    provider = ProviderType.OPENROUTER,
                    accountName = account.name,
                    remainingFormatted = "조회 실패",
                    statusSummary = "통신 실패",
                    isError = true,
                    errorMessage = "OpenRouter 서버 응답 오류"
                )
            }

            // Total remaining account credits
            val totalRemaining = (totalCredits - totalUsage).coerceAtLeast(0.0)
            val totalPercent = if (totalCredits > 0) ((totalUsage / totalCredits) * 100).toFloat().coerceIn(0f, 100f) else null

            // Key limit calculations
            val hasKeyLimit = (keyLimit != null && keyLimit!! > 0)
            val keyRemaining = if (hasKeyLimit) (keyLimit!! - keyUsage).coerceAtLeast(0.0) else null
            val keyPercent = if (hasKeyLimit) ((keyUsage / keyLimit!!) * 100).toFloat().coerceIn(0f, 100f) else null

            // Format main display text: Show current remaining credits and key limit/usage
            val remainingDisplay = if (hasKeyLimit) {
                String.format(Locale.US, "잔여 크레딧: $%.2f (키 남음: $%.2f)", totalRemaining, keyRemaining)
            } else {
                String.format(Locale.US, "잔여 크레딧: $%.2f", totalRemaining)
            }

            val limitDisplay = if (hasKeyLimit) {
                String.format(
                    Locale.US,
                    "키 한도: $%.2f (사용: $%.2f, %.1f%%)",
                    keyLimit,
                    keyUsage,
                    keyPercent ?: 0f
                )
            } else {
                String.format(
                    Locale.US,
                    "키 한도: 무제한 (현재까지 $%.2f 사용)",
                    keyUsage
                )
            }

            // Summary ticker for status bar collapsed view
            val tickerSummary = if (hasKeyLimit) {
                String.format(Locale.US, "OR: $%.2f (키 $%.2f)", totalRemaining, keyRemaining)
            } else {
                String.format(Locale.US, "OR: $%.2f", totalRemaining)
            }

            val isWarn = (totalRemaining < 2.0 && totalRemaining > 0) || (keyPercent != null && keyPercent > 80f)
            val isCrit = (totalRemaining < 0.5 && totalRemaining > 0) || (keyPercent != null && keyPercent > 95f)

            TokenStatus(
                accountId = account.id,
                provider = ProviderType.OPENROUTER,
                accountName = account.name,
                remainingFormatted = remainingDisplay,
                totalLimitFormatted = limitDisplay,
                usagePercent = keyPercent ?: totalPercent,
                statusSummary = tickerSummary,
                isWarning = isWarn,
                isCritical = isCrit
            )
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
