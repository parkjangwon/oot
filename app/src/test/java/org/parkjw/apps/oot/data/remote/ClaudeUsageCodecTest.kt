package org.parkjw.apps.oot.data.remote

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.parkjw.apps.oot.data.model.AuthType
import org.parkjw.apps.oot.data.model.ProviderAccount
import org.parkjw.apps.oot.data.model.ProviderType
import org.parkjw.apps.oot.data.model.TokenStatus
import org.parkjw.apps.oot.data.model.VendorError
import java.time.Instant


// Robolectric: error texts come from string resources (AppText), Korean by default.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ClaudeUsageCodecTest {

    private val json = Json { ignoreUnknownKeys = true }

    // -- status code -> VendorError mapping --

    @Test
    fun `403 maps to ScopeMissing`() {
        assertEquals(VendorError.ScopeMissing, mapClaudeHttpError(403))
    }

    @Test
    fun `429 maps to RateLimited`() {
        assertEquals(VendorError.RateLimited, mapClaudeHttpError(429))
    }

    @Test
    fun `other http codes map to Http`() {
        assertEquals(VendorError.Http(500), mapClaudeHttpError(500))
        assertEquals(VendorError.Http(503), mapClaudeHttpError(503))
        assertEquals(VendorError.Http(404), mapClaudeHttpError(404))
    }

    @Test
    fun `error texts for login-needed states`() {
        assertEquals("다시 로그인 필요", claudeErrorText(VendorError.Unauthorized))
        assertEquals("다시 로그인 필요(범위)", claudeErrorText(VendorError.ScopeMissing))
    }

    // -- usage JSON parsing into two windows --

    private val usageBody = """
        {
          "five_hour": {
            "utilization": 42.5,
            "resets_at": "2026-10-21T18:00:00Z"
          },
          "seven_day": {
            "utilization": 10.0,
            "resets_at": "2026-10-26T00:00:00Z"
          }
        }
    """.trimIndent()

    @Test
    fun `usage json parses into 5-hour and weekly windows`() {
        val windows = parseClaudeUsage(usageBody, json)!!
        assertEquals(2, windows.size)

        val fiveHour = windows.first { it.id == "five_hour" }
        assertEquals("5h", fiveHour.label)
        assertEquals(42.5, fiveHour.utilization, 0.001)
        assertEquals(Instant.parse("2026-10-21T18:00:00Z"), fiveHour.resetsAt)

        val weekly = windows.first { it.id == "seven_day" }
        assertEquals("7d", weekly.label)
        assertEquals(10.0, weekly.utilization, 0.001)
        assertEquals(Instant.parse("2026-10-26T00:00:00Z"), weekly.resetsAt)

        // remaining = 100 - utilization
        assertEquals(57.5, 100.0 - fiveHour.utilization, 0.001)
        assertEquals(90.0, 100.0 - weekly.utilization, 0.001)
    }

    @Test
    fun `usage json with missing fields fails to parse`() {
        assertNull(parseClaudeUsage("not json", json))
        assertNull(parseClaudeUsage("""{"five_hour": {}}""", json))
        assertNull(parseClaudeUsage("""{"five_hour": {"utilization": 1.0}}""", json))
    }

    @Test
    fun `resets_at without timezone still parses`() {
        val windows = parseClaudeUsage(
            """{"five_hour": {"utilization": 1.0, "resets_at": "2026-10-21T18:00:00+09:00"},
               "seven_day": {"utilization": 2.0, "resets_at": ""}}""",
            json
        )!!
        assertEquals(Instant.parse("2026-10-21T09:00:00Z"), windows[0].resetsAt)
        assertNull(windows[1].resetsAt)
    }

    // -- 429 keeps last value --

    private fun account() = ProviderAccount(
        id = "acc1",
        provider = ProviderType.CLAUDE,
        name = "Claude",
        authType = AuthType.OAUTH
    )

    private fun cachedStatus() = TokenStatus(
        accountId = "acc1",
        provider = ProviderType.CLAUDE,
        accountName = "Claude",
        remainingFormatted = "5시간: 80% 남음 | 주간: 90% 남음",
        statusSummary = "5h 80% | 주 90%",
        usagePercent = 20f
    )

    @Test
    fun `429 with cached value keeps it and marks stale`() {
        val status = claudeRateLimitedStatus(account(), cachedStatus())
        assertFalse(status.isError)
        assertTrue(status.isWarning)
        assertTrue(status.remainingFormatted.contains("이전 값"))
        assertTrue(status.remainingFormatted.contains("80% 남음"))
    }

    @Test
    fun `429 without cached value is an error and never falls back to 연동 완료`() {
        val status = claudeRateLimitedStatus(account(), null)
        assertTrue(status.isError)
        assertTrue(status.errorMessage!!.contains("429"))
        assertFalse(status.remainingFormatted.contains("연동 완료"))
    }

    // -- log summary never contains the token --

    @Test
    fun `log summary does not include token`() {
        val token = "sk-ant-oat01-SECRET-TOKEN-abc123"
        val windows = parseClaudeUsage(usageBody, json)!!
        val summary = claudeLogSummary("/api/oauth/usage", 200, windows)

        assertTrue(summary.contains("path=/api/oauth/usage"))
        assertTrue(summary.contains("http=200"))
        assertTrue(summary.contains("5h"))
        assertTrue(summary.contains("util=42.5%"))
        assertTrue(summary.contains("resets_at=2026-10-21T18:00:00Z"))
        assertFalse(summary.contains(token))
        assertFalse(summary.contains("Bearer"))
        assertFalse(summary.contains("Authorization"))
    }
}
