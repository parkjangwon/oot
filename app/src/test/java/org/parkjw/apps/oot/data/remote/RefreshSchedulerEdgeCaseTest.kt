package org.parkjw.apps.oot.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshSchedulerEdgeCaseTest {

    private var now = 1_000_000L
    private val clock = { now }
    private val oneMin = 60_000L
    private val sixtyMin = 60L * 60L * 1000L

    @Test
    fun `unknown key fetches and has no wait and success on it is a no-op`() {
        val s = RefreshScheduler(clock)
        assertTrue(s.shouldFetch("x"))
        assertNull(s.notBeforeMs("x"))
        s.onSuccess("x")
        assertTrue(s.shouldFetch("x"))
    }

    @Test
    fun `non-positive Retry-After falls back to the exponential wait`() {
        val s = RefreshScheduler(clock)
        s.onRateLimited("a", oneMin, 0L)
        assertEquals(now + oneMin, s.notBeforeMs("a"))
        s.onRateLimited("a", oneMin, -5L)
        assertEquals(now + 2 * oneMin, s.notBeforeMs("a"))
    }

    @Test
    fun `Retry-After is capped and still counts as a strike`() {
        val s = RefreshScheduler(clock)
        s.onRateLimited("a", oneMin, 10 * sixtyMin)
        assertEquals(now + sixtyMin, s.notBeforeMs("a"))
        // The next 429 without a header continues from strike 2, not from the base.
        s.onRateLimited("a", oneMin, null)
        assertEquals(now + 2 * oneMin, s.notBeforeMs("a"))
    }

    @Test
    fun `a huge strike count neither overflows nor exceeds the cap`() {
        val s = RefreshScheduler(clock)
        repeat(100) { s.onRateLimited("a", oneMin, null) }
        assertEquals(now + sixtyMin, s.notBeforeMs("a"))
    }

    @Test
    fun `a zero base interval never blocks and a custom cap is honoured`() {
        val s = RefreshScheduler(clock)
        s.onRateLimited("a", 0L, null)
        assertTrue(s.shouldFetch("a"))
        val capped = RefreshScheduler(clock, maxBackoffMs = 90_000L)
        repeat(5) { capped.onRateLimited("b", oneMin, null) }
        assertEquals(now + 90_000L, capped.notBeforeMs("b"))
        capped.onRateLimited("b", oneMin, 5 * oneMin)
        assertEquals(now + 90_000L, capped.notBeforeMs("b"))
    }

    @Test
    fun `fetch is allowed again exactly at the not-before time`() {
        val s = RefreshScheduler(clock)
        s.onRateLimited("a", oneMin, null)
        now = s.notBeforeMs("a")!! - 1
        assertFalse(s.shouldFetch("a"))
        now += 1
        assertTrue(s.shouldFetch("a"))
    }

    @Test
    fun `success clears only that key`() {
        val s = RefreshScheduler(clock)
        s.onRateLimited("a", oneMin, null)
        s.onRateLimited("b", oneMin, null)
        s.onSuccess("a")
        assertTrue(s.shouldFetch("a"))
        assertFalse(s.shouldFetch("b"))
    }

    @Test
    fun `parseRetryAfter edge inputs`() {
        assertEquals(5_000L, parseRetryAfterMs("  5  ", now))
        assertEquals(0L, parseRetryAfterMs("-10", now))
        assertNull(parseRetryAfterMs("", now))
        assertNull(parseRetryAfterMs("soon", now))
        assertNull(parseRetryAfterMs("1.5", now))
        assertNull(parseRetryAfterMs("2026-10-21", now))
    }

    @Test
    fun `parseRetryAfter HTTP-date in the past is zero`() {
        assertEquals(0L, parseRetryAfterMs("Wed, 21 Oct 2015 07:28:00 GMT", 1_800_000_000_000L))
    }
}
