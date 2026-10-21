package org.parkjw.apps.oot.data.remote

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.parkjw.apps.oot.data.model.AuthType
import org.parkjw.apps.oot.data.model.ProviderAccount
import org.parkjw.apps.oot.data.model.ProviderType
import org.parkjw.apps.oot.data.model.Remaining
import org.parkjw.apps.oot.data.model.TokenStatus
import org.parkjw.apps.oot.data.oauth.OAuthManager
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

// A fake Google (no network): routes by URL, records every call. All ids, tokens and numbers are FAKE.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AntigravityApiTest {

    private class Call(val url: String, val authorization: String?, val userAgent: String?, val body: String)

    private val context: Application = ApplicationProvider.getApplicationContext()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val calls = CopyOnWriteArrayList<Call>()
    private val refreshedAccounts = CopyOnWriteArrayList<ProviderAccount>()
    private var route: (Call) -> Pair<Int, String> = { 404 to "{}" }

    private val loadUrl = "https://cloudcode-pa.googleapis.com/v1internal:loadCodeAssist"
    private val summaryUrl = "https://daily-cloudcode-pa.googleapis.com/v1internal:retrieveUserQuotaSummary"
    private val modelsUrl = "https://cloudcode-pa.googleapis.com/v1internal:fetchAvailableModels"
    private val tokenUrl = "https://oauth2.googleapis.com/token"

    private val assistBody = """{"cloudaicompanionProject": "demo-project-000", "currentTier": {"id": "free-tier"}}"""
    private val summaryBody = """
        {"response": {"groups": [
          {"displayName": "Gemini Models", "buckets": [
            {"bucketId": "gemini-5h", "remaining": {"remainingFraction": 0.62}, "resetTime": "2026-10-21T14:00:00Z"}]},
          {"displayName": "Claude and GPT models", "buckets": [
            {"bucketId": "3p-5h", "remaining": {"remainingFraction": 0.4}, "resetTime": "2026-10-21T15:30:00Z"}]}
        ]}}
    """.trimIndent()
    private val modelsBody = """
        {"models": {
          "gemini-3-pro-high": {"quotaInfo": {"remainingFraction": 0.62, "resetTime": "2026-10-21T14:00:00Z"}},
          "claude-sonnet-4-5": {"quotaInfo": {"remainingFraction": 0.4, "resetTime": "2026-10-21T15:30:00Z"}}
        }}
    """.trimIndent()

    private val client = OkHttpClient.Builder().addInterceptor { chain ->
        val request = chain.request()
        val call = Call(
            url = request.url.toString(),
            authorization = request.header("Authorization"),
            userAgent = request.header("User-Agent"),
            body = request.body?.let { Buffer().also { buffer -> it.writeTo(buffer) }.readUtf8() }.orEmpty()
        )
        calls.add(call)
        val (code, body) = route(call)
        Response.Builder()
            .request(request).protocol(Protocol.HTTP_1_1).code(code).message("fake")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
    }.build()

    private fun api(secret: String = "secret-test-000") =
        AntigravityApi(client, json, OAuthManager(context, client, json, antigravityClientSecret = secret)) {
            refreshedAccounts += it
        }

    private val account = ProviderAccount(
        id = "agy-1", provider = ProviderType.ANTIGRAVITY, name = "demo", authType = AuthType.OAUTH,
        accessToken = "at-old", refreshToken = "rt-test-000", tokenExpiresAt = Long.MAX_VALUE
    )

    private fun fetch(account: ProviderAccount = this.account, secret: String = "secret-test-000"): TokenStatus =
        runBlocking { api(secret).fetchStatus(account) }

    private fun percents(status: TokenStatus) = status.windows.map { (it.remaining as Remaining.Percent).percentLeft }

    @Test
    fun `reads the quota summary for the project with the documented headers and bodies`() {
        route = { call ->
            when (call.url) {
                loadUrl -> 200 to assistBody
                summaryUrl -> 200 to summaryBody
                else -> 404 to "{}"
            }
        }

        val status = fetch()

        assertFalse(status.isError)
        assertEquals(listOf("google", "third_party"), status.windows.map { it.id })
        assertEquals(listOf(62.0, 40.0), percents(status).map { Math.round(it).toDouble() })
        assertEquals(listOf(loadUrl, summaryUrl), calls.map { it.url })
        assertEquals(
            """{"metadata":{"ideType":"ANTIGRAVITY","platform":"PLATFORM_UNSPECIFIED","pluginType":"GEMINI"}}""",
            calls[0].body
        )
        assertEquals("""{"project":"demo-project-000"}""", calls[1].body)
        calls.forEach {
            assertEquals("Bearer at-old", it.authorization)
            assertEquals("antigravity/hub/2.9.1 linux/arm64", it.userAgent)
        }
    }

    @Test
    fun `falls back to the model list when the summary is unavailable`() {
        route = { call ->
            when (call.url) {
                loadUrl -> 200 to assistBody
                summaryUrl -> 404 to "{}"
                modelsUrl -> 200 to modelsBody
                else -> 404 to "{}"
            }
        }

        val status = fetch()

        assertFalse(status.isError)
        assertEquals(listOf("google", "third_party"), status.windows.map { it.id })
        assertEquals(listOf(loadUrl, summaryUrl, modelsUrl), calls.map { it.url })
        assertEquals("""{"project":"demo-project-000"}""", calls[2].body)
    }

    @Test
    fun `without a project the quota calls send an empty body`() {
        route = { call ->
            when (call.url) {
                loadUrl -> 200 to """{"currentTier": {"id": "free-tier"}}"""
                summaryUrl -> 200 to summaryBody
                else -> 404 to "{}"
            }
        }

        assertFalse(fetch().isError)
        assertEquals("{}", calls[1].body)
    }

    @Test
    fun `401 refreshes the token once and retries with the new one`() {
        route = { call ->
            when {
                call.url == tokenUrl -> 200 to """{"access_token": "at-new", "expires_in": 3600}"""
                call.authorization == "Bearer at-old" -> 401 to """{"error": {"status": "UNAUTHENTICATED"}}"""
                call.url == loadUrl -> 200 to assistBody
                call.url == summaryUrl -> 200 to summaryBody
                else -> 404 to "{}"
            }
        }

        val status = fetch()

        assertFalse(status.isError)
        assertEquals("at-new", refreshedAccounts.single().accessToken)
        assertEquals(listOf(loadUrl, tokenUrl, loadUrl, summaryUrl), calls.map { it.url })
        assertEquals("Bearer at-new", calls.last().authorization)
    }

    @Test
    fun `401 without a refresh token asks to sign in again`() {
        route = { 401 to "{}" }

        val status = fetch(account.copy(refreshToken = null))

        assertTrue(status.isError)
        assertEquals("다시 로그인 필요", status.errorMessage)
        assertEquals(listOf(loadUrl), calls.map { it.url })
    }

    @Test
    fun `401 on a build without the client secret says the build is not configured`() {
        route = { 401 to "{}" }

        val status = fetch(secret = "")

        assertEquals("이 빌드에는 Antigravity 로그인 설정이 없어요", status.errorMessage)
        assertEquals(listOf(loadUrl), calls.map { it.url })
    }

    @Test
    fun `a client Google refused for consumer sign-in asks to sign in again without refreshing`() {
        route = { call ->
            if (call.url == loadUrl) 200 to """{"ineligibleTiers": [{"reasonCode": "GOOGLE_TOS_NOT_SUPPORTED_BY_CLIENT"}]}"""
            else 404 to "{}"
        }

        val status = fetch()

        assertEquals("다시 로그인 필요(범위)", status.errorMessage)
        assertEquals(listOf(loadUrl), calls.map { it.url })
    }

    @Test
    fun `a Gemini API key cannot read the quota`() {
        val keyAccount = account.copy(authType = AuthType.API_KEY, apiKey = "AIza-test-000", accessToken = null)

        val status = fetch(keyAccount)

        assertTrue(status.isError)
        assertTrue(status.errorMessage!!.startsWith("Google 로그인 필요"))
        assertTrue(calls.isEmpty())
    }

    @Test
    fun `no measurable quota anywhere is an error`() {
        route = { call ->
            when (call.url) {
                loadUrl -> 200 to assistBody
                summaryUrl -> 200 to """{"groups": []}"""
                modelsUrl -> 200 to """{"models": {}}"""
                else -> 404 to "{}"
            }
        }

        assertEquals("한도 정보를 찾지 못했어요", fetch().errorMessage)
    }

    @Test
    fun `a network failure is reported as such`() {
        route = { throw IOException("offline") }

        assertEquals("네트워크 오류", fetch().errorMessage)
    }
}
