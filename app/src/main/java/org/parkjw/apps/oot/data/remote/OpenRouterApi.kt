package org.parkjw.apps.oot.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import org.parkjw.apps.oot.R
import org.parkjw.apps.oot.data.model.ProviderAccount
import org.parkjw.apps.oot.data.model.ProviderType
import org.parkjw.apps.oot.data.model.Remaining
import org.parkjw.apps.oot.data.model.TokenStatus
import org.parkjw.apps.oot.data.model.UsageWindow
import org.parkjw.apps.oot.i18n.AppText
import java.util.Locale

// Pure codec for the public OpenRouter API responses (GET /key, GET /credits).
// No Android or network types so it is unit-testable on the JVM.
object OpenRouterCodec {
    data class KeyData(
        val label: String?,
        val limit: Double?,
        val limitRemaining: Double?,
        val usage: Double
    )

    data class CreditsData(val totalCredits: Double, val totalUsage: Double)

    fun parseKey(body: String): KeyData? = try {
        val data = Json.parseToJsonElement(body).jsonObject["data"]?.jsonObject ?: return null
        KeyData(
            label = data["label"]?.jsonPrimitive?.contentOrNull,
            limit = data["limit"]?.jsonPrimitive?.doubleOrNull,
            limitRemaining = data["limit_remaining"]?.jsonPrimitive?.doubleOrNull,
            usage = data["usage"]?.jsonPrimitive?.doubleOrNull ?: 0.0
        )
    } catch (_: Exception) {
        null
    }

    fun parseCredits(body: String): CreditsData? = try {
        val data = Json.parseToJsonElement(body).jsonObject["data"]?.jsonObject ?: return null
        CreditsData(
            totalCredits = data["total_credits"]?.jsonPrimitive?.doubleOrNull ?: return null,
            totalUsage = data["total_usage"]?.jsonPrimitive?.doubleOrNull ?: 0.0
        )
    } catch (_: Exception) {
        null
    }

    fun accountBalance(credits: CreditsData): Double =
        (credits.totalCredits - credits.totalUsage).coerceAtLeast(0.0)

    fun formatUsd(amount: Double): String = "$${String.format(Locale.US, "%.2f", amount)}"

    // "credit" window = account balance; "key" window only when the key has a limit.
    fun windows(credits: CreditsData?, key: KeyData?): List<UsageWindow> = buildList {
        credits?.let {
            add(UsageWindow("credit", "credit", Remaining.Balance(accountBalance(it))))
        }
        key?.let { k ->
            val limit = k.limit?.takeIf { it > 0.0 } ?: return@buildList
            val remaining = k.limitRemaining ?: (limit - k.usage).coerceAtLeast(0.0)
            add(UsageWindow("key", formatUsd(limit), Remaining.Balance(remaining)))
        }
    }
}

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
                remainingFormatted = AppText.get(R.string.data_no_api_key),
                statusSummary = AppText.get(R.string.data_key_needed),
                isError = true,
                errorMessage = AppText.get(R.string.data_openrouter_key_required)
            )
        }

        val baseUrl = resolveBaseUrl(account.customEndpoint, ProviderType.OPENROUTER.baseUrl)
            ?: return@withContext customEndpointErrorStatus(account)
        val authHeader = "Bearer $credential"

        try {
            var key: OpenRouterCodec.KeyData? = null
            var credits: OpenRouterCodec.CreditsData? = null

            val keyRequest = Request.Builder()
                .url("$baseUrl/key")
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
                if (response.code == 401) {
                    return@withContext TokenStatus(
                        accountId = account.id,
                        provider = ProviderType.OPENROUTER,
                        accountName = account.name,
                        remainingFormatted = AppText.get(R.string.data_auth_failed),
                        statusSummary = AppText.get(R.string.data_auth_error_code, 401),
                        isError = true,
                        errorMessage = AppText.get(R.string.data_openrouter_key_invalid)
                    )
                }
                if (response.isSuccessful) {
                    key = response.body?.string()?.let { OpenRouterCodec.parseKey(it) }
                }
            }

            // /credits may need a management key; 401/403 fall back to key data only.
            val creditsRequest = Request.Builder()
                .url("$baseUrl/credits")
                .addHeader("Authorization", authHeader)
                .build()

            client.newCall(creditsRequest).execute().use { response ->
                if (response.isSuccessful) {
                    credits = response.body?.string()?.let { OpenRouterCodec.parseCredits(it) }
                }
            }

            val windows = OpenRouterCodec.windows(credits, key)
            // /credits can be forbidden for a non-management key, so either window can be the only one.
            val credit = windows.firstOrNull { it.id == "credit" }?.remaining as? Remaining.Balance
            val keyWindow = windows.firstOrNull { it.id == "key" }
            val keyBalance = keyWindow?.remaining as? Remaining.Balance
            // Ticker/persistence value: the smaller of account balance and key remaining.
            val displayAmount = listOfNotNull(credit?.amountLeft, keyBalance?.amountLeft).minOrNull()
                ?: return@withContext loadErrorStatus(account)
            val displayText = AppText.get(R.string.remaining_left, OpenRouterCodec.formatUsd(displayAmount))

            TokenStatus(
                accountId = account.id,
                provider = ProviderType.OPENROUTER,
                accountName = account.name,
                remainingFormatted = displayText,
                totalLimitFormatted = keyWindow?.label,
                usagePercent = null,
                statusSummary = displayText,
                isWarning = false,
                isCritical = false,
                windows = windows
            )
        } catch (e: Exception) {
            TokenStatus(
                accountId = account.id,
                provider = ProviderType.OPENROUTER,
                accountName = account.name,
                remainingFormatted = AppText.get(R.string.data_error_network),
                statusSummary = AppText.get(R.string.data_network_failed),
                isError = true,
                errorMessage = e.localizedMessage ?: AppText.get(R.string.data_connection_failed)
            )
        }
    }

    private fun loadErrorStatus(account: ProviderAccount) = TokenStatus(
        accountId = account.id,
        provider = ProviderType.OPENROUTER,
        accountName = account.name,
        remainingFormatted = AppText.get(R.string.data_fetch_failed),
        statusSummary = AppText.get(R.string.data_comm_failed),
        isError = true,
        errorMessage = AppText.get(R.string.data_openrouter_server_error)
    )
}
