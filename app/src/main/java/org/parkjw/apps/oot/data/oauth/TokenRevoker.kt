package org.parkjw.apps.oot.data.oauth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.FormBody
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.parkjw.apps.oot.data.model.AuthType
import org.parkjw.apps.oot.data.model.ProviderAccount
import org.parkjw.apps.oot.data.model.ProviderType

/**
 * Best-effort RFC 7009 token revocation, run when an account is deleted.
 * Never throws and never logs token values; a failure leaves the provider token to expire on its own.
 */
class TokenRevoker(
    private val post: (url: String, form: Map<String, String>) -> Unit,
    private val endpointFor: (ProviderType) -> String? = { it.oauthRevocationEndpoint },
    private val clientIdFor: (ProviderType) -> String = { "oot-client" },
    private val timeoutMs: Long = TIMEOUT_MS,
) {
    suspend fun revoke(account: ProviderAccount) {
        if (account.authType != AuthType.OAUTH) return
        val url = endpointFor(account.provider) ?: return
        // RFC 7009: revoking the refresh token also invalidates grants issued from it.
        val (token, hint) = account.refreshToken?.takeIf { it.isNotBlank() }?.let { it to "refresh_token" }
            ?: account.accessToken?.takeIf { it.isNotBlank() }?.let { it to "access_token" }
            ?: return
        val form = mapOf("token" to token, "token_type_hint" to hint, "client_id" to clientIdFor(account.provider))
        try {
            withTimeoutOrNull(timeoutMs) { withContext(Dispatchers.IO) { post(url, form) } }
        } catch (_: Exception) {
            // best effort
        }
    }

    companion object {
        const val TIMEOUT_MS = 5_000L

        fun http(client: OkHttpClient): (String, Map<String, String>) -> Unit {
            val short = client.newBuilder().callTimeout(TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS).build()
            return { url, form ->
                // OpenAI's own client sends JSON; other providers use the RFC 7009 form encoding.
                val body = if (url.startsWith(OAuthManager.OPENAI_ISSUER)) {
                    JsonObject(form.mapValues { JsonPrimitive(it.value) }).toString()
                        .toRequestBody("application/json".toMediaType())
                } else {
                    FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build()
                }
                short.newCall(Request.Builder().url(url).post(body).build()).execute().close()
            }
        }
    }
}
