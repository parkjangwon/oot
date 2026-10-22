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
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.parkjw.apps.oot.data.local.testAccountPreferences
import org.parkjw.apps.oot.data.model.AuthType
import org.parkjw.apps.oot.data.model.ProviderAccount
import org.parkjw.apps.oot.data.model.ProviderType
import org.parkjw.apps.oot.data.oauth.OAuthManager
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// #105: when the scheduler is in backoff, the previous values are shown again and must be marked stale.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TokenSyncStaleTest {

    private val context: Application = ApplicationProvider.getApplicationContext()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val account = ProviderAccount(
        id = "xai",
        provider = ProviderType.XAI,
        name = "xai",
        authType = AuthType.API_KEY,
        apiKey = "sk-test-000",
    )

    // Fake billing body: the weekly window resets far in the future, so it survives the stale rollover.
    private fun client() = OkHttpClient.Builder().addInterceptor { chain ->
        Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(
                """{"config":{"currentPeriod":{"end":"2099-10-28T00:00:00Z"},"creditUsagePercent":1}}"""
                    .toResponseBody("application/json".toMediaType()),
            )
            .build()
    }.build()

    @Test
    fun `backoff reuses the previous values marked stale with their original time`() {
        val prefs = testAccountPreferences(context).also { it.saveAccount(account) }
        val client = client()
        val scheduler = RefreshScheduler({ 1_000_000L })
        val repository = TokenSyncRepository(prefs, OAuthManager(context, client, json), client, json, scheduler)

        val first = runBlocking { repository.refreshAll() }.single()
        assertNull(first.staleSince)

        scheduler.onRateLimited(account.id, 60 * 60_000L, null)
        val second = runBlocking { repository.refreshAll() }.single()

        assertEquals(first.lastUpdated, second.lastUpdated)
        assertEquals(first.lastUpdated, second.staleSince)

        val usage = repository.vendorUsages.value.single()
        assertEquals(first.lastUpdated, usage.staleSince?.toEpochMilli())
    }
}
