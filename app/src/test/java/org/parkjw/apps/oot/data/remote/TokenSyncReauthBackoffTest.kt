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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.parkjw.apps.oot.data.local.testAccountPreferences
import org.parkjw.apps.oot.data.model.AuthType
import org.parkjw.apps.oot.data.model.ProviderAccount
import org.parkjw.apps.oot.data.model.ProviderType
import org.parkjw.apps.oot.data.oauth.OAuthManager
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

// #161: When an account is in 429 backoff, re-authentication or manual refresh must clear backoff and make a fresh call.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TokenSyncReauthBackoffTest {

    private val context: Application = ApplicationProvider.getApplicationContext()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val account = ProviderAccount(
        id = "claude-test",
        provider = ProviderType.CLAUDE,
        name = "Claude",
        authType = AuthType.OAUTH,
        accessToken = "sk-ant-oat01-initial",
        refreshToken = "ref-1",
    )

    private val usageResponse = """
        {
          "five_hour": {
            "utilization": 20.0,
            "resets_at": "2099-01-01T00:00:00Z"
          }
        }
    """.trimIndent()

    @Test
    fun `clearAccountBackoff allows refreshAll to fetch freshly after 429`() {
        var callCount = 0
        var return429 = true

        val client = OkHttpClient.Builder().addInterceptor { chain ->
            callCount++
            if (return429) {
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(429)
                    .message("Rate Limited")
                    .header("Retry-After", "3600")
                    .body("""{"error":"rate_limited"}""".toResponseBody("application/json".toMediaType()))
                    .build()
            } else {
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(usageResponse.toResponseBody("application/json".toMediaType()))
                    .build()
            }
        }.build()

        val prefs = testAccountPreferences(context).also { it.saveAccount(account) }
        val scheduler = RefreshScheduler({ 1_000_000L })
        val repository = TokenSyncRepository(prefs, OAuthManager(context, client, json), client, json, scheduler)

        // 1st fetch: returns 429
        val first = runBlocking { repository.refreshAll() }.single()
        assertEquals(1, callCount)
        assertTrue(first.isRateLimited)

        // 2nd fetch without clearing backoff: skipped by scheduler, network call NOT made
        val second = runBlocking { repository.refreshAll() }.single()
        assertEquals(1, callCount) // call count unchanged
        assertTrue(second.isRateLimited)

        // Now token updated & backoff cleared (like in onSaveTokens / onSaveApiKey)
        return429 = false
        repository.clearAccountBackoff(account.id)

        // 3rd fetch: backoff cleared, makes fresh network call and succeeds with 200
        val third = runBlocking { repository.refreshAll() }.single()
        assertEquals(2, callCount)
        assertFalse(third.isRateLimited)
        assertFalse(third.isError)
        assertEquals(20.0f, third.usagePercent)
    }

    @Test
    fun `refreshAll with force true clears all backoffs and bypasses scheduler`() {
        var callCount = 0
        var return429 = true

        val client = OkHttpClient.Builder().addInterceptor { chain ->
            callCount++
            if (return429) {
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(429)
                    .message("Rate Limited")
                    .header("Retry-After", "3600")
                    .body("""{"error":"rate_limited"}""".toResponseBody("application/json".toMediaType()))
                    .build()
            } else {
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(usageResponse.toResponseBody("application/json".toMediaType()))
                    .build()
            }
        }.build()

        val prefs = testAccountPreferences(context).also { it.saveAccount(account) }
        val scheduler = RefreshScheduler({ 1_000_000L })
        val repository = TokenSyncRepository(prefs, OAuthManager(context, client, json), client, json, scheduler)

        // 1st fetch: 429
        runBlocking { repository.refreshAll() }
        assertEquals(1, callCount)

        // Normal refreshAll: blocked by scheduler
        runBlocking { repository.refreshAll() }
        assertEquals(1, callCount)

        // Force refreshAll (manual swipe refresh): clears backoff and makes call
        return429 = false
        val result = runBlocking { repository.refreshAll(force = true) }.single()
        assertEquals(2, callCount)
        assertFalse(result.isRateLimited)
        assertFalse(result.isError)
    }
}
