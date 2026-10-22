package org.parkjw.apps.oot.data.remote

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.parkjw.apps.oot.data.model.AuthType
import org.parkjw.apps.oot.data.model.ProviderAccount
import org.parkjw.apps.oot.data.model.ProviderType
import org.parkjw.apps.oot.data.model.Remaining
import org.parkjw.apps.oot.data.model.TokenStatus
import org.parkjw.apps.oot.data.model.UsageWindow
import org.parkjw.apps.oot.data.model.asStale
import org.parkjw.apps.oot.data.model.toVendorUsage
import org.parkjw.apps.oot.i18n.AppLanguage
import org.parkjw.apps.oot.i18n.AppLocale
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

// Robolectric: the cached and error texts come from string resources (AppText).
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RateLimitedStatusTest {

    private val fetchedAt = 1_000_000L
    private val nowMs = 5_000_000L
    private val now = Instant.ofEpochMilli(nowMs)

    private val account = ProviderAccount(
        id = "claude-1",
        provider = ProviderType.CLAUDE,
        name = "Claude",
        authType = AuthType.OAUTH,
        accessToken = "at-test-000",
    )

    private val cached = TokenStatus(
        accountId = "claude-1",
        provider = ProviderType.CLAUDE,
        accountName = "Claude",
        remainingFormatted = "62% 남음",
        statusSummary = "62% 남음",
        lastUpdated = fetchedAt,
        windows = listOf(
            UsageWindow("seven_day", "7d", Remaining.Percent(81.0), now.plusSeconds(3 * 24 * 3600)),
        ),
    )

    @Before
    fun setUp() {
        AppLocale.apply(ApplicationProvider.getApplicationContext(), AppLanguage.DEFAULT)
    }

    @Test
    fun `cached 429 keeps the fetch time and marks the values stale`() {
        val status = rateLimitedTokenStatus(account, retryAfterHeader = null, cached = cached, nowMs = nowMs)

        assertEquals(fetchedAt, status.lastUpdated)
        assertEquals(fetchedAt, status.staleSince)
        assertTrue(status.isRateLimited)
        assertEquals(cached.windows, status.windows)
    }

    @Test
    fun `an already stale snapshot keeps its first stale time`() {
        val status = rateLimitedTokenStatus(
            account,
            retryAfterHeader = null,
            cached = cached.copy(staleSince = fetchedAt - 500_000L),
            nowMs = nowMs,
        )

        assertEquals(fetchedAt - 500_000L, status.staleSince)
        assertEquals(fetchedAt, status.lastUpdated)
    }

    // A refresh failure after a success must leave the card stale-marked, not fresh.
    @Test
    fun `a failed refresh after a success converts to a stale usage`() {
        assertNull(cached.toVendorUsage(now).staleSince)

        val failed = rateLimitedTokenStatus(account, retryAfterHeader = null, cached = cached, nowMs = nowMs)
        val usage = failed.toVendorUsage(now)

        assertEquals(Instant.ofEpochMilli(fetchedAt), usage.staleSince)
        assertEquals(1, usage.windows.size)
        assertNull(usage.error)
    }

    @Test
    fun `marking a status stale keeps its original fetch time`() {
        val stale = cached.asStale()

        assertEquals(fetchedAt, stale.staleSince)
        assertEquals(fetchedAt, stale.lastUpdated)
        assertEquals(fetchedAt, stale.copy(lastUpdated = nowMs).asStale().staleSince)
    }
}
