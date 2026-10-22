package org.parkjw.apps.oot.data.remote

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.parkjw.apps.oot.data.model.Remaining
import org.parkjw.apps.oot.data.model.UsageWindow
import java.time.Instant

class XaiBillingCodecTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val weeklyOnly = """
        {"config": {"currentPeriod": {"end": "2026-10-28T00:00:00Z"}, "creditUsagePercent": 0.25}}
    """.trimIndent()

    private fun windowsOf(body: String): List<UsageWindow> = parseXaiBilling(body, json)!!.toUsageWindows()

    // Every field the UI reads is present; only a missing period end may leave resetsAt null.
    private fun assertComplete(window: UsageWindow, expectReset: Boolean) {
        assertTrue(window.id.isNotBlank())
        assertTrue(window.label.isNotBlank())
        val remaining = window.remaining as Remaining.Percent
        assertTrue(remaining.percentLeft.isFinite() && remaining.percentLeft in 0.0..100.0)
        if (expectReset) assertNotNull(window.resetsAt) else assertNull(window.resetsAt)
    }

    @Test
    fun `weekly only gives one weekly window with remaining and reset`() {
        val windows = windowsOf(weeklyOnly)

        val weekly = windows.single()
        assertEquals("weekly", weekly.id)
        assertEquals("weekly", weekly.label)
        assertEquals(75.0, (weekly.remaining as Remaining.Percent).percentLeft, 0.001)
        assertEquals(Instant.parse("2026-10-28T00:00:00Z"), weekly.resetsAt)
        assertComplete(weekly, expectReset = true)
    }

    @Test
    fun `period end without a zone is UTC and fractions are kept only when zoned`() {
        mapOf(
            "2026-10-28T00:00:00Z" to "2026-10-28T00:00:00Z",
            "2026-10-28T00:00:00" to "2026-10-28T00:00:00Z",
            "2026-10-28T00:00:00.500" to "2026-10-28T00:00:00Z",
            "2026-10-28T00:00:00.500Z" to "2026-10-28T00:00:00.500Z",
            "2026-10-28T09:00:00+09:00" to "2026-10-28T00:00:00Z"
        ).forEach { (end, expected) ->
            val billing = parseXaiBilling("""{"config": {"currentPeriod": {"end": "$end"}}}""", json)!!
            assertEquals(end, Instant.parse(expected), billing.periodEnd)
        }
    }

    @Test
    fun `missing or unusable end keeps the window but has no reset`() {
        listOf(
            """{"config": {"creditUsagePercent": 0.5}}""",
            """{"config": {"currentPeriod": {}, "creditUsagePercent": 0.5}}""",
            """{"config": {"currentPeriod": {"end": ""}, "creditUsagePercent": 0.5}}""",
            """{"config": {"currentPeriod": {"end": "next week"}, "creditUsagePercent": 0.5}}""",
            """{"config": {"currentPeriod": {"end": null}, "creditUsagePercent": 0.5}}"""
        ).forEach { body ->
            val weekly = windowsOf(body).single()
            assertEquals(body, 50.0, (weekly.remaining as Remaining.Percent).percentLeft, 0.001)
            assertComplete(weekly, expectReset = false)
        }
    }

    @Test
    fun `usage right after a weekly reset may be omitted and means nothing used`() {
        val weekly = windowsOf("""{"config": {"currentPeriod": {"end": "2026-10-28T00:00:00Z"}}}""").single()
        assertEquals(100.0, (weekly.remaining as Remaining.Percent).percentLeft, 0.001)
        assertComplete(weekly, expectReset = true)
    }

    @Test
    fun `usage above 1 is already a percent and is clamped to 100`() {
        assertEquals(42.0, parseXaiBilling("""{"config": {"creditUsagePercent": 42}}""", json)!!.usedPercent, 0.001)
        assertEquals(100.0, parseXaiBilling("""{"config": {"creditUsagePercent": 250}}""", json)!!.usedPercent, 0.001)
        assertEquals(100.0, parseXaiBilling("""{"config": {"creditUsagePercent": -3}}""", json)!!.percentLeft, 0.001)
    }

    @Test
    fun `fields at the root without a config wrapper still parse`() {
        val billing = parseXaiBilling("""{"currentPeriod": {"end": "2026-10-28T00:00:00Z"}, "creditUsagePercent": 0.1}""", json)!!
        assertEquals(10.0, billing.usedPercent, 0.001)
        assertNotNull(billing.periodEnd)
    }

    @Test
    fun `product usage and prepaid balance are read`() {
        val billing = parseXaiBilling(
            """
            {"config": {"creditUsagePercent": 0.25,
              "productUsage": [{"product": "GrokBuild", "usagePercent": 0.5}, {"product": "GrokChat", "usagePercent": 12.5}, {"usagePercent": 1}, 7],
              "isUnifiedBillingUser": true, "prepaidBalance": {"val": 5.5}}}
            """.trimIndent(),
            json
        )!!
        assertEquals(listOf("Build" to 50.0, "Chat" to 12.5), billing.productUsage)
        assertEquals(5.5, billing.prepaidBalance!!, 0.001)
        // product usage has no reset timestamp, so it never becomes a window
        assertEquals(1, billing.toUsageWindows().size)
    }

    @Test
    fun `prepaid balance is ignored unless unified billing`() {
        val billing = parseXaiBilling(
            """{"config": {"creditUsagePercent": 0.25, "prepaidBalance": {"val": 5.5}}}""",
            json
        )!!
        assertNull(billing.prepaidBalance)
    }

    @Test
    fun `error and unexpected bodies give no billing and never throw`() {
        listOf(
            "", "not json", "[]", "null", "{}", """{"config": {}}""", """{"config": "x"}""",
            """{"error": "unauthorized"}""", """{"code": 16, "message": "unauthenticated"}""",
            """{"error": {"code": 401, "message": "bad key"}, "config": null}""",
            """{"config": {"productUsage": [{"product": "GrokChat", "usagePercent": 0.5}]}}""",
            """{"config": {"currentPeriod": [], "creditUsagePercent": "NaN"}}"""
        ).forEach { body ->
            assertNull(body, parseXaiBilling(body, json))
        }
    }
}
