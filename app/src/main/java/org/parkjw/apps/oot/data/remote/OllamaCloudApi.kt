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
import kotlin.math.roundToInt

class OllamaCloudApi(
    private val client: OkHttpClient,
    private val json: Json
) {
    suspend fun fetchStatus(account: ProviderAccount): TokenStatus = withContext(Dispatchers.IO) {
        val credential = account.activeCredential().trim().removePrefix("Bearer ").trim()
        if (credential.isBlank()) {
            return@withContext errorStatus(
                account,
                remainingFormatted = AppText.get(R.string.data_no_api_key),
                errorMessage = AppText.get(R.string.data_ollama_key_required)
            )
        }

        val baseUrl = account.customEndpoint?.takeIf { it.isNotBlank() } ?: ProviderType.OLLAMA_CLOUD.baseUrl
        val request = Request.Builder()
            .url("$baseUrl/api/balance")
            .addHeader("Authorization", "Bearer $credential")
            .addHeader("Accept", "application/json")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                when {
                    response.isSuccessful -> {
                        val windows = OllamaCloudCodec.parse(body, json)
                        when {
                            windows == null -> errorStatus(
                                account,
                                remainingFormatted = AppText.get(R.string.data_fetch_failed),
                                errorMessage = AppText.get(R.string.data_ollama_parse_failed)
                            )
                            windows.isEmpty() -> errorStatus(
                                account,
                                remainingFormatted = AppText.get(R.string.data_no_usage_info),
                                errorMessage = AppText.get(R.string.data_ollama_empty_usage)
                            )
                            else -> successStatus(account, windows)
                        }
                    }
                    response.code == 429 -> rateLimitedTokenStatus(
                        account,
                        ProviderType.OLLAMA_CLOUD,
                        response.header("Retry-After")
                    )
                    response.code == 401 -> errorStatus(
                        account,
                        remainingFormatted = AppText.get(R.string.data_auth_failed_code, 401),
                        errorMessage = AppText.get(R.string.data_ollama_key_invalid)
                    )
                    else -> errorStatus(
                        account,
                        remainingFormatted = "HTTP ${response.code}",
                        errorMessage = AppText.get(R.string.data_ollama_server_error, response.code)
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
        val text = windows.joinToString(" | ") { window ->
            when (val remaining = window.remaining) {
                is Remaining.Percent -> AppText.get(
                    R.string.data_window_percent_left,
                    window.label,
                    remaining.percentLeft.roundToInt()
                )
                is Remaining.Balance -> "${window.label} ${NotificationContent.formatBalance(remaining.amountLeft, remaining.currency)}"
            }
        }
        return TokenStatus(
            accountId = account.id,
            provider = ProviderType.OLLAMA_CLOUD,
            accountName = account.name,
            remainingFormatted = text,
            statusSummary = "OL: ${windows.first().label}",
            windows = windows
        )
    }

    private fun errorStatus(
        account: ProviderAccount,
        remainingFormatted: String,
        errorMessage: String
    ): TokenStatus = TokenStatus(
        accountId = account.id,
        provider = ProviderType.OLLAMA_CLOUD,
        accountName = account.name,
        remainingFormatted = remainingFormatted,
        statusSummary = AppText.get(R.string.status_needs_check),
        isError = true,
        errorMessage = errorMessage
    )
}
