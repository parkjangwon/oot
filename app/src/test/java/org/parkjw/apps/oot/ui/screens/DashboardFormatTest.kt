package org.parkjw.apps.oot.ui.screens

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.parkjw.apps.oot.data.model.VendorError
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en")
class DashboardFormatTest {

    private val res = ApplicationProvider.getApplicationContext<Context>().resources
    private val now: Instant = Instant.parse("2026-10-21T12:00:00Z")

    @Test
    fun `key limit is parsed from the window label`() {
        // OpenRouter codec labels the key window with formatUsd(limit)
        assertEquals(20.0, keyLimitFromLabel("\$20.00")!!, 0.0)
        assertEquals(5.0, keyLimitFromLabel("5")!!, 0.0)
        assertNull(keyLimitFromLabel("no amount"))
        assertNull(keyLimitFromLabel(""))
    }

    @Test
    fun `time ago rounds to the nearest minute and picks the largest unit`() {
        assertEquals("1m", formatTimeAgo(res, now, now))
        assertEquals("1m", formatTimeAgo(res, now.plusSeconds(60), now)) // future clamps to 0 -> 1m
        assertEquals("2m", formatTimeAgo(res, now.minusSeconds(90), now))
        assertEquals("59m", formatTimeAgo(res, now.minusSeconds(59 * 60), now))
        assertEquals("1h", formatTimeAgo(res, now.minusSeconds(3600 + 29 * 60), now))
        assertEquals("1d", formatTimeAgo(res, now.minusSeconds(24 * 3600), now))
        assertEquals("3d", formatTimeAgo(res, now.minusSeconds(3 * 24 * 3600 + 5 * 3600), now))
    }

    @Test
    fun `time to reset formats each unit and hides past or sub-minute resets`() {
        assertNull(formatTimeToReset(res, null, now))
        assertNull(formatTimeToReset(res, now, now))
        assertNull(formatTimeToReset(res, now.minusSeconds(60), now))
        assertNull(formatTimeToReset(res, now.plusSeconds(29), now))
        assertEquals("in 1m", formatTimeToReset(res, now.plusSeconds(30), now))
        assertEquals("in 45m", formatTimeToReset(res, now.plusSeconds(45 * 60), now))
        assertEquals("in 2h", formatTimeToReset(res, now.plusSeconds(2 * 3600), now))
        assertEquals("in 2h 5m", formatTimeToReset(res, now.plusSeconds(2 * 3600 + 5 * 60), now))
        assertEquals("in 1d", formatTimeToReset(res, now.plusSeconds(25 * 3600), now))
    }

    @Test
    fun `every vendor error has non blank short text`() {
        val errors = listOf(
            VendorError.Unauthorized, VendorError.ScopeMissing, VendorError.RateLimited,
            VendorError.Network, VendorError.Parse, VendorError.Http(503), VendorError.Unknown(),
        )
        val texts = errors.map { vendorErrorShortText(res, it) }
        assertTrue(texts.all { it.isNotBlank() })
        assertTrue(vendorErrorShortText(res, VendorError.Http(503)).contains("503"))
    }

    @Test
    fun `unknown error prefers a non blank message`() {
        assertEquals("boom", vendorErrorShortText(res, VendorError.Unknown("boom")))
        assertEquals(
            vendorErrorShortText(res, VendorError.Unknown()),
            vendorErrorShortText(res, VendorError.Unknown("  ")),
        )
    }
}
