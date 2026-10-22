package org.parkjw.apps.oot.data.oauth

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.parkjw.apps.oot.data.model.AuthType
import org.parkjw.apps.oot.data.model.ProviderAccount
import org.parkjw.apps.oot.data.model.ProviderType
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ConnectException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList

// Real loopback sockets, a fake Google token endpoint (no network), FAKE secrets and codes.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OAuthManagerAntigravityTest {

    private val context: Application = ApplicationProvider.getApplicationContext()
    private val json = Json { ignoreUnknownKeys = true }
    private val port = OAuthManager.ANTIGRAVITY_LOOPBACK_PORT

    // Every request the fake token endpoint received: url to form fields.
    private val requests = CopyOnWriteArrayList<Pair<String, Map<String, String>>>()
    private val tokens = CopyOnWriteArrayList<OAuthTokenResult>()
    private val errors = CopyOnWriteArrayList<String>()

    private fun fakeClient(code: Int = 200, body: String = """{"access_token":"at-new","refresh_token":"rt-new","expires_in":3600}"""): OkHttpClient =
        OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val form = request.body as FormBody
            requests.add(request.url.toString() to (0 until form.size).associate { form.name(it) to form.value(it) })
            Response.Builder()
                .request(request).protocol(Protocol.HTTP_1_1).code(code).message("fake")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }.build()

    private val clientId = "client-id-test-000.apps.example.invalid"

    private fun manager(secret: String = "secret-test-000", id: String = clientId, client: OkHttpClient = fakeClient()) =
        OAuthManager(context, client, json, antigravityClientSecret = secret, antigravityClientId = id)

    private fun startLogin(manager: OAuthManager): android.net.Uri {
        manager.startAntigravityOAuthFlow({ tokens += it }, { errors += it })
        val started = checkNotNull(shadowOf(context).nextStartedActivity) { "browser not opened, errors=$errors" }
        return started.data!!
    }

    // A listener still closing from one test must not take the port away from the next.
    @After
    fun waitForPortReleased() {
        waitFor { portIsFree() }
    }

    private fun portIsFree(): Boolean =
        try { ServerSocket(port, 1, InetAddress.getLoopbackAddress()).use { true } } catch (e: java.io.IOException) { false }

    // First line of the loopback listener's answer to a plain GET, as the status code.
    private fun get(path: String): Int =
        Socket(InetAddress.getLoopbackAddress(), port).use { socket ->
            socket.soTimeout = 5_000
            socket.getOutputStream().apply {
                write("GET $path HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n".toByteArray())
                flush()
            }
            BufferedReader(InputStreamReader(socket.getInputStream())).readLine().split(' ')[1].toInt()
        }

    private fun waitFor(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!condition() && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
    }

    private val account = ProviderAccount(
        id = "agy-1", provider = ProviderType.ANTIGRAVITY, name = "demo", authType = AuthType.OAUTH,
        accessToken = "at-old", refreshToken = "rt-test-000", tokenExpiresAt = 0L
    )

    // -- configuration gate --

    @Test
    fun `without a client secret sign-in stops before the browser with a clear message`() {
        startLoginExpectingError(manager(secret = ""))

        assertEquals(listOf("이 빌드에는 Antigravity 로그인 설정이 없어요"), errors.toList())
        assertNull(shadowOf(context).nextStartedActivity)
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `without a client id sign-in stops before the browser with the same message`() {
        val manager = manager(id = "")
        assertFalse(manager.isAntigravityConfigured)
        startLoginExpectingError(manager)

        assertEquals(listOf("이 빌드에는 Antigravity 로그인 설정이 없어요"), errors.toList())
        assertNull(shadowOf(context).nextStartedActivity)
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `without a client id a pasted code and a refresh never reach Google`() {
        val manager = manager(id = "  ")

        assertTrue(runBlocking { manager.exchangeAntigravityCode("4/0Abc") }.isFailure)
        assertSame(account, runBlocking { manager.refreshTokenIfNeeded(account) })
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `the injected client id is what Google sees and only both values together configure login`() {
        assertTrue(manager().isAntigravityConfigured)
        assertFalse(manager(secret = "").isAntigravityConfigured)
        assertEquals(clientId, manager().getClientIdForProvider(ProviderType.ANTIGRAVITY))
    }

    @Test
    fun `sign-in asks for cloud-platform, userinfo email and openid`() {
        assumeTrue(portIsFree())
        val authUri = startLogin(manager())
        val scopes = authUri.getQueryParameter("scope")!!.split(" ")
        assertTrue(scopes.containsAll(listOf(
            "https://www.googleapis.com/auth/cloud-platform",
            "https://www.googleapis.com/auth/userinfo.email",
            "openid",
        )))

        // end the login so the port is released for the next test
        assertEquals(200, get("/?state=${authUri.getQueryParameter("state")}&error=access_denied"))
        waitFor { errors.isNotEmpty() }
    }

    private fun startLoginExpectingError(manager: OAuthManager) =
        manager.startAntigravityOAuthFlow({ tokens += it }, { errors += it })

    @Test
    fun `without a client secret a pasted code and a refresh never reach Google`() {
        val manager = manager(secret = "")

        val exchange = runBlocking { manager.exchangeAntigravityCode("4/0Abc") }
        assertTrue(exchange.isFailure)
        assertSame(account, runBlocking { manager.refreshTokenIfNeeded(account) })
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `a pasted code without a login session asks to sign in again`() {
        val result = runBlocking { manager().exchangeAntigravityCode("4/0Abc") }

        assertTrue(result.exceptionOrNull()!!.message!!.contains("다시 로그인"))
        assertTrue(requests.isEmpty())
    }

    // -- refresh --

    @Test
    fun `refresh sends the client secret with the client id`() {
        val refreshed = runBlocking { manager().refreshTokenIfNeeded(account) }

        assertEquals("at-new", refreshed.accessToken)
        val (url, form) = requests.single()
        assertEquals("https://oauth2.googleapis.com/token", url)
        assertEquals(
            mapOf(
                "grant_type" to "refresh_token",
                "client_id" to clientId,
                "refresh_token" to "rt-test-000",
                "client_secret" to "secret-test-000"
            ),
            form
        )
    }

    // -- loopback login --

    @Test
    fun `login exchanges the code with the secret, the same redirect and the PKCE verifier`() {
        assumeTrue(portIsFree())
        val authUri = startLogin(manager())
        val state = authUri.getQueryParameter("state")!!

        assertEquals("S256", authUri.getQueryParameter("code_challenge_method"))
        assertEquals(clientId, authUri.getQueryParameter("client_id"))
        assertEquals("http://127.0.0.1:$port", authUri.getQueryParameter("redirect_uri"))

        // A stale tab and a probe get an answer but do not end the login.
        assertEquals(400, get("/?state=stale&code=evil"))
        assertEquals(400, get("/favicon.ico"))
        assertEquals(200, get("/?state=$state&code=code-test-000&scope=email"))
        waitFor { tokens.isNotEmpty() || errors.isNotEmpty() }

        assertEquals(emptyList<String>(), errors.toList())
        assertEquals("at-new", tokens.single().accessToken)
        val form = requests.single().second
        assertEquals("authorization_code", form["grant_type"])
        assertEquals("code-test-000", form["code"])
        assertEquals("secret-test-000", form["client_secret"])
        assertEquals(clientId, form["client_id"])
        assertEquals(authUri.getQueryParameter("redirect_uri"), form["redirect_uri"])
        val digest = MessageDigest.getInstance("SHA-256").digest(form["code_verifier"]!!.toByteArray(Charsets.US_ASCII))
        assertEquals(
            authUri.getQueryParameter("code_challenge"),
            Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
        )
    }

    @Test
    fun `a second login attempt takes over the port and the first state is dead`() {
        assumeTrue(portIsFree())
        val manager = manager()
        val firstState = startLogin(manager).getQueryParameter("state")!!
        val secondState = startLogin(manager).getQueryParameter("state")!!

        assertEquals(400, get("/?state=$firstState&code=old"))
        assertEquals(200, get("/?state=$secondState&code=new"))
        waitFor { tokens.isNotEmpty() || errors.isNotEmpty() }

        assertEquals(emptyList<String>(), errors.toList())
        assertEquals("new", requests.single().second["code"])
    }

    @Test
    fun `declined consent is reported and frees the port`() {
        assumeTrue(portIsFree())
        val state = startLogin(manager()).getQueryParameter("state")!!

        assertEquals(200, get("/?state=$state&error=access_denied"))
        waitFor { errors.isNotEmpty() }

        assertEquals(listOf("Google 로그인이 취소되었어요."), errors.toList())
        assertTrue(requests.isEmpty())
        waitFor { portIsFree() }
        assertTrue(portIsFree())
    }

    @Test
    fun `a rejected exchange shows a short message and never the raw body`() {
        assumeTrue(portIsFree())
        val rejecting = fakeClient(code = 400, body = """{"error":"invalid_grant","error_description":"Bad Request"}""")
        val state = startLogin(manager(client = rejecting)).getQueryParameter("state")!!

        get("/?state=$state&code=code-test-000")
        waitFor { errors.isNotEmpty() }

        assertEquals(listOf("인증 코드가 만료되었거나 이미 사용되었어요. 다시 로그인해 주세요."), errors.toList())
        assertTrue(tokens.isEmpty())
    }

    @Test
    fun `the login listener is reachable through loopback only`() {
        assumeTrue(portIsFree())
        val lanAddress = NetworkInterface.getNetworkInterfaces().toList()
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
        assumeTrue(lanAddress != null)
        val state = startLogin(manager()).getQueryParameter("state")!!

        assertThrows(ConnectException::class.java) { Socket(lanAddress, port).close() }

        // finish the login so the port is released
        assertEquals(200, get("/?state=$state&code=code-test-000"))
        waitFor { tokens.isNotEmpty() || errors.isNotEmpty() }
    }

    @Test
    fun `a busy port is reported and the browser is not opened`() {
        assumeTrue(portIsFree())
        ServerSocket(port, 1, InetAddress.getLoopbackAddress()).use {
            manager().startAntigravityOAuthFlow({ tokens += it }, { errors += it })
        }

        assertEquals(1, errors.size)
        assertTrue(errors.single().contains("포트"))
        assertNull(shadowOf(context).nextStartedActivity)
    }

    // -- pasted code --

    @Test
    fun `a pasted address from an older attempt is refused and the current one finishes the login`() {
        assumeTrue(portIsFree())
        val manager = manager()
        val state = startLogin(manager).getQueryParameter("state")!!

        val stale = runBlocking { manager.exchangeAntigravityCode("http://127.0.0.1:$port/?state=old&code=x") }
        assertTrue(stale.isFailure)
        assertTrue(requests.isEmpty())

        val result = runBlocking { manager.exchangeAntigravityCode("http://127.0.0.1:$port/?state=$state&code=code-test-000") }
        assertEquals("at-new", result.getOrThrow().accessToken)
        assertEquals("code-test-000", requests.single().second["code"])
        // the pasted login ends the loopback listener; the close lands on the listener's thread, so wait for it
        waitFor { portIsFree() }
        assertTrue(portIsFree())
        assertThrows(ConnectException::class.java) { get("/?state=$state&code=late") }
    }
}
