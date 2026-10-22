package org.parkjw.apps.oot.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.parkjw.apps.oot.R
import org.parkjw.apps.oot.data.model.AuthType
import org.parkjw.apps.oot.data.model.ProviderAccount
import org.parkjw.apps.oot.data.model.ProviderType
import org.parkjw.apps.oot.data.model.TokenStatus
import org.parkjw.apps.oot.data.model.VendorError
import org.parkjw.apps.oot.data.oauth.OAuthManager
import org.parkjw.apps.oot.i18n.AppText
import java.io.IOException
import java.util.Locale

class OpenAiApi(
    private val client: OkHttpClient,
    private val json: Json,
    private val oauthManager: OAuthManager? = null,
    private val onAccountRefreshed: (ProviderAccount) -> Boolean = { true }
) {
    suspend fun fetchStatus(account: ProviderAccount): TokenStatus = withContext(Dispatchers.IO) {
        val credential = account.activeCredential().trim()
        if (credential.isBlank()) {
            return@withContext errorStatus(
                account,
                openAiErrorText(VendorError.Unknown(AppText.get(R.string.data_openai_key_required)))
            )
        }

        val isOAuth = account.authType == AuthType.OAUTH ||
            credential.startsWith("Bearer ", ignoreCase = true)
        val cleanToken = credential.removePrefix("Bearer ").trim()

        if (isOAuth) {
            return@withContext fetchWhamUsage(account, cleanToken, allowRefresh = true)
        }

        fetchApiKeyStatus(account, cleanToken)
    }

    private suspend fun fetchWhamUsage(
        account: ProviderAccount,
        token: String,
        allowRefresh: Boolean
    ): TokenStatus {
        val requestBuilder = Request.Builder()
            .url(WHAM_USAGE_URL)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Accept", "application/json")
        account.chatgptAccountId?.takeIf { it.isNotBlank() }?.let {
            requestBuilder.addHeader("ChatGPT-Account-Id", it)
        }
        val request = requestBuilder.build()

        val response = try {
            client.newCall(request).execute()
        } catch (_: IOException) {
            return errorStatus(account, openAiErrorText(VendorError.Network))
        } catch (_: Exception) {
            return errorStatus(account, openAiErrorText(VendorError.Network))
        }

        response.use { resp ->
            when {
                resp.isSuccessful -> {
                    val body = resp.body?.string().orEmpty()
                    val windows = try {
                        parseOpenAiWhamUsage(body, json)
                    } catch (e: Exception) {
                        Log.w(TAG, "usage parse threw endpoint=/backend-api/wham/usage error=${e.javaClass.simpleName}")
                        null
                    }
                    if (windows == null) {
                        Log.w(TAG, "usage parse failed endpoint=/backend-api/wham/usage error=Parse bytes=${body.toByteArray().size}")
                        return errorStatus(account, openAiErrorText(VendorError.Parse))
                    }
                    return openAiOAuthSuccessStatus(account, windows)
                }
                resp.code == 401 -> {
                    if (!allowRefresh || oauthManager == null) {
                        return errorStatus(account, openAiErrorText(VendorError.Unauthorized))
                    }
                    val refreshed = try {
                        oauthManager.refreshTokenIfNeeded(account, force = true)
                    } catch (_: Exception) {
                        account
                    }
                    if (refreshed == account || refreshed.refreshToken.isNullOrBlank()) {
                        return errorStatus(account, openAiErrorText(VendorError.Unauthorized))
                    }
                    if (!onAccountRefreshed(refreshed)) {
                        return errorStatus(account, openAiErrorText(VendorError.Unauthorized))
                    }
                    return fetchWhamUsage(
                        refreshed,
                        refreshed.activeCredential().trim().removePrefix("Bearer ").trim(),
                        allowRefresh = false
                    )
                }
                resp.code == 429 -> {
                    return rateLimitedTokenStatus(account, ProviderType.OPENAI, resp.header("Retry-After"))
                }
                else -> {
                    return errorStatus(account, openAiErrorText(mapOpenAiHttpError(resp.code)))
                }
            }
        }
    }

    private fun fetchApiKeyStatus(account: ProviderAccount, credential: String): TokenStatus {
        val baseUrl = resolveBaseUrl(account.customEndpoint, ProviderType.OPENAI.baseUrl)
            ?: return customEndpointErrorStatus(account)
        val authHeader = "Bearer $credential"

        return try {
            val request = Request.Builder()
                .url("$baseUrl/models")
                .addHeader("Authorization", authHeader)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    if (response.code == 429) {
                        return rateLimitedTokenStatus(
                            account,
                            ProviderType.OPENAI,
                            response.header("Retry-After"),
                        )
                    }
                    if (response.code == 401) {
                        return errorStatus(account, openAiErrorText(VendorError.Unauthorized))
                    }
                    return errorStatus(account, openAiErrorText(mapOpenAiHttpError(response.code)))
                }

                val limitTokens = response.header("x-ratelimit-limit-tokens")?.toLongOrNull()
                val remainingTokens = response.header("x-ratelimit-remaining-tokens")?.toLongOrNull()
                val resetTokens = response.header("x-ratelimit-reset-tokens")
                val remainingRequests = response.header("x-ratelimit-remaining-requests")

                if (limitTokens != null && remainingTokens != null && limitTokens > 0) {
                    val used = limitTokens - remainingTokens
                    val percent = ((used.toDouble() / limitTokens.toDouble()) * 100)
                        .toFloat()
                        .coerceIn(0f, 100f)

                    return TokenStatus(
                        accountId = account.id,
                        provider = ProviderType.OPENAI,
                        accountName = account.name,
                        remainingFormatted = AppText.get(
                            R.string.data_tokens_left,
                            String.format(Locale.US, "%,d", remainingTokens)
                        ),
                        totalLimitFormatted = AppText.get(
                            R.string.data_limit_value,
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
                    AppText.get(R.string.data_reqs_left, remainingRequests)
                } else {
                    AppText.get(R.string.data_subscription_active)
                }
                TokenStatus(
                    accountId = account.id,
                    provider = ProviderType.OPENAI,
                    accountName = account.name,
                    remainingFormatted = summary,
                    statusSummary = AppText.get(R.string.data_openai_ok),
                    resetTimeFormatted = resetTokens?.let { AppText.get(R.string.data_reset_value, it) }
                )
            }
        } catch (e: Exception) {
            errorStatus(account, openAiErrorText(VendorError.Network)).copy(
                errorMessage = e.localizedMessage ?: AppText.get(R.string.data_connection_failed)
            )
        }
    }

    private fun errorStatus(account: ProviderAccount, text: String): TokenStatus = TokenStatus(
        accountId = account.id,
        provider = ProviderType.OPENAI,
        accountName = account.name,
        remainingFormatted = text,
        statusSummary = AppText.get(R.string.status_needs_check),
        isError = true,
        errorMessage = text
    )

    companion object {
        private const val TAG = "oot.OpenAi"

        // Codex backend-client PathStyle::ChatGptApi + rate_limit_status_url
        const val WHAM_USAGE_URL = "https://chatgpt.com/backend-api/wham/usage"
    }
}
