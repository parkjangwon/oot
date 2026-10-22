package org.parkjw.apps.oot.data.remote

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.parkjw.apps.oot.R
import org.parkjw.apps.oot.data.model.AuthType
import org.parkjw.apps.oot.data.model.ProviderAccount
import org.parkjw.apps.oot.data.model.ProviderType
import org.parkjw.apps.oot.i18n.AppText
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Fake interceptor: a request reaching it means the endpoint policy did not stop it. Keys are FAKE.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CustomEndpointPolicyApiTest {

    private var requestCount = 0

    private val client = OkHttpClient.Builder().addInterceptor { chain ->
        requestCount++
        Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(500)
            .message("fake")
            .body("{}".toResponseBody("application/json".toMediaType()))
            .build()
    }.build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun account(customEndpoint: String?) = ProviderAccount(
        id = "ds-endpoint",
        provider = ProviderType.DEEPSEEK,
        name = "demo-ds-endpoint",
        authType = AuthType.API_KEY,
        apiKey = "key-fake-000",
        customEndpoint = customEndpoint
    )

    @Test
    fun `http custom endpoint makes no request and returns the endpoint error`() = runBlocking {
        val status = DeepSeekApi(client, json).fetchStatus(account("http://api.example.com"))

        assertEquals(0, requestCount)
        assertTrue(status.isError)
        assertEquals(AppText.get(R.string.error_custom_endpoint_not_https), status.errorMessage)
    }

    @Test
    fun `blank custom endpoint still uses the provider default`() = runBlocking {
        DeepSeekApi(client, json).fetchStatus(account(null))

        assertEquals(1, requestCount)
    }
}
