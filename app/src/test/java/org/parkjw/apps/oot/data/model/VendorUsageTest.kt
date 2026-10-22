package org.parkjw.apps.oot.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class VendorUsageTest {

    private val now: Instant = Instant.parse("2026-10-21T12:00:00Z")

    private fun status(
        usagePercent: Float? = null,
        remainingFormatted: String = "",
        isError: Boolean = false,
        errorMessage: String? = null,
        provider: ProviderType = ProviderType.CLAUDE,
        accountName: String = "acc",
        staleSince: Long? = null,
        isRateLimited: Boolean = false,
        windows: List<UsageWindow> = emptyList(),
    ) = TokenStatus(
        accountId = "id",
        provider = provider,
        accountName = accountName,
        remainingFormatted = remainingFormatted,
        usagePercent = usagePercent,
        statusSummary = "",
        isError = isError,
        errorMessage = errorMessage,
        staleSince = staleSince,
        isRateLimited = isRateLimited,
        windows = windows,
    )

    private fun vendor(
        vararg windows: UsageWindow,
        error: VendorError? = null,
        provider: ProviderType = ProviderType.CLAUDE
    ) = VendorUsage("id", provider, "acc", windows.toList(), error)

    @Test
    fun `representative is window with minimum percent`() {
        val v = vendor(
            UsageWindow("w1", "5시간", Remaining.Percent(80.0)),
            UsageWindow("w2", "주간", Remaining.Percent(30.0)),
            UsageWindow("w3", "월간", Remaining.Percent(95.0))
        )
        assertEquals("w2", v.representative?.id)
    }

    @Test
    fun `representative is the 5-hour window even when another has less remaining`() {
        val v = vendor(
            UsageWindow("seven_day", "주간", Remaining.Percent(10.0)),
            UsageWindow("five_hour", "5시간", Remaining.Percent(80.0))
        )
        assertEquals("five_hour", v.representative?.id)
    }

    @Test
    fun `balance-only vendor picks first balance window`() {
        val w1 = UsageWindow("b1", "credit", Remaining.Balance(12.4))
        val w2 = UsageWindow("b2", "credit2", Remaining.Balance(50.0))
        val v = vendor(w1, w2)
        assertEquals("b1", v.representative?.id)
    }

    @Test
    fun `representative is null when no windows`() {
        assertNull(vendor().representative)
    }

    @Test
    fun `severity thresholds`() {
        assertEquals(Level.OK, Remaining.Percent(20.1).level)
        assertEquals(Level.WARN, Remaining.Percent(20.0).level)
        assertEquals(Level.WARN, Remaining.Percent(5.1).level)
        assertEquals(Level.DANGER, Remaining.Percent(5.0).level)
        assertEquals(Level.DANGER, Remaining.Percent(0.0).level)
        assertEquals(Level.OK, Remaining.Balance(1.0).level)
    }

    @Test
    fun `sort puts errored vendors first then ascending remaining`() {
        val high = vendor(UsageWindow("w", "월간", Remaining.Percent(90.0)))
        val low = vendor(UsageWindow("w", "주간", Remaining.Percent(10.0)))
        val balance = vendor(UsageWindow("b", "credit", Remaining.Balance(5.0)))
        val errored = vendor(error = VendorError.Http(500))
        val sorted = listOf(high, balance, errored, low).sortedByRemaining()
        assertEquals(listOf(errored, low, high, balance), sorted)
    }

    @Test
    fun `usage percent converts to remaining percent`() {
        val v = status(usagePercent = 30f).toVendorUsage()
        val remaining = v.representative?.remaining as Remaining.Percent
        assertEquals(70.0, remaining.percentLeft, 0.001)
    }

    @Test
    fun `balance formatted string maps to balance remaining`() {
        val v = status(remainingFormatted = "$12.45 남음").toVendorUsage()
        val remaining = v.representative?.remaining as Remaining.Balance
        assertEquals(12.45, remaining.amountLeft, 0.001)
        assertEquals("USD", remaining.currency)
    }

    @Test
    fun `error status maps to errored vendor without windows`() {
        val v = status(isError = true, errorMessage = "failed").toVendorUsage()
        assertEquals(VendorError.Unknown("failed"), v.error)
        assertNull(v.representative)
    }

    @Test
    fun `stale status marks the usage and drops windows that already reset`() {
        val staleSince = now.minus(Duration.ofHours(2)).toEpochMilli()
        val v = status(
            staleSince = staleSince,
            windows = listOf(
                UsageWindow("seven_day", "7d", Remaining.Percent(10.0), now.minusSeconds(60)),
                UsageWindow("five_hour", "5h", Remaining.Percent(50.0), now.plusSeconds(3600)),
            ),
        ).toVendorUsage(now)

        assertEquals(listOf("five_hour"), v.windows.map { it.id })
        assertNull(v.error)
        assertEquals(Instant.ofEpochMilli(staleSince), v.staleSince)
    }

    @Test
    fun `stale status whose windows all reset becomes an error`() {
        val staleSince = now.minus(Duration.ofHours(2)).toEpochMilli()
        val past = listOf(
            UsageWindow("weekly", "weekly", Remaining.Percent(4.0), now.minusSeconds(1)),
        )

        val rateLimited = status(staleSince = staleSince, isRateLimited = true, windows = past).toVendorUsage(now)
        assertTrue(rateLimited.windows.isEmpty())
        assertEquals(VendorError.RateLimited, rateLimited.error)

        val unknown = status(staleSince = staleSince, windows = past).toVendorUsage(now)
        assertTrue(unknown.windows.isEmpty())
        assertTrue(unknown.error is VendorError.Unknown)
    }

    @Test
    fun `fresh status keeps a window whose reset just passed`() {
        val v = status(
            windows = listOf(
                UsageWindow("weekly", "weekly", Remaining.Percent(4.0), now.minusSeconds(60)),
            ),
        ).toVendorUsage(now)

        assertEquals(1, v.windows.size)
        assertNull(v.staleSince)
        assertNull(v.error)
    }
}
