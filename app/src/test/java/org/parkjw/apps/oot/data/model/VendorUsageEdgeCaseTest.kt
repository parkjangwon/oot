package org.parkjw.apps.oot.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class VendorUsageEdgeCaseTest {

    private val now: Instant = Instant.parse("2026-10-21T12:00:00Z")
    private val staleSince = now.minus(Duration.ofHours(1)).toEpochMilli()

    private fun status(
        usagePercent: Float? = null,
        remainingFormatted: String = "",
        isError: Boolean = false,
        errorMessage: String? = null,
        staleSince: Long? = null,
        isRateLimited: Boolean = false,
        windows: List<UsageWindow> = emptyList(),
    ) = TokenStatus(
        accountId = "id",
        provider = ProviderType.DEEPSEEK,
        accountName = "acc",
        remainingFormatted = remainingFormatted,
        usagePercent = usagePercent,
        statusSummary = "",
        isError = isError,
        errorMessage = errorMessage,
        staleSince = staleSince,
        isRateLimited = isRateLimited,
        windows = windows,
    )

    private fun vendor(vararg windows: UsageWindow, error: VendorError? = null) =
        VendorUsage("id", ProviderType.CLAUDE, "acc", windows.toList(), error)

    @Test
    fun `a five-hour balance window is not the percent representative`() {
        val v = vendor(
            UsageWindow("five_hour", "5h", Remaining.Balance(9.0)),
            UsageWindow("weekly", "7d", Remaining.Percent(40.0)),
        )
        assertEquals("weekly", v.representative?.id)
    }

    @Test
    fun `percent windows win over balances and ties pick the first`() {
        val v = vendor(
            UsageWindow("b", "credit", Remaining.Balance(1.0)),
            UsageWindow("p1", "a", Remaining.Percent(30.0)),
            UsageWindow("p2", "b", Remaining.Percent(30.0)),
        )
        assertEquals("p1", v.representative?.id)
    }

    @Test
    fun `sort keeps input order among equal keys and orders several errors together`() {
        val e1 = vendor(error = VendorError.Network)
        val e2 = vendor(error = VendorError.Parse)
        val a = vendor(UsageWindow("w", "a", Remaining.Percent(50.0)))
        val b = vendor(UsageWindow("w", "b", Remaining.Percent(50.0)))
        val sorted = listOf(a, e1, b, e2).sortedByRemaining()
        assertEquals(listOf(e1, e2, a, b), sorted)
    }

    @Test
    fun `sorting an empty list returns an empty list`() {
        assertTrue(emptyList<VendorUsage>().sortedByRemaining().isEmpty())
    }

    @Test
    fun `level boundaries are inclusive and balances never warn`() {
        assertEquals(Level.WARN, Remaining.Percent(20.0).level)
        assertEquals(Level.OK, Remaining.Percent(20.0001).level)
        assertEquals(Level.DANGER, Remaining.Percent(5.0).level)
        assertEquals(Level.WARN, Remaining.Percent(5.0001).level)
        assertEquals(Level.DANGER, Remaining.Percent(-1.0).level)
        assertEquals(Level.OK, Remaining.Balance(0.0).level)
        assertEquals(Level.OK, Remaining.Balance(-3.0).level)
    }

    @Test
    fun `usage percent edge values convert to remaining`() {
        assertEquals(100.0, (status(usagePercent = 0f).toVendorUsage(now).representative!!.remaining as Remaining.Percent).percentLeft, 0.001)
        val full = status(usagePercent = 100f).toVendorUsage(now).representative!!.remaining as Remaining.Percent
        assertEquals(0.0, full.percentLeft, 0.001)
        assertEquals(Level.DANGER, full.level)
    }

    @Test
    fun `status without percent or number falls back to a full primary window`() {
        val v = status(remainingFormatted = "확인 필요").toVendorUsage(now)
        val w = v.windows.single()
        assertEquals("primary", w.id)
        assertEquals(Remaining.Percent(100.0), w.remaining)
    }

    @Test
    fun `percent takes precedence over a formatted amount and the amount keeps its label`() {
        val both = status(usagePercent = 25f, remainingFormatted = "$9.00 남음").toVendorUsage(now)
        assertEquals(Remaining.Percent(75.0), both.windows.single().remaining)
        val amount = status(remainingFormatted = "$9.5 left").toVendorUsage(now).windows.single()
        assertEquals("$9.5 left", amount.label)
        assertEquals(9.5, (amount.remaining as Remaining.Balance).amountLeft, 0.0)
    }

    @Test
    fun `explicit windows are carried over untouched for a fresh status`() {
        val w = listOf(UsageWindow("five_hour", "5h", Remaining.Percent(10.0), now.minusSeconds(5)))
        assertEquals(w, status(windows = w).toVendorUsage(now).windows)
    }

    @Test
    fun `error status keeps its stale time and message`() {
        val v = status(isError = true, errorMessage = "boom", staleSince = staleSince).toVendorUsage(now)
        assertEquals(Instant.ofEpochMilli(staleSince), v.staleSince)
        assertEquals(VendorError.Unknown("boom"), v.error)
        assertTrue(v.windows.isEmpty())
        assertEquals(VendorError.Unknown(null), status(isError = true).toVendorUsage(now).error)
    }

    @Test
    fun `stale status drops a window whose reset is exactly now and keeps undated ones`() {
        val v = status(
            staleSince = staleSince,
            windows = listOf(
                UsageWindow("a", "a", Remaining.Percent(10.0), now),
                UsageWindow("b", "b", Remaining.Percent(20.0), null),
                UsageWindow("c", "c", Remaining.Percent(30.0), now.plusSeconds(1)),
            ),
        ).toVendorUsage(now)
        assertEquals(listOf("b", "c"), v.windows.map { it.id })
        assertNull(v.error)
    }

    @Test
    fun `stale status with the percent fallback keeps its undated primary window`() {
        val v = status(usagePercent = 40f, staleSince = staleSince).toVendorUsage(now)
        assertEquals(Remaining.Percent(60.0), v.windows.single().remaining)
        assertNull(v.error)
    }

    @Test
    fun `rollover returns the same instance when nothing changes`() {
        val fresh = vendor(UsageWindow("w", "w", Remaining.Percent(5.0), now.minusSeconds(10)))
        assertSame(fresh, fresh.withStaleRollover(now))
        val stale = fresh.copy(staleSince = Instant.ofEpochMilli(staleSince)).copy(
            windows = listOf(UsageWindow("w", "w", Remaining.Percent(5.0), now.plusSeconds(10))),
        )
        assertSame(stale, stale.withStaleRollover(now))
    }

    @Test
    fun `rollover drops only passed windows and keeps an existing error when all pass`() {
        val s = Instant.ofEpochMilli(staleSince)
        val mixed = vendor(
            UsageWindow("old", "old", Remaining.Percent(5.0), now.minusSeconds(1)),
            UsageWindow("new", "new", Remaining.Percent(50.0), now.plusSeconds(60)),
        ).copy(staleSince = s)
        val partial = mixed.withStaleRollover(now)
        assertEquals(listOf("new"), partial.windows.map { it.id })
        assertNull(partial.error)

        val allGone = vendor(
            UsageWindow("old", "old", Remaining.Percent(5.0), now.minusSeconds(1)),
            error = VendorError.Network,
        ).copy(staleSince = s).withStaleRollover(now)
        assertTrue(allGone.windows.isEmpty())
        assertEquals(VendorError.Network, allGone.error)
    }
}
