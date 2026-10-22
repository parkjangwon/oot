package org.parkjw.apps.oot.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.parkjw.apps.oot.data.model.ProviderAccount
import org.parkjw.apps.oot.data.model.ProviderType
import org.parkjw.apps.oot.data.model.TokenStatus
import java.time.Instant
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
                errorMessage = "xAI API 키 또는 Device 로그인이 필요합니다."
            )
        }

        val authHeader = "Bearer $credential"

        // 1. Try Grok CLI Subscription Billing endpoint (Weekly quota & product usage)
        try {
            val billingUrl = "https://cli-chat-proxy.grok.com/v1/billing?format=credits"
            val billingReq = Request.Builder()
                .url(billingUrl)
                .addHeader("Authorization", authHeader)
                .addHeader("x-xai-token-auth", "xai-grok-cli")
                .addHeader("Accept", "application/json")
                .build()

            client.newCall(billingReq).execute().use { response ->
                if (response.isSuccessful) {
                    val billing = parseXaiBilling(response.body?.string().orEmpty(), json)
                    if (billing != null) {
                        return@withContext billingStatus(account, billing)
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore and fall back to xAI developer API
        }

        // 2. Fallback: xAI Developer API (api-key & models endpoint)
        val baseUrl = account.customEndpoint?.takeIf { it.isNotBlank() } ?: ProviderType.XAI.baseUrl
        val keyUrl = "$baseUrl/api-key"
        val modelsUrl = "$baseUrl/models"

        try {
            // Check api-key endpoint
            val keyReq = Request.Builder()
                .url(keyUrl)
                .addHeader("Authorization", authHeader)
                .build()

            client.newCall(keyReq).execute().use { response ->
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
                        totalLimitFormatted = "정상 작동 중 ($name)",
                        usagePercent = null,
                        statusSummary = "xAI $tier",
                        isWarning = false
                    )
                }
            }

            // Check models endpoint and read rate limit headers
            val modelsReq = Request.Builder()
                .url(modelsUrl)
                .addHeader("Authorization", authHeader)
                .build()

            client.newCall(modelsReq).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext TokenStatus(
                        accountId = account.id,
                        provider = ProviderType.XAI,
                        accountName = account.name,
                        remainingFormatted = if (response.code == 401) "인증 실패 (401)" else "HTTP ${response.code}",
                        statusSummary = "오류 (${response.code})",
                        isError = true,
                        errorMessage = if (response.code == 401) "유효하지 않은 xAI 인증 토큰/키입니다." else "상태 코드: ${response.code}"
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

                val summary = if (remainingRequests != null) "남은 요청: $remainingRequests" else "xAI 정상"
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
                statusSummary = "통신 실패",
                isError = true,
                errorMessage = e.localizedMessage ?: "통신 실패"
            )
        }
    }

    private fun billingStatus(account: ProviderAccount, billing: XaiBilling): TokenStatus {
        val resetFormatted = formatPeriodEnd(billing.periodEnd)
        val productBreakdown = billing.productUsage.joinToString(", ") { (name, percent) ->
            String.format(Locale.US, "%s: %.1f%%", name, percent)
        }
        val limitText = buildString {
            append(String.format(Locale.US, "주간 사용: %.1f%%", billing.usedPercent))
            if (resetFormatted.isNotBlank()) append(" (리셋: $resetFormatted)")
            if (productBreakdown.isNotBlank()) append(" | $productBreakdown")
            if (billing.prepaidBalance != null) append(" [잔액 ${billing.prepaidBalance}]")
        }

        return TokenStatus(
            accountId = account.id,
            provider = ProviderType.XAI,
            accountName = account.name,
            remainingFormatted = String.format(Locale.US, "주간: %.1f%% 남음", billing.percentLeft),
            totalLimitFormatted = limitText,
            usagePercent = billing.usedPercent.toFloat(),
            resetTimeFormatted = if (resetFormatted.isNotBlank()) "리셋: $resetFormatted" else null,
            statusSummary = String.format(Locale.US, "주간 %.0f%%", billing.percentLeft),
            isWarning = billing.usedPercent > 80.0,
            isCritical = billing.usedPercent > 95.0,
            windows = billing.toUsageWindows()
        )
    }

    private fun formatPeriodEnd(periodEnd: Instant?): String {
        if (periodEnd == null) return ""
        val diffMs = periodEnd.toEpochMilli() - System.currentTimeMillis()
        if (diffMs <= 0) return "곧 리셋"
        val hours = diffMs / (1000 * 60 * 60)
        val days = hours / 24
        val remHours = hours % 24
        return if (days > 0) "${days}일 ${remHours}시간 후" else "${hours}시간 후"
    }
}
