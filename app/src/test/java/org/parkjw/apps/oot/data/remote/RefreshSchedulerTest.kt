package org.parkjw.apps.oot.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class RefreshSchedulerTest {

    private var now = 1_000_000L
    private val clock = { now }
    private val oneMin = 60_000L
    private val sixtyMin = 60L * 60L * 1000L

    @Test
    fun `first 429 waits base interval`() {
        val s = RefreshScheduler(clock)
        s.onRateLimited("a", oneMin, null)
        assertFalse(s.shouldFetch("a"))
        now += oneMin - 1
        assertFalse(s.shouldFetch("a"))
        now += 1
        assertTrue(s.shouldFetch("a"))
    }

    @Test
    fun `subsequent 429 doubles until cap`() {
        val s = RefreshScheduler(clock)
        s.onRateLimited("a", oneMin, null) // 1 min
        now += oneMin
        s.onRateLimited("a", oneMin, null) // 2 min
        assertEquals(now + 2 * oneMin, s.notBeforeMs("a"))
        now += 2 * oneMin
        s.onRateLimited("a", oneMin, null) // 4 min
        assertEquals(now + 4 * oneMin, s.notBeforeMs("a"))
        repeat(20) {
            now = s.notBeforeMs("a")!!
            s.onRateLimited("a", oneMin, null)
        }
        val wait = s.notBeforeMs("a")!! - now
        assertEquals(sixtyMin, wait)
    }

    @Test
    fun `success resets backoff`() {
        val s = RefreshScheduler(clock)
        s.onRateLimited("a", oneMin, null)
        s.onSuccess("a")
        assertTrue(s.shouldFetch("a"))
        s.onRateLimited("a", oneMin, null)
        assertEquals(now + oneMin, s.notBeforeMs("a"))
    }

    @Test
    fun `Retry-After seconds honored over exponential`() {
        val s = RefreshScheduler(clock)
        s.onRateLimited("a", oneMin, 120_000L)
        assertEquals(now + 120_000L, s.notBeforeMs("a"))
    }

    @Test
    fun `vendors independent`() {
        val s = RefreshScheduler(clock)
        s.onRateLimited("a", oneMin, null)
        assertTrue(s.shouldFetch("b"))
        assertFalse(s.shouldFetch("a"))
    }

    @Test
    fun `parseRetryAfter delta-seconds`() {
        assertEquals(60_000L, parseRetryAfterMs("60", now))
        assertEquals(0L, parseRetryAfterMs("0", now))
        assertEquals(null, parseRetryAfterMs(null, now))
        assertEquals(null, parseRetryAfterMs("  ", now))
    }

    @Test
    fun `parseRetryAfter HTTP-date`() {
        val target = Instant.ofEpochMilli(now + 90_000L)
        val header = DateTimeFormatter.RFC_1123_DATE_TIME
            .withZone(ZoneOffset.UTC)
            .format(target)
        assertEquals(90_000L, parseRetryAfterMs(header, now))
    }
}
