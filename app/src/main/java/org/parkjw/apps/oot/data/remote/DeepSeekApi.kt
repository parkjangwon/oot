package org.parkjw.apps.oot.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.parkjw.apps.oot.R
import org.parkjw.apps.oot.data.model.ProviderAccount
import org.parkjw.apps.oot.data.model.ProviderType
import org.parkjw.apps.oot.data.model.Remaining
import org.parkjw.apps.oot.data.model.TokenStatus
import org.parkjw.apps.oot.data.model.UsageWindow
import org.parkjw.apps.oot.i18n.AppText
import org.parkjw.apps.oot.notification.NotificationContent

class DeepSeekApi(
    private val client: OkHttpClient,
    private val json: Json
) {
    suspend fun fetchStatus(account: ProviderAccount): TokenStatus = withContext(Dispatchers.IO) {
        val credential = account.activeCredential().trim().removePrefix("Bearer ").trim()
        if (credential.isBlank()) {
            return@withContext errorStatus(
                account,
                remainingFormatted = AppText.get(R.string.data_no_api_key),
                errorMessage = AppText.get(R.string.data_deepseek_key_required)
            )
        }

        val baseUrl = resolveBaseUrl(account.customEndpoint, ProviderType.DEEPSEEK.baseUrl)
            ?: return@withContext customEndpointErrorStatus(account)
        val request = Request.Builder()
            .url("$baseUrl/user/balance")
            .addHeader("Authorization", "Bearer $credential")
            .addHeader("Accept", "application/json")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                when {
                    response.isSuccessful -> {
                        val windows = DeepSeekCodec.parse(body, json)
                        when {
                            windows == null -> errorStatus(
                                account,
                                remainingFormatted = AppText.get(R.string.data_fetch_failed),
                                errorMessage = AppText.get(R.string.data_deepseek_parse_failed)
                            )
                            windows.isEmpty() -> errorStatus(
                                account,
                                remainingFormatted = AppText.get(R.string.data_no_balance),
                                errorMessage = AppText.get(R.string.data_deepseek_empty_balance)
                            )
                            else -> successStatus(account, windows)
                        }
                    }
                    response.code == 429 -> rateLimitedTokenStatus(
                        account,
                        ProviderType.DEEPSEEK,
                        response.header("Retry-After")
                    )
                    response.code == 401 -> errorStatus(
                        account,
                        remainingFormatted = AppText.get(R.string.data_auth_failed_code, 401),
                        errorMessage = AppText.get(R.string.data_deepseek_key_invalid)
                    )
                    else -> errorStatus(
                        account,
                        remainingFormatted = "HTTP ${response.code}",
                        errorMessage = AppText.get(R.string.data_deepseek_server_error, response.code)
                    )
                }
            }
        } catch (e: Exception) {
            errorStatus(
                account,
                remainingFormatted = AppText.get(R.string.data_error_network),
                errorMessage = e.localizedMessage ?: AppText.get(R.string.data_connection_failed)
            )
        }
    }

    private fun successStatus(account: ProviderAccount, windows: List<UsageWindow>): TokenStatus {
        val balances = windows.mapNotNull { it.remaining as? Remaining.Balance }
        val total = balances.sumOf { it.amountLeft }
        val currency = balances.firstOrNull()?.currency ?: "USD"
        val formatted = NotificationContent.formatBalance(total, currency)
        return TokenStatus(
            accountId = account.id,
            provider = ProviderType.DEEPSEEK,
            accountName = account.name,
            remainingFormatted = AppText.get(R.string.remaining_left, formatted),
            statusSummary = "DS: $formatted",
            windows = windows
        )
    }

    private fun errorStatus(
        account: ProviderAccount,
        remainingFormatted: String,
        errorMessage: String
    ): TokenStatus = TokenStatus(
        accountId = account.id,
        provider = ProviderType.DEEPSEEK,
        accountName = account.name,
        remainingFormatted = remainingFormatted,
        statusSummary = AppText.get(R.string.status_needs_check),
        isError = true,
        errorMessage = errorMessage
    )
}
