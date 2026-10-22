package org.parkjw.apps.oot.data.remote

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.parkjw.apps.oot.data.model.AuthType
import org.parkjw.apps.oot.data.model.ProviderAccount
import org.parkjw.apps.oot.data.model.ProviderType
import org.parkjw.apps.oot.data.model.Remaining
import org.parkjw.apps.oot.data.model.VendorError
import java.time.Instant


// Robolectric: error texts come from string resources (AppText), Korean by default.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OpenAiUsageCodecTest {

    private val json = Json { ignoreUnknownKeys = true }

    // FAKE fixture shaped like Codex RateLimitStatusPayload + RateLimitWindowSnapshot
    private val whamBody = """
        {
          "plan_type": "plus",
          "rate_limit": {
            "allowed": true,
            "limit_reached": false,
            "primary_window": {
              "used_percent": 38,
              "limit_window_seconds": 18000,
              "reset_after_seconds": 7200,
              "reset_at": 1761055200
            },
            "secondary_window": {
              "used_percent": 12,
              "limit_window_seconds": 604800,
              "reset_after_seconds": 259200,
              "reset_at": 1761314400
            }
          }
        }
    """.trimIndent()

    @Test
    fun `401 maps to Unauthorized`() {
        assertEquals(VendorError.Unauthorized, mapOpenAiHttpError(401))
    }

    @Test
    fun `wham usage json maps primary to 5h and secondary to weekly`() {
        val windows = parseOpenAiWhamUsage(whamBody, json)!!
        assertEquals(2, windows.size)

        val fiveHour = windows.first { it.id == "five_hour" }
        assertEquals("5h", fiveHour.label)
        assertEquals(38.0, fiveHour.usedPercent, 0.001)
        assertEquals(Instant.ofEpochSecond(1761055200L), fiveHour.resetsAt)
        assertEquals(62.0, 100.0 - fiveHour.usedPercent, 0.001)

        val weekly = windows.first { it.id == "seven_day" }
        assertEquals("7d", weekly.label)
        assertEquals(12.0, weekly.usedPercent, 0.001)
        assertEquals(Instant.ofEpochSecond(1761314400L), weekly.resetsAt)
        assertEquals(88.0, 100.0 - weekly.usedPercent, 0.001)
    }

    @Test
    fun `malformed body returns null`() {
        assertNull(parseOpenAiWhamUsage("{not-json", json))
        assertNull(parseOpenAiWhamUsage("""{"plan_type":"plus"}""", json))
        assertNull(parseOpenAiWhamUsage("""{"rate_limit":{}}""", json))
    }

    @Test
    fun `oauth success status builds TokenStatus windows`() {
        val account = ProviderAccount(
            id = "acct-fake",
            provider = ProviderType.OPENAI,
            name = "OpenAI (ChatGPT)",
            authType = AuthType.OAUTH,
            accessToken = "sk-test-000",
            chatgptAccountId = "acct_fake_000"
        )
        val windows = parseOpenAiWhamUsage(whamBody, json)!!
        val status = openAiOAuthSuccessStatus(account, windows)
        assertEquals(2, status.windows.size)
        assertEquals("five_hour", status.windows[0].id)
        assertEquals("seven_day", status.windows[1].id)
        assertEquals(Remaining.Percent(62.0), status.windows[0].remaining)
        assertEquals(Remaining.Percent(88.0), status.windows[1].remaining)
        assertTrue(status.statusSummary.contains("62"))
        assertTrue(!status.isError)
    }
}
