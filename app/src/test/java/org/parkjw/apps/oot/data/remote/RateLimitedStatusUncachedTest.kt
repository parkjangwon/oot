package org.parkjw.apps.oot.data.remote

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.parkjw.apps.oot.data.model.AuthType
import org.parkjw.apps.oot.data.model.ProviderAccount
import org.parkjw.apps.oot.data.model.ProviderType
import org.parkjw.apps.oot.data.model.TokenStatus
import org.parkjw.apps.oot.i18n.AppLanguage
import org.parkjw.apps.oot.i18n.AppLocale
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric: texts come from string resources (AppText).
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RateLimitedStatusUncachedTest {

    private val nowMs = 5_000_000L

    private val account = ProviderAccount(
        id = "xai-1",
        provider = ProviderType.XAI,
        name = "xAI",
        authType = AuthType.API_KEY,
        accessToken = "sk-test-000",
    )

    private fun cached(isError: Boolean = false) = TokenStatus(
        accountId = "xai-1",
        provider = ProviderType.XAI,
        accountName = "xAI",
        remainingFormatted = "40% 남음",
        statusSummary = "40%",
        isError = isError,
        lastUpdated = 1_000_000L,
    )

    @Before
    fun setUp() {
        AppLocale.apply(ApplicationProvider.getApplicationContext(), AppLanguage.DEFAULT)
    }

    @Test
    fun `without a cache the result is a rate limited error stamped now`() {
        val status = rateLimitedTokenStatus(account, retryAfterHeader = "30", nowMs = nowMs)

        assertTrue(status.isError)
        assertTrue(status.isRateLimited)
        assertEquals(nowMs, status.lastUpdated)
        assertEquals("xai-1", status.accountId)
        assertEquals("xAI", status.accountName)
        assertEquals(ProviderType.XAI, status.provider)
        assertEquals(30_000L, status.rateLimitedRetryAfterMs)
        assertNull(status.staleSince)
        assertTrue(status.windows.isEmpty())
    }

    @Test
    fun `an errored cache is not reused`() {
        val status = rateLimitedTokenStatus(account, retryAfterHeader = null, cached = cached(isError = true), nowMs = nowMs)

        assertTrue(status.isError)
        assertEquals(nowMs, status.lastUpdated)
        assertNull(status.staleSince)
        assertNull(status.rateLimitedRetryAfterMs)
    }

    @Test
    fun `a good cache is kept as a warning with the previous value suffix and the retry hint`() {
        val status = rateLimitedTokenStatus(account, retryAfterHeader = "120", cached = cached(), nowMs = nowMs)

        assertFalse(status.isError)
        assertTrue(status.isWarning)
        assertTrue(status.remainingFormatted.startsWith("40% 남음 "))
        assertTrue(status.statusSummary.startsWith("40% "))
        assertEquals(120_000L, status.rateLimitedRetryAfterMs)
    }

    @Test
    fun `the provider can be overridden for the error result`() {
        val status = rateLimitedTokenStatus(account, provider = ProviderType.ZAI, retryAfterHeader = null, nowMs = nowMs)
        assertEquals(ProviderType.ZAI, status.provider)
    }
}
