package org.parkjw.apps.oot.data.oauth

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
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
    private val managerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var xaiPollingJob: Job? = null

    fun startOAuthFlow(provider: ProviderType) {
        val authEndpoint = provider.oauthAuthEndpoint ?: return
        val state = UUID.randomUUID().toString()
        val codeVerifier = generateCodeVerifier()
        val codeChallenge = generateCodeChallenge(codeVerifier)

        pendingVerifications[state] = Pair(provider, codeVerifier)

        val uriBuilder = Uri.parse(authEndpoint).buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", getClientIdForProvider(provider))
            .appendQueryParameter("code_challenge", codeChallenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("state", state)

        if (provider == ProviderType.CLAUDE) {
            uriBuilder.appendQueryParameter("code", "true")
            uriBuilder.appendQueryParameter("redirect_uri", CLAUDE_REDIRECT_URI)
        } else {
            uriBuilder.appendQueryParameter("redirect_uri", REDIRECT_URI)
        }

        if (provider.defaultScopes.isNotEmpty()) {
            uriBuilder.appendQueryParameter("scope", provider.defaultScopes.joinToString(" "))
        }

        val authUri = uriBuilder.build()
        openExternalBrowser(authUri.toString())
    }

    fun openExternalBrowser(url: String) {
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(browserIntent)
    }

    /**
     * RFC 8628 Device Authorization Grant for xAI (Grok)
     */
    fun startXaiDeviceFlow(
        onCodeReceived: (userCode: String, verificationUrl: String) -> Unit,
        onTokenReceived: (OAuthTokenResult) -> Unit,
        onError: (String) -> Unit
    ) {
        xaiPollingJob?.cancel()
        xaiPollingJob = managerScope.launch {
            try {
                val formBody = FormBody.Builder()
                    .add("client_id", XAI_CLIENT_ID)
                    .add("scope", "openid profile email offline_access grok-cli:access api:access")
                    .build()

                val request = Request.Builder()
                    .url("https://auth.x.ai/oauth2/device/code")
                    .addHeader("Accept", "application/json")
                    .post(formBody)
                    .build()

                val (deviceCode, userCode, verificationUrl, intervalSec) = client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        onError("xAI Device 인증 코드 요청 실패 (${response.code}): $body")
                        return@launch
                    }

                    val root = json.parseToJsonElement(body).jsonObject
                    val devCode = root["device_code"]?.jsonPrimitive?.content ?: ""
                    val uCode = root["user_code"]?.jsonPrimitive?.content ?: ""
                    val vUrl = root["verification_uri_complete"]?.jsonPrimitive?.content
                        ?: root["verification_uri"]?.jsonPrimitive?.content ?: "https://accounts.x.ai/oauth2/device"
                    val interval = root["interval"]?.jsonPrimitive?.longOrNull ?: 5L

                    Tuple4(devCode, uCode, vUrl, interval)
                }

                if (deviceCode.isBlank() || userCode.isBlank()) {
                    onError("유효한 xAI 디바이스 코드를 받지 못했습니다.")
                    return@launch
                }

                // Copy user code to clipboard for convenience
                withContext(Dispatchers.Main) {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("xAI Verification Code", userCode))
                    onCodeReceived(userCode, verificationUrl)
                }

                // Open phone's default external browser
                openExternalBrowser(verificationUrl)

                // Poll token endpoint
                pollXaiDeviceToken(deviceCode, intervalSec, onTokenReceived, onError)

            } catch (e: Exception) {
                onError("xAI Device 인증 요청 중 오류: ${e.localizedMessage}")
            }
        }
    }

    private suspend fun pollXaiDeviceToken(
        deviceCode: String,
        intervalSec: Long,
        onTokenReceived: (OAuthTokenResult) -> Unit,
        onError: (String) -> Unit
    ) {
        val maxDurationMs = 1800 * 1000L
        val startTime = System.currentTimeMillis()
        var currentInterval = intervalSec.coerceAtLeast(3)

        while (System.currentTimeMillis() - startTime < maxDurationMs) {
            delay(currentInterval * 1000L)

            val tokenForm = FormBody.Builder()
                .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                .add("client_id", XAI_CLIENT_ID)
                .add("device_code", deviceCode)
                .build()

            val tokenReq = Request.Builder()
                .url("https://auth.x.ai/oauth2/token")
                .addHeader("Accept", "application/json")
                .post(tokenForm)
                .build()

            try {
                client.newCall(tokenReq).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        val root = json.parseToJsonElement(body).jsonObject
                        val accessToken = root["access_token"]?.jsonPrimitive?.content ?: ""
                        val refreshToken = root["refresh_token"]?.jsonPrimitive?.content
                        val expiresIn = root["expires_in"]?.jsonPrimitive?.longOrNull ?: 3600L

                        if (accessToken.isNotBlank()) {
                            withContext(Dispatchers.Main) {
                                onTokenReceived(
                                    OAuthTokenResult(
                                        provider = ProviderType.XAI,
                                        accessToken = accessToken,
                                        refreshToken = refreshToken,
                                        expiresAt = System.currentTimeMillis() + (expiresIn * 1000L)
                                    )
                                )
                            }
                            return
                        }
                    } else {
                        val root = try { json.parseToJsonElement(body).jsonObject } catch (e: Exception) { null }
                        val error = root?.get("error")?.jsonPrimitive?.content
                        when (error) {
                            "authorization_pending" -> {
                                // Still waiting for user approval in browser
                            }
                            "slow_down" -> {
                                currentInterval += 5
                            }
                            "expired_token" -> {
                                onError("인증 시간이 만료되었습니다. 다시 시도해주세요.")
                                return
                            }
                            "access_denied" -> {
                                onError("사용자가 로그인을 취소하거나 거절했습니다.")
                                return
                            }
                            else -> {
                                // Continue waiting if transient
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore transient network errors during polling
            }
        }
        onError("xAI 인증 대기 시간이 초과되었습니다.")
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
            val redirectUri = if (provider == ProviderType.CLAUDE) CLAUDE_REDIRECT_URI else REDIRECT_URI

            val request = if (provider == ProviderType.CLAUDE) {
                // Anthropic requires JSON payload
                val jsonPayload = """{"grant_type":"authorization_code","client_id":"$CLAUDE_CLIENT_ID","code":"$code","state":"$state","redirect_uri":"$redirectUri","code_verifier":"$codeVerifier"}"""
                Request.Builder()
                    .url(tokenEndpoint)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "application/json")
                    .post(jsonPayload.toRequestBody("application/json".toMediaType()))
                    .build()
            } else {
                val formBody = FormBody.Builder()
                    .add("grant_type", "authorization_code")
                    .add("client_id", getClientIdForProvider(provider))
                    .add("code", code)
                    .add("redirect_uri", redirectUri)
                    .add("code_verifier", codeVerifier)
                    .build()

                Request.Builder()
                    .url(tokenEndpoint)
                    .addHeader("Accept", "application/json")
                    .post(formBody)
                    .build()
            }

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

    suspend fun exchangeClaudeAuthCode(rawInput: String): Result<OAuthTokenResult> = withContext(Dispatchers.IO) {
        val trimmed = rawInput.trim()
        val parts = trimmed.split("#")
        val code = parts[0].trim()
        val state = parts.getOrNull(1)?.trim()

        val entry = if (!state.isNullOrBlank()) {
            pendingVerifications.remove(state)
        } else {
            val key = pendingVerifications.keys.lastOrNull { pendingVerifications[it]?.first == ProviderType.CLAUDE }
            key?.let { pendingVerifications.remove(it) }
        }

        val codeVerifier = entry?.second
            ?: return@withContext Result.failure(Exception("Claude 인증 세션을 찾을 수 없습니다. 다시 브라우저 로그인을 시도해주세요."))

        val jsonPayload = """{"grant_type":"authorization_code","client_id":"$CLAUDE_CLIENT_ID","code":"$code","state":"${state.orEmpty()}","redirect_uri":"$CLAUDE_REDIRECT_URI","code_verifier":"$codeVerifier"}"""
        val request = Request.Builder()
            .url("https://console.anthropic.com/v1/oauth/token")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .post(jsonPayload.toRequestBody("application/json".toMediaType()))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Claude 토큰 교환 실패 (${response.code}): $body"))
                }

                val root = json.parseToJsonElement(body).jsonObject
                val accessToken = root["access_token"]?.jsonPrimitive?.content
                    ?: return@withContext Result.failure(Exception("응답에 access_token이 없습니다."))

                val refreshToken = root["refresh_token"]?.jsonPrimitive?.content
                val expiresIn = root["expires_in"]?.jsonPrimitive?.longOrNull ?: 3600L
                val expiresAt = System.currentTimeMillis() + (expiresIn * 1000L)

                Result.success(
                    OAuthTokenResult(
                        provider = ProviderType.CLAUDE,
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
            val request = if (account.provider == ProviderType.CLAUDE) {
                val jsonPayload = """{"grant_type":"refresh_token","client_id":"$CLAUDE_CLIENT_ID","refresh_token":"${account.refreshToken}"}"""
                Request.Builder()
                    .url(tokenEndpoint)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "application/json")
                    .post(jsonPayload.toRequestBody("application/json".toMediaType()))
                    .build()
            } else {
                val formBody = FormBody.Builder()
                    .add("grant_type", "refresh_token")
                    .add("client_id", getClientIdForProvider(account.provider))
                    .add("refresh_token", account.refreshToken)
                    .build()

                Request.Builder()
                    .url(tokenEndpoint)
                    .addHeader("Accept", "application/json")
                    .post(formBody)
                    .build()
            }

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

    fun getClientIdForProvider(provider: ProviderType): String {
        return when (provider) {
            ProviderType.CLAUDE -> CLAUDE_CLIENT_ID
            ProviderType.XAI -> XAI_CLIENT_ID
            ProviderType.OPENAI -> "oot-openai-client"
            ProviderType.CURSOR -> "oot-cursor-client"
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

    private data class Tuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

    companion object {
        const val REDIRECT_URI = "oot://oauth"
        const val CLAUDE_REDIRECT_URI = "https://console.anthropic.com/oauth/code/callback"

        // Official Client ID UUIDs
        const val CLAUDE_CLIENT_ID = "9d1c250a-e61b-44d9-88ed-5944d1962f5e"
        const val XAI_CLIENT_ID = "b1a00492-073a-47ea-816f-4c329264a828"
    }
}

data class OAuthTokenResult(
    val provider: ProviderType,
    val accessToken: String,
    val refreshToken: String?,
    val expiresAt: Long
)
