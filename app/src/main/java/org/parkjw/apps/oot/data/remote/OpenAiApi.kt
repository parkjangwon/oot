package org.parkjw.apps.oot.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.parkjw.apps.oot.data.model.AuthType
import org.parkjw.apps.oot.data.model.ProviderAccount
import org.parkjw.apps.oot.data.model.ProviderType
import org.parkjw.apps.oot.data.model.TokenStatus
import org.parkjw.apps.oot.data.model.VendorError
import org.parkjw.apps.oot.data.oauth.OAuthManager
import java.io.IOException
import java.util.Locale

class OpenAiApi(
    private val client: OkHttpClient,
    private val json: Json,
    private val oauthManager: OAuthManager? = null,
    private val onAccountRefreshed: (ProviderAccount) -> Unit = {}
) {
    suspend fun fetchStatus(account: ProviderAccount): TokenStatus = withContext(Dispatchers.IO) {
        val credential = account.activeCredential().trim()
        if (credential.isBlank()) {
            return@withContext errorStatus(
                account,
                openAiErrorText(VendorError.Unknown("OAuth 로그인 또는 API 키가 필요합니다."))
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
                    val windows = parseOpenAiWhamUsage(body, json)
                        ?: return errorStatus(account, openAiErrorText(VendorError.Parse))
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
                    onAccountRefreshed(refreshed)
                    return fetchWhamUsage(
                        refreshed,
                        refreshed.activeCredential().trim().removePrefix("Bearer ").trim(),
                        allowRefresh = false
                    )
                }
                else -> {
                    return errorStatus(account, openAiErrorText(mapOpenAiHttpError(resp.code)))
                }
            }
        }
    }

    private fun fetchApiKeyStatus(account: ProviderAccount, credential: String): TokenStatus {
        val baseUrl = account.customEndpoint?.takeIf { it.isNotBlank() } ?: ProviderType.OPENAI.baseUrl
        val authHeader = "Bearer $credential"

        return try {
            val request = Request.Builder()
                .url("$baseUrl/models")
                .addHeader("Authorization", authHeader)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
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
                        remainingFormatted = String.format(Locale.US, "%,d 토큰 남음", remainingTokens),
                        totalLimitFormatted = String.format(Locale.US, "한도: %,d", limitTokens),
                        usagePercent = percent,
                        resetTimeFormatted = resetTokens?.let { "리셋: $it" },
                        statusSummary = String.format(Locale.US, "%,dk 남음", remainingTokens / 1000),
                        isWarning = percent > 80f,
                        isCritical = percent > 95f
                    )
                }

                val summary = if (remainingRequests != null) "$remainingRequests req 남음" else "구독 활성화됨"
                TokenStatus(
                    accountId = account.id,
                    provider = ProviderType.OPENAI,
                    accountName = account.name,
                    remainingFormatted = summary,
                    statusSummary = "OpenAI 정상",
                    resetTimeFormatted = resetTokens?.let { "리셋: $it" }
                )
            }
        } catch (e: Exception) {
            errorStatus(account, openAiErrorText(VendorError.Network)).copy(
                errorMessage = e.localizedMessage ?: "연결 실패"
            )
        }
    }

    private fun errorStatus(account: ProviderAccount, text: String): TokenStatus = TokenStatus(
        accountId = account.id,
        provider = ProviderType.OPENAI,
        accountName = account.name,
        remainingFormatted = text,
        statusSummary = "확인 필요",
        isError = true,
        errorMessage = text
    )

    companion object {
        // Codex backend-client PathStyle::ChatGptApi + rate_limit_status_url
        const val WHAM_USAGE_URL = "https://chatgpt.com/backend-api/wham/usage"
    }
}
