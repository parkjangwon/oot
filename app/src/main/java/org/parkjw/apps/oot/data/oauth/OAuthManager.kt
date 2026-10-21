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
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
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
    private var loopbackJob: Job? = null

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
            ProviderType.ANTIGRAVITY -> {
                uriBuilder.appendQueryParameter("redirect_uri", ANTIGRAVITY_REDIRECT_URI)
                uriBuilder.appendQueryParameter("access_type", "offline")
                uriBuilder.appendQueryParameter("prompt", "consent")
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
                                // Continue waiting
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
                        onError("OpenAI Device 인증 코드 요청 실패 (${response.code}): $body")
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

                if (deviceAuthId.isBlank() || userCode.isBlank()) {
                    onError("유효한 OpenAI 디바이스 코드를 받지 못했습니다.")
                    return@launch
                }

                val verificationUrl = "$issuer/codex/device"
                withContext(Dispatchers.Main) {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("OpenAI Verification Code", userCode))
                    onCodeReceived(userCode, verificationUrl)
                }
                openExternalBrowser(verificationUrl)
                pollOpenAiDeviceToken(deviceAuthId, userCode, intervalSec, onTokenReceived, onError)
            } catch (e: Exception) {
                onError("OpenAI Device 인증 요청 중 오류: ${e.localizedMessage}")
            }
        }
    }

    private suspend fun pollOpenAiDeviceToken(
        deviceAuthId: String,
        userCode: String,
        intervalSec: Long,
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
                    if (response.isSuccessful) {
                        val root = json.parseToJsonElement(body).jsonObject
                        val authCode = root["authorization_code"]?.jsonPrimitive?.content.orEmpty()
                        val codeVerifier = root["code_verifier"]?.jsonPrimitive?.content.orEmpty()
                        if (authCode.isBlank() || codeVerifier.isBlank()) {
                            onError("OpenAI deviceauth/token 응답이 불완전합니다.")
                            return
                        }
                        val exchanged = exchangeOpenAiAuthorizationCode(authCode, codeVerifier)
                        exchanged.onSuccess { result ->
                            withContext(Dispatchers.Main) { onTokenReceived(result) }
                        }.onFailure { err ->
                            onError(err.message ?: "OpenAI 토큰 교환 실패")
                        }
                        return
                    }
                    // Codex: 403/404 = still waiting for user to enter the code
                    if (response.code == 403 || response.code == 404) {
                        return@use
                    }
                    onError("OpenAI Device 인증 실패 (${response.code}): $body")
                    return
                }
            } catch (_: Exception) {
                // Ignore transient network errors during polling
            }
        }
        onError("OpenAI 인증 대기 시간이 초과되었습니다 (15분).")
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
                    return@withContext Result.failure(Exception("OpenAI 토큰 교환 실패 (${response.code}): $body"))
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
            ?: throw Exception("응답에 access_token이 없습니다.")
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

    /**
     * Google Antigravity OAuth 2.0 PKCE with local loopback listener
     */
    fun startAntigravityOAuthFlow(
        onTokenReceived: (OAuthTokenResult) -> Unit,
        onError: (String) -> Unit
    ) {
        val state = UUID.randomUUID().toString()
        val codeVerifier = generateCodeVerifier()
        val codeChallenge = generateCodeChallenge(codeVerifier)
        pendingVerifications[state] = Pair(ProviderType.ANTIGRAVITY, codeVerifier)

        startLoopbackListener(4000, state, codeVerifier, onTokenReceived, onError)

        val uri = Uri.parse("https://accounts.google.com/o/oauth2/auth").buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", ANTIGRAVITY_CLIENT_ID)
            .appendQueryParameter("redirect_uri", ANTIGRAVITY_REDIRECT_URI)
            .appendQueryParameter("scope", ProviderType.ANTIGRAVITY.defaultScopes.joinToString(" "))
            .appendQueryParameter("code_challenge", codeChallenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("state", state)
            .appendQueryParameter("access_type", "offline")
            .appendQueryParameter("prompt", "consent")
            .build()

        openExternalBrowser(uri.toString())
    }

    private fun startLoopbackListener(
        port: Int,
        expectedState: String,
        codeVerifier: String,
        onTokenReceived: (OAuthTokenResult) -> Unit,
        onError: (String) -> Unit
    ) {
        loopbackJob?.cancel()
        loopbackJob = managerScope.launch(Dispatchers.IO) {
            var serverSocket: ServerSocket? = null
            try {
                serverSocket = ServerSocket(port)
                serverSocket.soTimeout = 180_000 // 3 minutes timeout
                val socket = serverSocket.accept()
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val firstLine = reader.readLine().orEmpty()

                val uriString = firstLine.split(" ").getOrNull(1).orEmpty()
                val fullUri = Uri.parse("http://localhost$uriString")
                val code = fullUri.getQueryParameter("code")

                val htmlResponse = """HTTP/1.1 200 OK
Content-Type: text/html; charset=utf-8
Connection: close

<!DOCTYPE html>
<html>
<head><meta charset="utf-8"><title>Antigravity 인증 완료</title>
<style>body{font-family:sans-serif;text-align:center;padding:50px;background:#121212;color:#fff;}h1{color:#8ab4f8;}</style>
</head>
<body>
<h1>Antigravity 연동 완료!</h1>
<p>Google 인증이 성공했습니다. OOT 앱으로 돌아가주세요.</p>
</body>
</html>"""
                socket.getOutputStream().write(htmlResponse.toByteArray(Charsets.UTF_8))
                socket.getOutputStream().flush()
                socket.close()

                if (code.isNullOrBlank()) {
                    withContext(Dispatchers.Main) { onError("Google 인증 코드를 받지 못했습니다.") }
                    return@launch
                }

                val tokenResult = exchangeGoogleCode(code, codeVerifier)
                tokenResult.onSuccess { res ->
                    withContext(Dispatchers.Main) { onTokenReceived(res) }
                }.onFailure { err ->
                    withContext(Dispatchers.Main) { onError(err.message ?: "Google 토큰 교환 실패") }
                }
            } catch (e: Exception) {
                // Timeout or socket closed
            } finally {
                try { serverSocket?.close() } catch (ignored: Exception) {}
            }
        }
    }

    suspend fun exchangeAntigravityCode(rawInput: String): Result<OAuthTokenResult> = withContext(Dispatchers.IO) {
        val trimmed = rawInput.trim()
        val code = if (trimmed.contains("code=")) {
            Uri.parse(trimmed).getQueryParameter("code") ?: trimmed
        } else {
            trimmed
        }

        val key = pendingVerifications.keys.lastOrNull { pendingVerifications[it]?.first == ProviderType.ANTIGRAVITY }
        val entry = key?.let { pendingVerifications.remove(it) }
        val codeVerifier = entry?.second ?: generateCodeVerifier()

        exchangeGoogleCode(code, codeVerifier)
    }

    private suspend fun exchangeGoogleCode(code: String, codeVerifier: String): Result<OAuthTokenResult> = withContext(Dispatchers.IO) {
        try {
            val formBody = FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("client_id", ANTIGRAVITY_CLIENT_ID)
                .add("code", code)
                .add("redirect_uri", ANTIGRAVITY_REDIRECT_URI)
                .add("code_verifier", codeVerifier)
                .build()

            val request = Request.Builder()
                .url("https://oauth2.googleapis.com/token")
                .addHeader("Accept", "application/json")
                .post(formBody)
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Google 토큰 교환 실패 (${response.code}): $body"))
                }

                val root = json.parseToJsonElement(body).jsonObject
                val accessToken = root["access_token"]?.jsonPrimitive?.content
                    ?: return@withContext Result.failure(Exception("응답에 access_token이 없습니다."))
                val refreshToken = root["refresh_token"]?.jsonPrimitive?.content
                val expiresIn = root["expires_in"]?.jsonPrimitive?.longOrNull ?: 3600L

                Result.success(
                    OAuthTokenResult(
                        provider = ProviderType.ANTIGRAVITY,
                        accessToken = accessToken,
                        refreshToken = refreshToken,
                        expiresAt = System.currentTimeMillis() + (expiresIn * 1000L)
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
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
            val redirectUri = when (provider) {
                ProviderType.CLAUDE -> CLAUDE_REDIRECT_URI
                ProviderType.ANTIGRAVITY -> ANTIGRAVITY_REDIRECT_URI
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
            ProviderType.ANTIGRAVITY -> ANTIGRAVITY_CLIENT_ID
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
        const val ANTIGRAVITY_REDIRECT_URI = "http://localhost:4000"

        // Official Client ID UUIDs
        const val CLAUDE_CLIENT_ID = "9d1c250a-e61b-44d9-88ed-5944d1962f5e"
        const val XAI_CLIENT_ID = "b1a00492-073a-47ea-816f-4c329264a828"
        const val ANTIGRAVITY_CLIENT_ID = "1071006060591-tmhssin2h21lcre235vtolojh4g403ep.apps.googleusercontent.com"

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
