package org.parkjw.apps.oot.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.parkjw.apps.oot.data.model.ProviderType
import org.parkjw.apps.oot.data.model.Remaining
import org.parkjw.apps.oot.data.model.UsageWindow
import org.parkjw.apps.oot.data.model.VendorUsage
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DashboardLeastRemainingTest {

    private val now: Instant = Instant.parse("2026-10-21T12:00:00Z")

    private fun vendor(
        id: String,
        percent: Double,
        provider: ProviderType,
        staleSince: Instant? = null,
    ) = VendorUsage(
        accountId = id,
        providerType = provider,
        displayName = id,
        windows = listOf(UsageWindow("five_hour", "5h", Remaining.Percent(percent), now.plusSeconds(3600))),
        staleSince = staleSince,
    )

    @Test
    fun `least remaining header skips a stale vendor`() {
        val stale = vendor("stale", 3.0, ProviderType.CLAUDE, staleSince = now.minusSeconds(7200))
        val fresh = vendor("fresh", 80.0, ProviderType.ZAI)

        val least = findLeastRemainingItem(listOf(stale, fresh))

        assertEquals("fresh", least?.first?.accountId)
    }

    @Test
    fun `least remaining header is empty when every vendor is stale`() {
        val stale = vendor("stale", 3.0, ProviderType.CLAUDE, staleSince = now.minusSeconds(7200))

        assertNull(findLeastRemainingItem(listOf(stale)))
    }
}
