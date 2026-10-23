package org.parkjw.apps.oot.data.oauth

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.parkjw.apps.oot.data.model.AuthType
import org.parkjw.apps.oot.data.model.ProviderAccount
import org.parkjw.apps.oot.data.model.ProviderType

/** What deleting an account does to the provider-side credential. */
enum class RevocationMode {
    /** The provider documents an RFC 7009 revocation endpoint; oot asks it to revoke the sign-in. */
    SERVER,

    /** No documented revoke endpoint: oot only deletes the credential on this device. The user revokes it in the service. */
    DEVICE_ONLY,
}

enum class RevokeResult { REVOKED, FAILED, UNSUPPORTED }

/**
 * Providers covered by the revoke-on-delete behaviour: the widely used ones among oot's providers.
 * Z.ai, Ollama Cloud and OpenCode Go are not covered; deleting them removes the key from the device as before.
 * See docs/token-revocation.md.
 */
val REVOCATION_COVERED_PROVIDERS: Set<ProviderType> = setOf(
    ProviderType.CLAUDE,
    ProviderType.OPENAI,
    ProviderType.XAI,
    ProviderType.OPENROUTER,
    ProviderType.DEEPSEEK,
)

/** null = provider not covered (no server call, no device-only note). */
fun ProviderAccount.revocationMode(
    endpointFor: (ProviderType) -> String? = { it.oauthRevocationEndpoint },
): RevocationMode? = when {
    provider !in REVOCATION_COVERED_PROVIDERS -> null
    authType == AuthType.OAUTH && endpointFor(provider) != null -> RevocationMode.SERVER
    else -> RevocationMode.DEVICE_ONLY
}

/**
 * Best-effort RFC 7009 token revocation, run after an account has already been deleted from the device.
 * Never throws and never logs token values; a failure leaves the provider token to expire on its own.
 */
class TokenRevoker(
    /** Sends the form to the URL and returns the HTTP status code. */
    private val post: (url: String, form: Map<String, String>) -> Int,
    private val endpointFor: (ProviderType) -> String? = { it.oauthRevocationEndpoint },
    private val clientIdFor: (ProviderType) -> String = { "oot-client" },
    private val timeoutMs: Long = TIMEOUT_MS,
) {
    suspend fun revoke(account: ProviderAccount): RevokeResult {
        if (account.revocationMode(endpointFor) != RevocationMode.SERVER) return RevokeResult.UNSUPPORTED
        val url = endpointFor(account.provider) ?: return RevokeResult.UNSUPPORTED
        // RFC 7009: revoking the refresh token also invalidates grants issued from it.
        val (token, hint) = account.refreshToken?.takeIf { it.isNotBlank() }?.let { it to "refresh_token" }
            ?: account.accessToken?.takeIf { it.isNotBlank() }?.let { it to "access_token" }
            ?: return RevokeResult.UNSUPPORTED
        val form = mapOf("token" to token, "token_type_hint" to hint, "client_id" to clientIdFor(account.provider))
        return try {
            val status = withTimeoutOrNull(timeoutMs) { withContext(Dispatchers.IO) { post(url, form) } }
            // RFC 7009 §2.2: 200 for a revoked token and for one that was already invalid.
            if (status == 200) RevokeResult.REVOKED else RevokeResult.FAILED
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            RevokeResult.FAILED
        }
    }

    companion object {
        const val TIMEOUT_MS = 5_000L

        fun http(client: OkHttpClient): (String, Map<String, String>) -> Int {
            val short = client.newBuilder().callTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS).build()
            return { url, form ->
                // RFC 7009 §2.1: application/x-www-form-urlencoded; both documented endpoints accept it.
                val body = FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build()
                short.newCall(Request.Builder().url(url).post(body).build()).execute().use { it.code }
            }
        }
    }
}
