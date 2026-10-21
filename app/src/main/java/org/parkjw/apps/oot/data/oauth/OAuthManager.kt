package org.parkjw.apps.oot.data.oauth

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.browser.customtabs.CustomTabsIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.parkjw.apps.oot.data.model.ProviderAccount
import org.parkjw.apps.oot.data.model.ProviderType
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

class OAuthManager(
    private val context: Context,
    private val client: OkHttpClient,
    private val json: Json
) {
    // Session state holding pending PKCE verifiers: key = state, value = (provider, verifier)
    private val pendingVerifications = mutableMapOf<String, Pair<ProviderType, String>>()

    fun startOAuthFlow(provider: ProviderType) {
        val authEndpoint = provider.oauthAuthEndpoint ?: return
        val state = UUID.randomUUID().toString()
        val codeVerifier = generateCodeVerifier()
        val codeChallenge = generateCodeChallenge(codeVerifier)

        pendingVerifications[state] = Pair(provider, codeVerifier)

        val uriBuilder = Uri.parse(authEndpoint).buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", getClientIdForProvider(provider))
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge", codeChallenge)
            .appendQueryParameter("code_challenge_method", "S256")

        if (provider.defaultScopes.isNotEmpty()) {
            uriBuilder.appendQueryParameter("scope", provider.defaultScopes.joinToString(" "))
        }

        val authUri = uriBuilder.build()

        val browserIntent = Intent(Intent.ACTION_VIEW, authUri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(browserIntent)
    }

    fun openExternalBrowser(url: String) {
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(browserIntent)
    }

    suspend fun handleOAuthCallback(uri: Uri): Result<OAuthTokenResult> = withContext(Dispatchers.IO) {
        val code = uri.getQueryParameter("code")
        val state = uri.getQueryParameter("state")
        val error = uri.getQueryParameter("error")

        if (error != null) {
            val errorDesc = uri.getQueryParameter("error_description") ?: error
            return@withContext Result.failure(Exception("OAuth 인증 거부: $errorDesc"))
        }

        if (code == null || state == null) {
            return@withContext Result.failure(Exception("유효하지 않은 인증 콜백 데이터입니다."))
        }

        val pending = pendingVerifications.remove(state)
            ?: return@withContext Result.failure(Exception("만료되었거나 일치하지 않는 세션 상태(state)입니다."))

        val (provider, codeVerifier) = pending
        val tokenEndpoint = provider.oauthTokenEndpoint
            ?: return@withContext Result.failure(Exception("토큰 엔드포인트가 정의되지 않았습니다."))

        try {
            val formBody = FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("client_id", getClientIdForProvider(provider))
                .add("code", code)
                .add("redirect_uri", REDIRECT_URI)
                .add("code_verifier", codeVerifier)
                .build()

            val request = Request.Builder()
                .url(tokenEndpoint)
                .post(formBody)
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("토큰 교환 실패 (${response.code}): $body"))
                }

                val root = json.parseToJsonElement(body).jsonObject
                val accessToken = root["access_token"]?.jsonPrimitive?.content
                    ?: return@withContext Result.failure(Exception("응답에 access_token이 없습니다."))

                val refreshToken = root["refresh_token"]?.jsonPrimitive?.content
                val expiresIn = root["expires_in"]?.jsonPrimitive?.longOrNull ?: 3600L
                val expiresAt = System.currentTimeMillis() + (expiresIn * 1000L)

                Result.success(
                    OAuthTokenResult(
                        provider = provider,
                        accessToken = accessToken,
                        refreshToken = refreshToken,
                        expiresAt = expiresAt
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun refreshTokenIfNeeded(account: ProviderAccount): ProviderAccount = withContext(Dispatchers.IO) {
        if (!account.isTokenExpired || account.refreshToken == null) {
            return@withContext account
        }

        val tokenEndpoint = account.provider.oauthTokenEndpoint ?: return@withContext account

        try {
            val formBody = FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("client_id", getClientIdForProvider(account.provider))
                .add("refresh_token", account.refreshToken)
                .build()

            val request = Request.Builder()
                .url(tokenEndpoint)
                .post(formBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext account

                val body = response.body?.string().orEmpty()
                val root = json.parseToJsonElement(body).jsonObject
                val newAccessToken = root["access_token"]?.jsonPrimitive?.content ?: return@withContext account
                val newRefreshToken = root["refresh_token"]?.jsonPrimitive?.content ?: account.refreshToken
                val expiresIn = root["expires_in"]?.jsonPrimitive?.longOrNull ?: 3600L

                account.copy(
                    accessToken = newAccessToken,
                    refreshToken = newRefreshToken,
                    tokenExpiresAt = System.currentTimeMillis() + (expiresIn * 1000L)
                )
            }
        } catch (e: Exception) {
            account
        }
    }

    private fun getClientIdForProvider(provider: ProviderType): String {
        return when (provider) {
            ProviderType.CLAUDE -> "oot-claude-mobile-client"
            ProviderType.OPENAI -> "oot-openai-mobile-client"
            ProviderType.XAI -> "oot-xai-grok-mobile-client"
            ProviderType.CURSOR -> "oot-cursor-mobile-client"
            else -> "oot-client"
        }
    }

    private fun generateCodeVerifier(): String {
        val secureRandom = SecureRandom()
        val code = ByteArray(32)
        secureRandom.nextBytes(code)
        return Base64.encodeToString(code, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun generateCodeChallenge(verifier: String): String {
        val bytes = verifier.toByteArray(Charsets.US_ASCII)
        val messageDigest = MessageDigest.getInstance("SHA-256")
        messageDigest.update(bytes, 0, bytes.size)
        val digest = messageDigest.digest()
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    companion object {
        const val REDIRECT_URI = "oot://oauth"
    }
}

data class OAuthTokenResult(
    val provider: ProviderType,
    val accessToken: String,
    val refreshToken: String?,
    val expiresAt: Long
)
