package org.parkjw.apps.oot.data.remote

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
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
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Fake responses via an interceptor (no network). All keys and values are FAKE.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OpenRouterApiTest {

    private val keyWithLimit = """
        {"data":{"label":"sk-or-v1-demo","limit":20.0,"limit_remaining":7.6,"usage":12.4}}
    """.trimIndent()

    private val keyWithoutLimit = """
        {"data":{"label":"sk-or-v1-demo","limit":null,"limit_remaining":null,"usage":3.2}}
    """.trimIndent()

    private val credits = """
        {"data":{"total_credits":50.0,"total_usage":37.6}}
    """.trimIndent()

    private val creditsForbidden = """
        {"error":{"code":403,"message":"Insufficient permissions to access this endpoint"}}
    """.trimIndent()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun client(keyBody: String, creditsCode: Int, creditsBody: String): OkHttpClient =
        OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val path = request.url.encodedPath
            val (code, body) = when {
                path.endsWith("/key") -> 200 to keyBody
                path.endsWith("/credits") -> creditsCode to creditsBody
                else -> throw AssertionError("Unexpected request: $path")
            }
            response(request, code, body)
        }.build()

    private fun response(request: Request, code: Int, body: String): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("fake")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()

    private fun account() = ProviderAccount(
        id = "openrouter",
        provider = ProviderType.OPENROUTER,
        name = "demo-openrouter",
        authType = AuthType.API_KEY,
        apiKey = "key-fake-000"
    )

    @Test
    fun `credits 403 shows the key limit instead of an error`() = runBlocking {
        val status = OpenRouterApi(client(keyWithLimit, 403, creditsForbidden), json)
            .fetchStatus(account())

        assertFalse(status.isError)
        assertTrue(status.remainingFormatted.contains("$7.60"))
        assertTrue(status.statusSummary.contains("$7.60"))
        assertEquals("$20.00", status.totalLimitFormatted)
        assertEquals(listOf("key"), status.windows.map { it.id })
    }

    @Test
    fun `credits and key both available keeps the smaller amount`() = runBlocking {
        val status = OpenRouterApi(client(keyWithLimit, 200, credits), json)
            .fetchStatus(account())

        assertFalse(status.isError)
        // account balance is 12.4, key remaining is 7.6 -> the key limit is shown
        assertTrue(status.remainingFormatted.contains("$7.60"))
        assertTrue(status.statusSummary.contains("$7.60"))
        assertEquals("$20.00", status.totalLimitFormatted)
        assertEquals(listOf("credit", "key"), status.windows.map { it.id })
    }

    @Test
    fun `credits 403 without a key limit keeps the error status`() = runBlocking {
        val status = OpenRouterApi(client(keyWithoutLimit, 403, creditsForbidden), json)
            .fetchStatus(account())

        assertTrue(status.isError)
        assertTrue(status.windows.isEmpty())
    }
}
