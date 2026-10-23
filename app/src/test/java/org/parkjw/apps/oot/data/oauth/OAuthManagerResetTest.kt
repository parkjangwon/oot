package org.parkjw.apps.oot.data.oauth

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.parkjw.apps.oot.data.model.ProviderType
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// A secure-storage reset cancels pending sign-ins: their verifiers are gone and no code can be exchanged.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OAuthManagerResetTest {

    private val context: Application = ApplicationProvider.getApplicationContext()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // No network: every request is answered 503 with a fake body.
    private fun clientAnswering503() = OkHttpClient.Builder()
        .addInterceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(503)
                .message("fake")
                .body("{}".toResponseBody("application/json".toMediaType()))
                .build()
        }
        .build()

    @Test
    fun `cancelling pending flows drops the PKCE session and the code cannot be exchanged`() {
        val manager = OAuthManager(context, clientAnswering503(), json)

        manager.startOAuthFlow(ProviderType.CLAUDE)
        assertTrue(manager.hasPendingVerificationFlow())

        manager.cancelPendingFlows()
        assertFalse(manager.hasPendingVerificationFlow())

        val result = runBlocking { manager.exchangeClaudeAuthCode("fake-code") }
        assertTrue(result.isFailure)
    }
}
