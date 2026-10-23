package org.parkjw.apps.oot.data.remote

import android.app.Application
import android.app.Notification
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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.parkjw.apps.oot.data.local.AccountPreferences
import org.parkjw.apps.oot.data.local.testAccountPreferences
import org.parkjw.apps.oot.data.model.AuthType
import org.parkjw.apps.oot.data.model.ProviderAccount
import org.parkjw.apps.oot.data.model.ProviderType
import org.parkjw.apps.oot.data.oauth.OAuthManager
import org.parkjw.apps.oot.notification.TestNotificationStrings
import org.parkjw.apps.oot.service.TokenNotificationManager
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

// A secure-storage reset must leave no trace of the removed accounts in the refresh state.
// No network: every request is answered by a local fake interceptor.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TokenSyncResetTest {

    private val context: Application = ApplicationProvider.getApplicationContext()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun xaiAccount() = ProviderAccount(
        id = "xai",
        provider = ProviderType.XAI,
        name = "xai",
        authType = AuthType.API_KEY,
        apiKey = "sk-test-000",
    )

    private fun client(interceptor: (okhttp3.Interceptor.Chain) -> Response) = OkHttpClient.Builder()
        .addInterceptor { chain -> interceptor(chain) }
        .build()

    private fun fakeResponse(
        request: okhttp3.Request,
        code: Int,
        body: String,
    ): Response = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("fake")
        .body(body.toResponseBody("application/json".toMediaType()))
        .build()

    private fun repository(prefs: AccountPreferences, client: OkHttpClient) =
        TokenSyncRepository(prefs, OAuthManager(context, client, json), client, json)

    @Test
    fun `accounts reset clears cached usages and the notification`() {
        val prefs = testAccountPreferences(context).also { it.saveAccount(xaiAccount()) }
        // Fake billing body: the weekly window resets far in the future.
        val client = client { chain ->
            fakeResponse(
                chain.request(),
                200,
                """{"config":{"currentPeriod":{"end":"2099-10-28T00:00:00Z"},"creditUsagePercent":1}}""",
            )
        }
        val repo = repository(prefs, client)

        runBlocking { repo.refreshAll() }
        assertTrue(repo.vendorUsages.value.isNotEmpty())

        repo.onAccountsReset()

        assertTrue(repo.statuses.value.isEmpty())
        assertTrue(repo.vendorUsages.value.isEmpty())
        val notification = TokenNotificationManager(context).buildNotification(repo.vendorUsages.value)
        assertEquals(
            TestNotificationStrings.KO.emptyNoProviders,
            notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString(),
        )
    }

    @Test
    fun `a reset during a token refresh sends no usage request with the old account`() {
        val prefs = testAccountPreferences(context).also {
            it.saveAccount(
                ProviderAccount(
                    id = "claude",
                    provider = ProviderType.CLAUDE,
                    name = "claude",
                    authType = AuthType.OAUTH,
                    accessToken = "at-old",
                    refreshToken = "rt-old",
                    tokenExpiresAt = 1L,
                ),
            )
        }
        val entered = CountDownLatch(1)
        val gate = CountDownLatch(1)
        val usageRequests = AtomicInteger()
        val client = client { chain ->
            val request = chain.request()
            if (request.url.encodedPath.endsWith("/v1/oauth/token")) {
                entered.countDown()
                assertTrue(gate.await(10, TimeUnit.SECONDS))
                fakeResponse(request, 200, """{"access_token":"at-new","refresh_token":"rt-new","expires_in":3600}""")
            } else {
                usageRequests.incrementAndGet()
                fakeResponse(request, 503, "{}")
            }
        }
        val repo = repository(prefs, client)

        val refresh = thread { runBlocking { repo.refreshAll() } }
        assertTrue(entered.await(10, TimeUnit.SECONDS))
        prefs.resetSecureStorage()
        repo.onAccountsReset()
        gate.countDown()
        refresh.join(10_000)

        assertFalse(refresh.isAlive)
        assertEquals(0, usageRequests.get())
        assertTrue(repo.vendorUsages.value.isEmpty())
    }

    @Test
    fun `a refresh that read accounts before a reset sends no usage request for a fresh token`() {
        val prefs = testAccountPreferences(context).also { it.saveAccount(xaiAccount()) }
        val entered = CountDownLatch(1)
        val gate = CountDownLatch(1)
        val armed = AtomicBoolean(false)
        // The scheduler is consulted after the accounts were read and before the fetch: park there.
        val scheduler = RefreshScheduler(clock = {
            if (armed.get()) {
                entered.countDown()
                assertTrue(gate.await(10, TimeUnit.SECONDS))
            }
            Long.MAX_VALUE
        })
        scheduler.onRateLimited("xai", 0L, null)
        armed.set(true)
        val usageRequests = AtomicInteger()
        val client = client { chain ->
            usageRequests.incrementAndGet()
            fakeResponse(chain.request(), 503, "{}")
        }
        val repo = TokenSyncRepository(prefs, OAuthManager(context, client, json), client, json, scheduler)

        val refresh = thread { runBlocking { repo.refreshAll() } }
        assertTrue(entered.await(10, TimeUnit.SECONDS))
        prefs.resetSecureStorage()
        repo.onAccountsReset()
        gate.countDown()
        refresh.join(10_000)

        assertFalse(refresh.isAlive)
        assertEquals(0, usageRequests.get())
        assertTrue(repo.vendorUsages.value.isEmpty())
    }

    @Test
    fun `a refresh started between the repository reset and the storage reset sends no usage request`() {
        val prefs = testAccountPreferences(context).also {
            it.saveAccount(
                ProviderAccount(
                    id = "claude",
                    provider = ProviderType.CLAUDE,
                    name = "claude",
                    authType = AuthType.OAUTH,
                    accessToken = "at-old",
                    refreshToken = "rt-old",
                    tokenExpiresAt = 1L,
                ),
            )
        }
        val entered = CountDownLatch(1)
        val gate = CountDownLatch(1)
        val usageRequests = AtomicInteger()
        val client = client { chain ->
            val request = chain.request()
            if (request.url.encodedPath.endsWith("/v1/oauth/token")) {
                entered.countDown()
                assertTrue(gate.await(10, TimeUnit.SECONDS))
                fakeResponse(request, 200, """{"access_token":"at-new","refresh_token":"rt-new","expires_in":3600}""")
            } else {
                usageRequests.incrementAndGet()
                fakeResponse(request, 503, "{}")
            }
        }
        val repo = repository(prefs, client)

        repo.onAccountsReset()
        val refresh = thread { runBlocking { repo.refreshAll() } }
        assertTrue(entered.await(10, TimeUnit.SECONDS))
        prefs.resetSecureStorage()
        repo.onAccountsReset()
        gate.countDown()
        refresh.join(10_000)

        assertFalse(refresh.isAlive)
        assertEquals(0, usageRequests.get())
        assertTrue(repo.vendorUsages.value.isEmpty())
    }

    @Test
    fun `a reset clears rate-limit backoff`() {
        val prefs = testAccountPreferences(context).also { it.saveAccount(xaiAccount()) }
        val requests = AtomicInteger()
        val client = client { chain ->
            // xAI falls back to /models on a 429; that call is the one the scheduler backs off from.
            if (chain.request().url.encodedPath.endsWith("/models")) requests.incrementAndGet()
            fakeResponse(chain.request(), 429, "{}")
        }
        val repo = repository(prefs, client)

        runBlocking { repo.refreshAll() }
        assertEquals(1, requests.get())

        repo.onAccountsReset()
        runBlocking { repo.refreshAll() }

        // Without the cleared backoff the scheduler would answer from the cache and send nothing.
        assertEquals(2, requests.get())
    }
}
