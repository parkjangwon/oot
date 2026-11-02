package org.parkjw.apps.oot.data.remote

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class MiniMaxUsageCodecTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val now = Instant.parse("2026-11-02T00:00:00Z")

    private fun model(
        name: String = "MiniMax-M*",
        intervalTotal: Int? = 1500,
        intervalCount: Int? = 228,
        intervalPercent: Double? = null,
        weeklyTotal: Int? = 0,
        weeklyCount: Int? = 0,
        weeklyPercent: Double? = null,
        intervalStatus: Int? = null,
        weeklyStatus: Int? = null,
        boostPermille: Int? = null,
        endTime: Long? = 1792000000000,
        remainsTime: Long? = 7_151_954,
        weeklyEndTime: Long? = 1792400000000,
        weeklyRemainsTime: Long? = 248_351_954,
    ): String {
        val fields = buildList {
            add(""""model_name":"$name"""")
            intervalTotal?.let { add(""""current_interval_total_count":$it""") }
            intervalCount?.let { add(""""current_interval_usage_count":$it""") }
            intervalPercent?.let { add(""""current_interval_remaining_percent":$it""") }
            intervalStatus?.let { add(""""current_interval_status":$it""") }
            weeklyTotal?.let { add(""""current_weekly_total_count":$it""") }
            weeklyCount?.let { add(""""current_weekly_usage_count":$it""") }
            weeklyPercent?.let { add(""""current_weekly_remaining_percent":$it""") }
            weeklyStatus?.let { add(""""current_weekly_status":$it""") }
            boostPermille?.let { add(""""weekly_boost_permille":$it""") }
            endTime?.let { add(""""end_time":$it""") }
            remainsTime?.let { add(""""remains_time":$it""") }
            weeklyEndTime?.let { add(""""weekly_end_time":$it""") }
            weeklyRemainsTime?.let { add(""""weekly_remains_time":$it""") }
        }
        return "{${fields.joinToString(",")}}"
    }

    private fun body(
        vararg models: String,
        statusCode: Int = 0,
        statusMsg: String = "success",
    ) = """{"base_resp":{"status_code":$statusCode,"status_msg":"$statusMsg"},"model_remains":[${models.joinToString(",")}]}"""

    private fun success(raw: String): MiniMaxUsageResult.Success =
        parseMiniMaxUsage(raw, json, now) as MiniMaxUsageResult.Success

    @Test
    fun `official fixture keeps the count as remaining and leaves an unreported weekly window out`() {
        val fixture = """
            {
              "base_resp": {"status_code": 0, "status_msg": "success"},
              "model_remains": [
                {
                  "model_name": "MiniMax-M*",
                  "start_time": 1776355200000,
                  "end_time": 1776373200000,
                  "remains_time": 7151954,
                  "current_interval_total_count": 1500,
                  "current_interval_usage_count": 228,
                  "current_weekly_total_count": 0,
                  "current_weekly_usage_count": 0,
                  "weekly_start_time": 1776009600000,
                  "weekly_end_time": 1776614400000,
                  "weekly_remains_time": 248351954
                }
              ]
            }
        """.trimIndent()

        val windows = success(fixture).windows

        assertEquals(listOf("five_hour"), windows.map { it.id })
        assertEquals("5h", windows.single().label)
        assertEquals(15.2, windows.single().percentLeft, 0.001)
        assertEquals(Instant.ofEpochMilli(1776373200000), windows.single().resetsAt)
    }

    @Test
    fun `a reported weekly window gives 5-hour then weekly`() {
        val windows = success(
            body(model(weeklyTotal = 1000, weeklyCount = 250))
        ).windows

        assertEquals(listOf("five_hour", "seven_day"), windows.map { it.id })
        assertEquals(listOf("5h", "7d"), windows.map { it.label })
        assertEquals(25.0, windows[1].percentLeft, 0.001)
        assertEquals(Instant.ofEpochMilli(1792400000000), windows[1].resetsAt)
    }

    @Test
    fun `without a percentage the count is legacy remaining`() {
        val windows = success(body(model(intervalTotal = 100, intervalCount = 30))).windows
        assertEquals(30.0, windows.single().percentLeft, 0.001)
    }

    @Test
    fun `a zero remaining count is zero percent left not unknown`() {
        val windows = success(body(model(intervalTotal = 100, intervalCount = 0))).windows
        assertEquals(0.0, windows.single().percentLeft, 0.001)
    }

    @Test
    fun `the percentage picks the used interpretation when it matches`() {
        val windows = success(
            body(model(intervalTotal = 100, intervalCount = 30, intervalPercent = 70.0))
        ).windows
        assertEquals(70.0, windows.single().percentLeft, 0.001)
    }

    @Test
    fun `the percentage keeps the remaining interpretation when it matches`() {
        val windows = success(
            body(model(intervalTotal = 100, intervalCount = 30, intervalPercent = 30.0))
        ).windows
        assertEquals(30.0, windows.single().percentLeft, 0.001)
    }

    @Test
    fun `a percentage that matches neither interpretation is unknown`() {
        assertTrue(
            success(
                body(model(intervalTotal = 100, intervalCount = 30, intervalPercent = 55.0))
            ).windows.isEmpty()
        )
    }

    @Test
    fun `unlimited weekly is shown as fully available`() {
        val windows = success(
            body(model(weeklyTotal = 0, weeklyCount = 0, weeklyStatus = 3))
        ).windows

        assertEquals(listOf("five_hour", "seven_day"), windows.map { it.id })
        assertEquals(100.0, windows[1].percentLeft, 0.001)
        assertEquals(Instant.ofEpochMilli(1792400000000), windows[1].resetsAt)
    }

    @Test
    fun `a model outside the plan gives no windows`() {
        val windows = success(
            body(
                model(
                    intervalTotal = 0,
                    intervalCount = 0,
                    intervalStatus = 3,
                    weeklyTotal = 0,
                    weeklyCount = 0,
                    weeklyStatus = 3,
                )
            )
        ).windows
        assertTrue(windows.isEmpty())
    }

    @Test
    fun `the text bucket wins over another available bucket`() {
        val windows = success(
            body(
                model(name = "speech-hd", intervalTotal = 100, intervalCount = 20),
                model(name = "MiniMax-M*", intervalTotal = 1500, intervalCount = 228),
            )
        ).windows
        assertEquals(15.2, windows.single().percentLeft, 0.001)
    }

    @Test
    fun `an out-of-plan text bucket falls through to the first available bucket`() {
        val windows = success(
            body(
                model(
                    name = "MiniMax-M*",
                    intervalTotal = 0,
                    intervalCount = 0,
                    intervalStatus = 3,
                    weeklyTotal = 0,
                    weeklyCount = 0,
                    weeklyStatus = 3,
                ),
                model(name = "speech-hd", intervalTotal = 100, intervalCount = 20),
            )
        ).windows
        assertEquals(20.0, windows.single().percentLeft, 0.001)
    }

    @Test
    fun `missing or empty model_remains gives no windows`() {
        assertTrue(success("""{"base_resp":{"status_code":0,"status_msg":"success"}}""").windows.isEmpty())
        assertTrue(success("""{"base_resp":{"status_code":0},"model_remains":[]}""").windows.isEmpty())
    }

    @Test
    fun `missing count fields give no windows instead of invented percentages`() {
        val windows = success(
            body(
                model(
                    intervalTotal = null,
                    intervalCount = null,
                    weeklyTotal = null,
                    weeklyCount = null,
                )
            )
        ).windows
        assertTrue(windows.isEmpty())
    }

    @Test
    fun `zero and negative totals are unknown`() {
        val windows = success(
            body(
                model(
                    intervalTotal = 0,
                    intervalCount = 0,
                    weeklyTotal = 100,
                    weeklyCount = 50,
                )
            )
        ).windows
        assertEquals(listOf("seven_day"), windows.map { it.id })
        assertEquals(50.0, windows.single().percentLeft, 0.001)

        assertTrue(success(body(model(intervalTotal = -5, intervalCount = 0))).windows.isEmpty())
    }

    @Test
    fun `a count above the total is unknown`() {
        assertTrue(success(body(model(intervalTotal = 100, intervalCount = 150))).windows.isEmpty())
    }

    @Test
    fun `a non-zero base_resp is an API error with its message`() {
        val result = parseMiniMaxUsage(
            body(model(), statusCode = 1004, statusMsg = "authentication failed"),
            json,
            now,
        )
        assertEquals(MiniMaxUsageResult.ApiError(1004, "authentication failed"), result)
    }

    @Test
    fun `empty and invalid bodies are invalid responses and never throw`() {
        listOf("", "not json", "[]", "null", "123", "\"text\"").forEach { raw ->
            assertEquals(raw, MiniMaxUsageResult.InvalidResponse, parseMiniMaxUsage(raw, json, now))
        }
    }

    @Test
    fun `a missing end time falls back to now plus remains`() {
        val windows = success(
            body(model(intervalTotal = 100, intervalCount = 30, endTime = null, remainsTime = 3_600_000))
        ).windows
        assertEquals(now.plusSeconds(3600), windows.single().resetsAt)
    }

    @Test
    fun `the weekly boost scales the derived weekly percentage`() {
        val windows = success(
            body(model(weeklyTotal = 100, weeklyCount = 50, boostPermille = 1500))
        ).windows
        assertEquals(75.0, windows[1].percentLeft, 0.001)
    }

    @Test
    fun `every returned window is complete and clamped`() {
        success(body(model(weeklyTotal = 1000, weeklyCount = 250))).windows.forEach { w ->
            assertTrue(w.id.isNotBlank() && w.label.isNotBlank())
            assertTrue(w.percentLeft in 0.0..100.0)
            assertTrue(w.resetsAt != null)
        }
    }
}
