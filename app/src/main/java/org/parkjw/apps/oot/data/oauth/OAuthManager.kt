package org.parkjw.apps.oot.data.oauth

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
import org.parkjw.apps.oot.R
import org.parkjw.apps.oot.data.model.ProviderAccount
import org.parkjw.apps.oot.data.model.ProviderType
import org.parkjw.apps.oot.i18n.AppText
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
    private var openaiPollingJob: Job? = null

    // Bumped by cancelPendingFlows(): a sign-in that was in flight during a secure-storage reset must not save.
    @Volatile
    private var flowGeneration = 0

    /** Cancel pending PKCE/device-code sign-ins; callbacks from before the reset are ignored. */
    @Synchronized
    fun cancelPendingFlows() {
        flowGeneration++
        xaiPollingJob?.cancel()
        xaiPollingJob = null
        openaiPollingJob?.cancel()
        openaiPollingJob = null
        pendingVerifications.clear()
    }

    internal fun hasPendingVerificationFlow(): Boolean = pendingVerifications.isNotEmpty()

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

        when (provider) {
            ProviderType.CLAUDE -> {
                uriBuilder.appendQueryParameter("code", "true")
                uriBuilder.appendQueryParameter("redirect_uri", CLAUDE_REDIRECT_URI)
            }
            else -> {
                uriBuilder.appendQueryParameter("redirect_uri", REDIRECT_URI)
            }
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
        val generation = flowGeneration
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
                        if (generation != flowGeneration) return@launch
                        onError(AppText.get(R.string.oauth_xai_code_request_failed, response.code, body))
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

                if (generation != flowGeneration) return@launch

                if (deviceCode.isBlank() || userCode.isBlank()) {
                    onError(AppText.get(R.string.oauth_xai_no_device_code))
                    return@launch
                }

                // Copy user code to clipboard for convenience
                withContext(Dispatchers.Main) {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(sensitiveClip("xAI Verification Code", userCode))
                    onCodeReceived(userCode, verificationUrl)
                }

                // Open phone's default external browser
                openExternalBrowser(verificationUrl)

                // Poll token endpoint
                pollXaiDeviceToken(deviceCode, intervalSec, generation, onTokenReceived, onError)

            } catch (e: Exception) {
                if (generation != flowGeneration) return@launch
                onError(AppText.get(R.string.oauth_xai_request_error, e.localizedMessage ?: ""))
            }
        }
    }

    private suspend fun pollXaiDeviceToken(
        deviceCode: String,
        intervalSec: Long,
        generation: Int,
        onTokenReceived: (OAuthTokenResult) -> Unit,
        onError: (String) -> Unit
    ) {
        val maxDurationMs = 1800 * 1000L
        val startTime = System.currentTimeMillis()
        var currentInterval = intervalSec.coerceAtLeast(3)

        while (System.currentTimeMillis() - startTime < maxDurationMs) {
            delay(currentInterval * 1000L)
            if (generation != flowGeneration) return

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
                    if (generation != flowGeneration) return
                    if (response.isSuccessful) {
                        val root = json.parseToJsonElement(body).jsonObject
                        val accessToken = root["access_token"]?.jsonPrimitive?.content ?: ""
                        val refreshToken = root["refresh_token"]?.jsonPrimitive?.content
                        val expiresIn = root["expires_in"]?.jsonPrimitive?.longOrNull ?: 3600L

                        if (accessToken.isNotBlank()) {
                            val result = OAuthTokenResult(
                                provider = ProviderType.XAI,
                                accessToken = accessToken,
                                refreshToken = refreshToken,
                                expiresAt = System.currentTimeMillis() + (expiresIn * 1000L)
                            )
                            withContext(Dispatchers.Main) {
                                // A reset (or a newer sign-in) while this response was in flight wins.
                                if (generation == flowGeneration) onTokenReceived(result)
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
                                if (generation != flowGeneration) return
                                onError(AppText.get(R.string.oauth_auth_expired))
                                return
                            }
                            "access_denied" -> {
                                if (generation != flowGeneration) return
                                onError(AppText.get(R.string.oauth_user_denied))
                                return
                            }
                            else -> {
                                // Continue waiting
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore transient network errors during polling
            }
        }
        if (generation != flowGeneration) return
        onError(AppText.get(R.string.oauth_xai_wait_timeout))
    }

    /**
     * OpenAI / ChatGPT device-code login (Codex CLI flow).
     * Source: openai/codex codex-rs/login/src/device_code_auth.rs
     */
    fun startOpenAiDeviceFlow(
        onCodeReceived: (userCode: String, verificationUrl: String) -> Unit,
        onTokenReceived: (OAuthTokenResult) -> Unit,
        onError: (String) -> Unit
    ) {
        openaiPollingJob?.cancel()
        val generation = flowGeneration
        openaiPollingJob = managerScope.launch {
            try {
                val issuer = OPENAI_ISSUER
                val apiBase = "$issuer/api/accounts"
                val reqBody = """{"client_id":"$OPENAI_CLIENT_ID"}"""
                    .toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("$apiBase/deviceauth/usercode")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "application/json")
                    .post(reqBody)
                    .build()

                val (deviceAuthId, userCode, intervalSec) = client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        if (generation != flowGeneration) return@launch
                        onError(AppText.get(R.string.oauth_openai_code_request_failed, response.code, body))
                        return@launch
                    }
                    val root = json.parseToJsonElement(body).jsonObject
                    val authId = root["device_auth_id"]?.jsonPrimitive?.content.orEmpty()
                    val uCode = root["user_code"]?.jsonPrimitive?.content
                        ?: root["usercode"]?.jsonPrimitive?.content
                        ?: ""
                    val interval = root["interval"]?.jsonPrimitive?.content?.toLongOrNull()
                        ?: root["interval"]?.jsonPrimitive?.longOrNull
                        ?: 5L
                    Tuple3(authId, uCode, interval.coerceAtLeast(1L))
                }

                if (generation != flowGeneration) return@launch

                if (deviceAuthId.isBlank() || userCode.isBlank()) {
                    onError(AppText.get(R.string.oauth_openai_no_device_code))
                    return@launch
                }

                val verificationUrl = "$issuer/codex/device"
                withContext(Dispatchers.Main) {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(sensitiveClip("OpenAI Verification Code", userCode))
                    onCodeReceived(userCode, verificationUrl)
                }
                openExternalBrowser(verificationUrl)
                pollOpenAiDeviceToken(deviceAuthId, userCode, intervalSec, generation, onTokenReceived, onError)
            } catch (e: Exception) {
                if (generation != flowGeneration) return@launch
                onError(AppText.get(R.string.oauth_openai_request_error, e.localizedMessage ?: ""))
            }
        }
    }

    private suspend fun pollOpenAiDeviceToken(
        deviceAuthId: String,
        userCode: String,
        intervalSec: Long,
        generation: Int,
        onTokenReceived: (OAuthTokenResult) -> Unit,
        onError: (String) -> Unit
    ) {
        val issuer = OPENAI_ISSUER
        val pollUrl = "$issuer/api/accounts/deviceauth/token"
        val maxDurationMs = 15 * 60 * 1000L
        val startTime = System.currentTimeMillis()
        val interval = intervalSec.coerceAtLeast(1L)

        while (System.currentTimeMillis() - startTime < maxDurationMs) {
            delay(interval * 1000L)
            if (generation != flowGeneration) return

            val reqBody = buildJsonObject {
                put("device_auth_id", deviceAuthId)
                put("user_code", userCode)
            }.toString().toRequestBody("application/json".toMediaType())

            val tokenReq = Request.Builder()
                .url(pollUrl)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .post(reqBody)
                .build()

            try {
                client.newCall(tokenReq).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (generation != flowGeneration) return
                    if (response.isSuccessful) {
                        val root = json.parseToJsonElement(body).jsonObject
                        val authCode = root["authorization_code"]?.jsonPrimitive?.content.orEmpty()
                        val codeVerifier = root["code_verifier"]?.jsonPrimitive?.content.orEmpty()
                        if (authCode.isBlank() || codeVerifier.isBlank()) {
                            onError(AppText.get(R.string.oauth_openai_token_response_incomplete))
                            return
                        }
                        val exchanged = exchangeOpenAiAuthorizationCode(authCode, codeVerifier)
                        exchanged.onSuccess { result ->
                            withContext(Dispatchers.Main) {
                                // A reset (or a newer sign-in) while the exchange was in flight wins.
                                if (generation == flowGeneration) onTokenReceived(result)
                            }
                        }.onFailure { err ->
                            if (generation != flowGeneration) return
                            onError(err.message ?: AppText.get(R.string.oauth_openai_token_exchange_failed))
                        }
                        return
                    }
                    // Codex: 403/404 = still waiting for user to enter the code
                    if (response.code == 403 || response.code == 404) {
                        return@use
                    }
                    onError(AppText.get(R.string.oauth_openai_auth_failed, response.code, body))
                    return
                }
            } catch (_: Exception) {
                // Ignore transient network errors during polling
            }
        }
        if (generation != flowGeneration) return
        onError(AppText.get(R.string.oauth_openai_wait_timeout))
    }

    private suspend fun exchangeOpenAiAuthorizationCode(
        authorizationCode: String,
        codeVerifier: String
    ): Result<OAuthTokenResult> = withContext(Dispatchers.IO) {
        try {
            val redirectUri = "$OPENAI_ISSUER/deviceauth/callback"
            val formBody = FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", authorizationCode)
                .add("redirect_uri", redirectUri)
                .add("client_id", OPENAI_CLIENT_ID)
                .add("code_verifier", codeVerifier)
                .build()
            val request = Request.Builder()
                .url("$OPENAI_ISSUER/oauth/token")
                .addHeader("Accept", "application/json")
                .post(formBody)
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception(AppText.get(R.string.oauth_openai_token_exchange_failed_code, response.code, body)))
                }
                Result.success(parseOpenAiTokenResponse(body))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseOpenAiTokenResponse(body: String): OAuthTokenResult {
        val root = json.parseToJsonElement(body).jsonObject
        val accessToken = root["access_token"]?.jsonPrimitive?.content
            ?: throw Exception(AppText.get(R.string.oauth_missing_access_token))
        val refreshToken = root["refresh_token"]?.jsonPrimitive?.content
        val expiresIn = root["expires_in"]?.jsonPrimitive?.longOrNull ?: 3600L
        val idToken = root["id_token"]?.jsonPrimitive?.content
        val claims = parseChatgptIdTokenClaims(idToken)
        return OAuthTokenResult(
            provider = ProviderType.OPENAI,
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAt = System.currentTimeMillis() + (expiresIn * 1000L),
            chatgptAccountId = claims.accountId,
            accountEmail = claims.email
        )
    }

    suspend fun handleOAuthCallback(uri: Uri): Result<OAuthTokenResult> = withContext(Dispatchers.IO) {
        val code = uri.getQueryParameter("code")
        val state = uri.getQueryParameter("state")
        val error = uri.getQueryParameter("error")

        if (error != null) {
            val errorDesc = uri.getQueryParameter("error_description") ?: error
            return@withContext Result.failure(Exception(AppText.get(R.string.oauth_callback_denied, errorDesc)))
        }

        if (code == null || state == null) {
            return@withContext Result.failure(Exception(AppText.get(R.string.oauth_invalid_callback)))
        }

        val pending = pendingVerifications.remove(state)
            ?: return@withContext Result.failure(Exception(AppText.get(R.string.oauth_invalid_state)))

        val (provider, codeVerifier) = pending
        val tokenEndpoint = provider.oauthTokenEndpoint
            ?: return@withContext Result.failure(Exception(AppText.get(R.string.oauth_token_endpoint_missing)))

        try {
            val redirectUri = when (provider) {
                ProviderType.CLAUDE -> CLAUDE_REDIRECT_URI
                else -> REDIRECT_URI
            }

            val request = if (provider == ProviderType.CLAUDE) {
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
                    return@withContext Result.failure(Exception(AppText.get(R.string.oauth_token_exchange_failed_code, response.code, body)))
                }

                val root = json.parseToJsonElement(body).jsonObject
                val accessToken = root["access_token"]?.jsonPrimitive?.content
                    ?: return@withContext Result.failure(Exception(AppText.get(R.string.oauth_missing_access_token)))

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
            ?: return@withContext Result.failure(Exception(AppText.get(R.string.oauth_claude_no_session)))

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
                    return@withContext Result.failure(Exception(AppText.get(R.string.oauth_claude_token_exchange_failed, response.code, body)))
                }

                val root = json.parseToJsonElement(body).jsonObject
                val accessToken = root["access_token"]?.jsonPrimitive?.content
                    ?: return@withContext Result.failure(Exception(AppText.get(R.string.oauth_missing_access_token)))

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

    suspend fun refreshTokenIfNeeded(account: ProviderAccount, force: Boolean = false): ProviderAccount = withContext(Dispatchers.IO) {
        if ((!force && !account.isTokenExpired) || account.refreshToken == null) {
            return@withContext account
        }

        val tokenEndpoint = account.provider.oauthTokenEndpoint ?: return@withContext account

        try {
            // Claude + OpenAI ChatGPT refresh use JSON bodies (Codex: TokenEncoding::Json for refresh).
            val request = if (account.provider == ProviderType.CLAUDE || account.provider == ProviderType.OPENAI) {
                val clientId = getClientIdForProvider(account.provider)
                val jsonPayload =
                    """{"grant_type":"refresh_token","client_id":"$clientId","refresh_token":"${account.refreshToken}"}"""
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
                val idClaims = if (account.provider == ProviderType.OPENAI) {
                    parseChatgptIdTokenClaims(root["id_token"]?.jsonPrimitive?.content)
                } else {
                    ChatgptIdClaims(null, null)
                }

                account.copy(
                    accessToken = newAccessToken,
                    refreshToken = newRefreshToken,
                    tokenExpiresAt = System.currentTimeMillis() + (expiresIn * 1000L),
                    chatgptAccountId = idClaims.accountId ?: account.chatgptAccountId,
                    accountEmail = idClaims.email ?: account.accountEmail
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
            ProviderType.OPENAI -> OPENAI_CLIENT_ID
            else -> "oot-client"
        }
    }

    // Codex token_data.rs: claims under https://api.openai.com/auth + optional email/profile.
    private fun parseChatgptIdTokenClaims(jwt: String?): ChatgptIdClaims {
        if (jwt.isNullOrBlank()) return ChatgptIdClaims(null, null)
        return try {
            val parts = jwt.split('.')
            if (parts.size < 2) return ChatgptIdClaims(null, null)
            val padded = parts[1] + "=".repeat((4 - parts[1].length % 4) % 4)
            val payloadBytes = Base64.decode(padded, Base64.URL_SAFE)
            val payload = json.parseToJsonElement(String(payloadBytes, Charsets.UTF_8)).jsonObject
            val auth = payload["https://api.openai.com/auth"]?.jsonObject
            val profile = payload["https://api.openai.com/profile"]?.jsonObject
            val accountId = auth?.get("chatgpt_account_id")?.jsonPrimitive?.contentOrNull
            val email = payload["email"]?.jsonPrimitive?.contentOrNull
                ?: profile?.get("email")?.jsonPrimitive?.contentOrNull
            ChatgptIdClaims(accountId, email)
        } catch (_: Exception) {
            ChatgptIdClaims(null, null)
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

    private data class Tuple3<A, B, C>(val a: A, val b: B, val c: C)
    private data class Tuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
    private data class ChatgptIdClaims(val accountId: String?, val email: String?)

    companion object {
        const val REDIRECT_URI = "oot://oauth"
        const val CLAUDE_REDIRECT_URI = "https://console.anthropic.com/oauth/code/callback"
        // Official Client ID UUIDs
        const val CLAUDE_CLIENT_ID = "9d1c250a-e61b-44d9-88ed-5944d1962f5e"
        const val XAI_CLIENT_ID = "b1a00492-073a-47ea-816f-4c329264a828"

        // Codex: codex-rs/login/src/auth/manager.rs CLIENT_ID + server.rs DEFAULT_ISSUER
        const val OPENAI_CLIENT_ID = "app_EMoamEEZ73f0CkXaXp7hrann"
        const val OPENAI_ISSUER = "https://auth.openai.com"
    }
}

data class OAuthTokenResult(
    val provider: ProviderType,
    val accessToken: String,
    val refreshToken: String?,
    val expiresAt: Long,
    val chatgptAccountId: String? = null,
    val accountEmail: String? = null
)
