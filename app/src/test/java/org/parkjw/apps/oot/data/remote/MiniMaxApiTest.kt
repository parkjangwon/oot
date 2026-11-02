package org.parkjw.apps.oot.data.remote

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.parkjw.apps.oot.data.model.AuthType
import org.parkjw.apps.oot.data.model.ProviderAccount
import org.parkjw.apps.oot.data.model.ProviderType
import org.parkjw.apps.oot.data.model.TokenStatus
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Fake responses through an interceptor (no network). All keys and values are FAKE.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MiniMaxApiTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val requests = mutableListOf<HttpUrl>()
    private var responder: (HttpUrl) -> Pair<Int, String> = { 200 to USAGE }

    private val client = OkHttpClient.Builder().addInterceptor { chain ->
        val request = chain.request()
        requests += request.url
        val (code, body) = responder(request.url)
        Response.Builder()
            .request(request).protocol(Protocol.HTTP_1_1).code(code).message("fake")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
    }.build()

    private fun account(customEndpoint: String? = null) = ProviderAccount(
        id = "minimax-fake",
        provider = ProviderType.MINIMAX,
        name = "demo-minimax",
        authType = AuthType.API_KEY,
        apiKey = TEST_KEY,
        customEndpoint = customEndpoint,
    )

    private fun fetch(account: ProviderAccount = account()): TokenStatus = runBlocking {
        MiniMaxApi(client, json).fetchStatus(account)
    }

    @Test
    fun `401 on the global region retries once against cn and remembers it`() {
        responder = { url ->
            if (url.host == "api.minimax.io") 401 to "{}" else 200 to USAGE
        }
        val api = MiniMaxApi(client, json)

        val first = runBlocking { api.fetchStatus(account()) }

        assertFalse(first.isError)
        assertEquals(listOf("api.minimax.io", "api.minimax.cn"), requests.map { it.host })
        assertEquals("/v1/token_plan/remains", requests[1].encodedPath)
        assertEquals(1, first.windows.size)

        requests.clear()
        val second = runBlocking { api.fetchStatus(account()) }

        assertFalse(second.isError)
        assertEquals(listOf("api.minimax.cn"), requests.map { it.host })
    }

    @Test
    fun `an auth-type base_resp error on the global region retries cn`() {
        responder = { url ->
            if (url.host == "api.minimax.io") {
                200 to """{"base_resp":{"status_code":1004,"status_msg":"invalid api key"}}"""
            } else {
                200 to USAGE
            }
        }

        val status = fetch()

        assertFalse(status.isError)
        assertEquals(listOf("api.minimax.io", "api.minimax.cn"), requests.map { it.host })
        assertEquals(1, status.windows.size)
        assertEquals(ProviderType.MINIMAX, status.provider)
    }

    @Test
    fun `both regions failing returns the normal auth error`() {
        responder = { 401 to "{}" }

        val status = fetch()

        assertTrue(status.isError)
        assertEquals(listOf("api.minimax.io", "api.minimax.cn"), requests.map { it.host })
        assertFalse(status.errorMessage.orEmpty().contains(TEST_KEY))
    }

    @Test
    fun `a non-auth base_resp error stops the region retry and redacts the key`() {
        responder = { 200 to """{"base_resp":{"status_code":1008,"status_msg":"insufficient balance for $TEST_KEY"}}""" }

        val status = fetch()

        assertTrue(status.isError)
        assertEquals(1, requests.size)
        assertFalse(status.errorMessage.orEmpty().contains(TEST_KEY))
        assertTrue(status.errorMessage.orEmpty().contains("insufficient balance"))
    }

    @Test
    fun `a custom endpoint disables the region fallback`() {
        responder = { 401 to "{}" }

        val status = fetch(account(customEndpoint = "https://proxy.example.com"))

        assertTrue(status.isError)
        assertEquals(listOf("proxy.example.com"), requests.map { it.host })
    }

    @Test
    fun `a non-https custom endpoint is refused without a request`() {
        val status = fetch(account(customEndpoint = "http://proxy.example.com"))

        assertTrue(status.isError)
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `429 is rate limited with the Retry-After delay`() {
        responder = { 429 to "{}" }
        val interceptorClient = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            Response.Builder()
                .request(request).protocol(Protocol.HTTP_1_1).code(429).message("fake")
                .header("Retry-After", "120")
                .body("{}".toResponseBody("application/json".toMediaType()))
                .build()
        }.build()

        val status = runBlocking { MiniMaxApi(interceptorClient, json).fetchStatus(account()) }

        assertTrue(status.isRateLimited)
        assertEquals(120_000L, status.rateLimitedRetryAfterMs)
    }

    private companion object {
        const val TEST_KEY = "sk-test-000"
        const val USAGE = """
            {
              "base_resp": {"status_code": 0, "status_msg": "success"},
              "model_remains": [
                {
                  "model_name": "MiniMax-M*",
                  "end_time": 1792000000000,
                  "remains_time": 7151954,
                  "current_interval_total_count": 1500,
                  "current_interval_usage_count": 228,
                  "current_weekly_total_count": 0,
                  "current_weekly_usage_count": 0,
                  "weekly_end_time": 1792400000000,
                  "weekly_remains_time": 248351954
                }
              ]
            }
        """
    }
}
