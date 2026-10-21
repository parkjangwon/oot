package org.parkjw.apps.oot.data.remote

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ZaiUsageCodecTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun body(vararg limits: String, level: String = "pro") =
        """{"code": 200, "success": true, "data": {"level": "$level", "limits": [${limits.joinToString(",")}]}}"""

    private val fiveHour = """{"unit": 3, "percentage": 34, "nextResetTime": 1792000000000}"""
    private val weekly = """{"unit": 6, "percentage": 10, "nextResetTime": 1792400000000}"""
    private val otherUnit = """{"unit": 5, "percentage": 1, "nextResetTime": 1793000000000}"""

    @Test
    fun `both limits give 5-hour then weekly with remaining and reset`() {
        val usage = parseZaiUsage(body(weekly, otherUnit, fiveHour), json)

        assertEquals("pro", usage.level)
        assertEquals(listOf("five_hour", "seven_day"), usage.windows.map { it.id })
        assertEquals(listOf("5시간", "주간"), usage.windows.map { it.label })
        val (five, week) = usage.windows
        assertEquals(66.0, five.percentLeft, 0.001)
        assertEquals(Instant.ofEpochMilli(1792000000000), five.resetsAt)
        assertEquals(90.0, week.percentLeft, 0.001)
        assertEquals(Instant.ofEpochMilli(1792400000000), week.resetsAt)
    }

    @Test
    fun `only a 5-hour limit gives a single window`() {
        val usage = parseZaiUsage(body(fiveHour, otherUnit), json)
        assertEquals(listOf("five_hour"), usage.windows.map { it.id })
    }

    @Test
    fun `only a weekly limit is not shown as 5-hour`() {
        val usage = parseZaiUsage(body(weekly), json)
        assertEquals(listOf("seven_day"), usage.windows.map { it.id })
        assertEquals(90.0, usage.windows.single().percentLeft, 0.001)
    }

    @Test
    fun `limits of other units give no windows`() {
        assertTrue(parseZaiUsage(body(otherUnit), json).windows.isEmpty())
        assertTrue(parseZaiUsage(body("""{"percentage": 5}"""), json).windows.isEmpty())
    }

    @Test
    fun `limits at the root without a data wrapper still parse`() {
        val usage = parseZaiUsage("""{"limits": [$fiveHour]}""", json)
        assertEquals(listOf("five_hour"), usage.windows.map { it.id })
        assertEquals("GLM", usage.level)
    }

    @Test
    fun `fractional percentage keeps its decimals`() {
        val usage = parseZaiUsage(body("""{"unit": 3, "percentage": 12.5}"""), json)
        assertEquals(87.5, usage.windows.single().percentLeft, 0.001)
    }

    @Test
    fun `percentage outside 0 to 100 is clamped`() {
        val usage = parseZaiUsage(
            body("""{"unit": 3, "percentage": 130}""", """{"unit": 6, "percentage": -4}"""),
            json
        )
        assertEquals(0.0, usage.windows[0].percentLeft, 0.001)
        assertEquals(100.0, usage.windows[1].percentLeft, 0.001)
    }

    @Test
    fun `missing reset time gives no reset and expireTime is the fallback`() {
        val usage = parseZaiUsage(
            body("""{"unit": 3, "percentage": 34}""", """{"unit": 6, "percentage": 10, "expireTime": 1792400000000}"""),
            json
        )
        assertNull(usage.windows[0].resetsAt)
        assertEquals(Instant.ofEpochMilli(1792400000000), usage.windows[1].resetsAt)
    }

    @Test
    fun `a limit without a numeric percentage is dropped instead of invented`() {
        val noPercent5h = """{"unit": 3, "nextResetTime": 1792000000000}"""
        val badPercentWeekly = """{"unit": 6, "percentage": "n/a"}"""
        assertEquals(listOf("seven_day"), parseZaiUsage(body(noPercent5h, weekly), json).windows.map { it.id })
        assertEquals(listOf("five_hour"), parseZaiUsage(body(fiveHour, badPercentWeekly), json).windows.map { it.id })
        assertTrue(parseZaiUsage(body(noPercent5h, badPercentWeekly), json).windows.isEmpty())
    }

    @Test
    fun `every window is complete`() {
        parseZaiUsage(body(fiveHour, weekly), json).windows.forEach { w ->
            assertTrue(w.id.isNotBlank() && w.label.isNotBlank())
            assertTrue(w.percentLeft in 0.0..100.0)
            assertTrue(w.resetsAt != null)
        }
    }

    @Test
    fun `unexpected bodies give no windows and never throw`() {
        listOf(
            "", "not json", "[]", "null", "{}", """{"data": []}""", """{"data": {"limits": "x"}}""",
            """{"data": {"limits": []}}""", """{"data": {"limits": [1, "a", null, [3]]}}""",
            """{"data": {"limits": [{"unit": {"a": 1}, "percentage": {}}]}}"""
        ).forEach { raw ->
            assertTrue(raw, parseZaiUsage(raw, json).windows.isEmpty())
        }
    }
}
