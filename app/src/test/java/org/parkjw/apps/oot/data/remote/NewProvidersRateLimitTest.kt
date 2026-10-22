package org.parkjw.apps.oot.data.remote

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.parkjw.apps.oot.data.model.AuthType
import org.parkjw.apps.oot.data.model.ProviderAccount
import org.parkjw.apps.oot.data.model.ProviderType
import org.parkjw.apps.oot.data.model.TokenStatus
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Fake 429 responses via an interceptor (no network). All keys and values are FAKE.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NewProvidersRateLimitTest {

    private var responseCode = 429
    private var retryAfter: String? = null

    private val client = OkHttpClient.Builder().addInterceptor { chain ->
        val request = chain.request()
        val builder = Response.Builder()
            .request(request).protocol(Protocol.HTTP_1_1).code(responseCode).message("fake")
            .body("{}".toResponseBody("application/json".toMediaType()))
        retryAfter?.let { builder.header("Retry-After", it) }
        builder.build()
    }.build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun account(id: String, provider: ProviderType) = ProviderAccount(
        id = id, provider = provider, name = "demo-$id",
        authType = AuthType.API_KEY, apiKey = "key-fake-$id"
    )

    private fun fetch(provider: ProviderType): TokenStatus = runBlocking {
        val account = account(provider.name.lowercase(), provider)
        when (provider) {
            ProviderType.DEEPSEEK -> DeepSeekApi(client, json).fetchStatus(account)
            ProviderType.OLLAMA_CLOUD -> OllamaCloudApi(client, json).fetchStatus(account)
            ProviderType.OPENCODE_GO -> OpenCodeGoApi(client, json).fetchStatus(account)
            else -> throw IllegalArgumentException("unexpected provider $provider")
        }
    }

    @Test
    fun `deepseek 429 with Retry-After is rate limited`() {
        responseCode = 429
        retryAfter = "120"

        val status = fetch(ProviderType.DEEPSEEK)

        assertTrue(status.isRateLimited)
        assertEquals(120_000L, status.rateLimitedRetryAfterMs)
    }

    @Test
    fun `deepseek 429 without Retry-After is rate limited with null delay`() {
        responseCode = 429
        retryAfter = null

        val status = fetch(ProviderType.DEEPSEEK)

        assertTrue(status.isRateLimited)
        assertNull(status.rateLimitedRetryAfterMs)
    }

    @Test
    fun `ollama cloud 429 with Retry-After is rate limited`() {
        responseCode = 429
        retryAfter = "120"

        val status = fetch(ProviderType.OLLAMA_CLOUD)

        assertTrue(status.isRateLimited)
        assertEquals(120_000L, status.rateLimitedRetryAfterMs)
    }

    @Test
    fun `ollama cloud 429 without Retry-After is rate limited with null delay`() {
        responseCode = 429
        retryAfter = null

        val status = fetch(ProviderType.OLLAMA_CLOUD)

        assertTrue(status.isRateLimited)
        assertNull(status.rateLimitedRetryAfterMs)
    }

    @Test
    fun `opencodego 429 with Retry-After is rate limited`() {
        responseCode = 429
        retryAfter = "120"

        val status = fetch(ProviderType.OPENCODE_GO)

        assertTrue(status.isRateLimited)
        assertEquals(120_000L, status.rateLimitedRetryAfterMs)
    }

    @Test
    fun `opencodego 429 without Retry-After is rate limited with null delay`() {
        responseCode = 429
        retryAfter = null

        val status = fetch(ProviderType.OPENCODE_GO)

        assertTrue(status.isRateLimited)
        assertNull(status.rateLimitedRetryAfterMs)
    }

    @Test
    fun `scheduler blocks shouldFetch until the status retry-after passes`() {
        var now = 1_000_000L
        val scheduler = RefreshScheduler(clock = { now })
        val key = "ds-fake"
        val status = run {
            responseCode = 429
            retryAfter = "120"
            fetch(ProviderType.DEEPSEEK)
        }

        scheduler.onRateLimited(key, 60_000L, status.rateLimitedRetryAfterMs)

        assertFalse(scheduler.shouldFetch(key))
        now += 119_999L
        assertFalse(scheduler.shouldFetch(key))
        now += 1L
        assertTrue(scheduler.shouldFetch(key))
    }
}
