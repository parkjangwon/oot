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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.parkjw.apps.oot.data.local.AccountPreferences
import org.parkjw.apps.oot.data.local.testAccountPreferences
import org.parkjw.apps.oot.data.model.AuthType
import org.parkjw.apps.oot.data.model.ProviderAccount
import org.parkjw.apps.oot.data.model.ProviderType
import org.parkjw.apps.oot.data.oauth.OAuthManager
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

// #157: A delayed periodic refresh that succeeds after re-authentication must not overwrite re-authenticated tokens.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TokenSyncLateRefreshTest {

    private val context: Application = ApplicationProvider.getApplicationContext()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

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
    fun `delayed periodic refresh completing after reauthentication is discarded and does not overwrite tokens`() {
        val initialAccount = ProviderAccount(
            id = "claude",
            provider = ProviderType.CLAUDE,
            name = "claude",
            authType = AuthType.OAUTH,
            accessToken = "at-initial",
            refreshToken = "rt-initial",
            tokenExpiresAt = 1L, // expired to force token refresh
        )
        val prefs = testAccountPreferences(context).also { it.saveAccount(initialAccount) }

        val entered = CountDownLatch(1)
        val gate = CountDownLatch(1)
        val usageRequests = AtomicInteger()

        val client = client { chain ->
            val request = chain.request()
            if (request.url.encodedPath.endsWith("/v1/oauth/token")) {
                entered.countDown()
                assertTrue(gate.await(10, TimeUnit.SECONDS))
                fakeResponse(request, 200, """{"access_token":"at-delayed","refresh_token":"rt-delayed","expires_in":3600}""")
            } else {
                usageRequests.incrementAndGet()
                fakeResponse(request, 200, """{"five_hour":{"utilization":0.1},"seven_day":{"utilization":0.2}}""")
            }
        }
        val repo = repository(prefs, client)

        // 1. Periodic refresh starts in background
        val refreshThread = thread { runBlocking { repo.refreshAll() } }
        assertTrue(entered.await(10, TimeUnit.SECONDS))

        // 2. While periodic refresh is in flight, user reauthenticates: bumps token generation and writes new tokens
        prefs.bumpTokenGeneration("claude")
        val reauthAccount = initialAccount.copy(
            accessToken = "at-reauth",
            refreshToken = "rt-reauth",
            tokenExpiresAt = System.currentTimeMillis() + 3600_000L,
        )
        assertTrue(prefs.updateAccountIfPresent(reauthAccount))

        // 3. Delayed periodic refresh completes now
        gate.countDown()
        refreshThread.join(10_000)

        assertFalse(refreshThread.isAlive)

        // 4. Stored account MUST retain re-authenticated tokens, NOT delayed refreshed tokens
        val currentAccount = prefs.accounts.value.first { it.id == "claude" }
        assertEquals("at-reauth", currentAccount.accessToken)
        assertEquals("rt-reauth", currentAccount.refreshToken)

        // 5. Usage request with old token was not sent
        assertEquals(0, usageRequests.get())
    }
}
