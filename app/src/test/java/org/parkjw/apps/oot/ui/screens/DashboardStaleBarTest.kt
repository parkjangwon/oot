package org.parkjw.apps.oot.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DashboardStaleBarTest {

    @Test
    fun `stale cards dim their usage bars`() {
        assertEquals(0.6f, staleBarAlpha(Instant.parse("2026-10-21T10:00:00Z")), 0.001f)
    }

    @Test
    fun `fresh cards keep full bar opacity`() {
        assertEquals(1f, staleBarAlpha(null), 0.001f)
    }
}
