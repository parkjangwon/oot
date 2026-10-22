package org.parkjw.apps.oot.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.parkjw.apps.oot.R
import org.parkjw.apps.oot.data.model.ProviderAccount
import org.parkjw.apps.oot.data.model.ProviderType
import org.parkjw.apps.oot.data.model.TokenStatus
import org.parkjw.apps.oot.i18n.AppText
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
                remainingFormatted = AppText.get(R.string.data_no_api_key),
                statusSummary = AppText.get(R.string.data_key_needed),
                isError = true,
                errorMessage = AppText.get(R.string.data_xai_key_required)
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
                        remainingFormatted = AppText.get(R.string.data_xai_subscription_active, tier),
                        totalLimitFormatted = AppText.get(R.string.data_working_normal_named, name),
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
                    if (response.code == 429) {
                        return@withContext rateLimitedTokenStatus(
                            account,
                            ProviderType.XAI,
                            response.header("Retry-After"),
                        )
                    }
                    return@withContext TokenStatus(
                        accountId = account.id,
                        provider = ProviderType.XAI,
                        accountName = account.name,
                        remainingFormatted = if (response.code == 401) {
                            AppText.get(R.string.data_auth_failed_code, 401)
                        } else {
                            "HTTP ${response.code}"
                        },
                        statusSummary = AppText.get(R.string.data_error_code, response.code),
                        isError = true,
                        errorMessage = if (response.code == 401) {
                            AppText.get(R.string.data_xai_token_invalid)
                        } else {
                            AppText.get(R.string.data_status_code, response.code)
                        }
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
                        remainingFormatted = AppText.get(
                            R.string.data_tokens_left,
                            String.format(Locale.US, "%,d", remainingTokens)
                        ),
                        totalLimitFormatted = AppText.get(
                            R.string.data_limit_tokens_per_min,
                            String.format(Locale.US, "%,d", limitTokens)
                        ),
                        usagePercent = percent,
                        resetTimeFormatted = resetTokens?.let { AppText.get(R.string.data_reset_value, it) },
                        statusSummary = AppText.get(
                            R.string.data_k_left,
                            String.format(Locale.US, "%,d", remainingTokens / 1000)
                        ),
                        isWarning = percent > 80f,
                        isCritical = percent > 95f
                    )
                }

                val summary = if (remainingRequests != null) {
                    AppText.get(R.string.data_requests_left, remainingRequests)
                } else {
                    AppText.get(R.string.data_xai_ok)
                }
                val limitInfo = limitRequests?.let { AppText.get(R.string.data_limit_reqs, it) }
                    ?: AppText.get(R.string.data_grok_connected)

                TokenStatus(
                    accountId = account.id,
                    provider = ProviderType.XAI,
                    accountName = account.name,
                    remainingFormatted = summary,
                    totalLimitFormatted = limitInfo,
                    resetTimeFormatted = resetRequests?.let { AppText.get(R.string.data_reset_value, it) },
                    statusSummary = summary,
                    isWarning = false
                )
            }
        } catch (e: Exception) {
            TokenStatus(
                accountId = account.id,
                provider = ProviderType.XAI,
                accountName = account.name,
                remainingFormatted = AppText.get(R.string.data_error_network),
                statusSummary = AppText.get(R.string.data_comm_failed),
                isError = true,
                errorMessage = e.localizedMessage ?: AppText.get(R.string.data_comm_failed)
            )
        }
    }

    private fun billingStatus(account: ProviderAccount, billing: XaiBilling): TokenStatus {
        val resetFormatted = formatPeriodEnd(billing.periodEnd)
        val productBreakdown = billing.productUsage.joinToString(", ") { (name, percent) ->
            String.format(Locale.US, "%s: %.1f%%", name, percent)
        }
        val limitText = buildString {
            append(AppText.get(R.string.data_used_weekly, String.format(Locale.US, "%.1f", billing.usedPercent)))
            if (resetFormatted.isNotBlank()) {
                append(" (${AppText.get(R.string.data_reset_value, resetFormatted)})")
            }
            if (productBreakdown.isNotBlank()) append(" | $productBreakdown")
            if (billing.prepaidBalance != null) {
                append(" [${AppText.get(R.string.data_balance_value, billing.prepaidBalance.toString())}]")
            }
        }

        return TokenStatus(
            accountId = account.id,
            provider = ProviderType.XAI,
            accountName = account.name,
            remainingFormatted = AppText.get(
                R.string.data_weekly_left,
                String.format(Locale.US, "%.1f", billing.percentLeft)
            ),
            totalLimitFormatted = limitText,
            usagePercent = billing.usedPercent.toFloat(),
            resetTimeFormatted = if (resetFormatted.isNotBlank()) {
                AppText.get(R.string.data_reset_value, resetFormatted)
            } else {
                null
            },
            statusSummary = AppText.get(
                R.string.data_weekly_summary,
                String.format(Locale.US, "%.0f", billing.percentLeft)
            ),
            isWarning = billing.usedPercent > 80.0,
            isCritical = billing.usedPercent > 95.0,
            windows = billing.toUsageWindows()
        )
    }

    private fun formatPeriodEnd(periodEnd: Instant?): String {
        if (periodEnd == null) return ""
        val diffMs = periodEnd.toEpochMilli() - System.currentTimeMillis()
        if (diffMs <= 0) return AppText.get(R.string.time_reset_soon)
        val hours = diffMs / (1000 * 60 * 60)
        val days = hours / 24
        val remHours = hours % 24
        return if (days > 0) {
            AppText.get(R.string.time_in_days_hours, days, remHours)
        } else {
            AppText.get(R.string.time_in_hours, hours)
        }
    }
}
