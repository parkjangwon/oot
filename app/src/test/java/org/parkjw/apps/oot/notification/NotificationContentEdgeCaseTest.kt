package org.parkjw.apps.oot.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.parkjw.apps.oot.data.model.Level
import org.parkjw.apps.oot.data.model.ProviderType
import org.parkjw.apps.oot.data.model.Remaining
import org.parkjw.apps.oot.data.model.UsageWindow
import org.parkjw.apps.oot.data.model.VendorError
import org.parkjw.apps.oot.data.model.VendorUsage
import java.time.Instant

class NotificationContentEdgeCaseTest {

    private val now: Instant = Instant.parse("2026-10-21T12:00:00Z")
    private val ko = TestNotificationStrings.KO
    private val en = TestNotificationStrings.EN

    private fun minutes(m: Long): Instant = now.plusSeconds(m * 60)

    private fun usage(vararg windows: UsageWindow, provider: ProviderType = ProviderType.CLAUDE) =
        VendorUsage("id", provider, "acc", windows.toList())

    private fun pct(id: String, label: String, left: Double, resetsAt: Instant? = null) =
        UsageWindow(id, label, Remaining.Percent(left), resetsAt)

    private fun expanded(u: VendorUsage, strings: NotificationStrings = ko) =
        NotificationContent.expandedItems(listOf(u), strings, now).single()

    private fun openRouter(credit: Double, keyLeft: Double?, keyLabel: String = "키 한도 $20.00") = VendorUsage(
        "id", ProviderType.OPENROUTER, "acc",
        listOfNotNull(
            UsageWindow("credit", "credit", Remaining.Balance(credit)),
            keyLeft?.let { UsageWindow("key", keyLabel, Remaining.Balance(it)) },
        ),
    )

    // --- formatReset boundaries ---

    @Test
    fun `formatReset minute and hour boundaries`() {
        assertEquals("", NotificationContent.formatReset(now, now, ko))
        assertEquals("", NotificationContent.formatReset(now.plusSeconds(59), now, ko))
        assertEquals("1분 후", NotificationContent.formatReset(minutes(1), now, ko))
        assertEquals("59분 후", NotificationContent.formatReset(minutes(59), now, ko))
        assertEquals("1시간 후", NotificationContent.formatReset(minutes(60), now, ko))
        assertEquals("1시간 1분 후", NotificationContent.formatReset(minutes(61), now, ko))
        assertEquals("23시간 59분 후", NotificationContent.formatReset(minutes(1439), now, ko))
        assertEquals("1일 후", NotificationContent.formatReset(minutes(1440), now, ko))
    }

    @Test
    fun `formatReset drops the hours once a day has passed while the notification form keeps them`() {
        assertEquals("1일 후", NotificationContent.formatReset(minutes(1500), now, ko))
        assertEquals("1일 1시간 후", NotificationContent.formatNotificationReset(minutes(1500), now, ko))
        assertEquals("1일 후", NotificationContent.formatNotificationReset(minutes(1440 + 59), now, ko))
    }

    @Test
    fun `notification reset in the past or under a day follows formatReset`() {
        assertEquals("", NotificationContent.formatNotificationReset(minutes(-5), now, ko))
        assertEquals("23시간 59분 후", NotificationContent.formatNotificationReset(minutes(1439), now, ko))
        assertEquals("in 1h 5m", NotificationContent.formatNotificationReset(minutes(65), now, en))
        assertEquals("in 2d 3h", NotificationContent.formatNotificationReset(minutes(2 * 1440 + 180), now, en))
    }

    // --- formatting helpers ---

    @Test
    fun `percent text is rounded and clamped to 0-100`() {
        assertEquals("62%", NotificationContent.formatPercent(Remaining.Percent(61.5)))
        assertEquals("0%", NotificationContent.formatPercent(Remaining.Percent(0.4)))
        assertEquals("0%", NotificationContent.formatPercent(Remaining.Percent(-3.0)))
        assertEquals("100%", NotificationContent.formatPercent(Remaining.Percent(130.0)))
    }

    @Test
    fun `currency symbol is case-insensitive and defaults to dollar`() {
        assertEquals("¥", NotificationContent.currencySymbol("cny"))
        assertEquals("$", NotificationContent.currencySymbol(""))
        assertEquals("¥5.00", NotificationContent.formatBalance(5.0, "cny"))
    }

    @Test
    fun `keyLimitAmount reads the first number of a label`() {
        assertEquals(20.0, NotificationContent.keyLimitAmount("키 한도 $20.00")!!, 0.0)
        assertEquals(5.0, NotificationContent.keyLimitAmount("limit 5")!!, 0.0)
        assertNull(NotificationContent.keyLimitAmount("no limit"))
    }

    @Test
    fun `short names and marks are non-blank for every provider`() {
        ProviderType.entries.forEach {
            assertTrue(NotificationContent.shortName(it).isNotBlank())
            assertTrue(NotificationContent.mark(it).isNotBlank())
        }
    }

    // --- collapsed ---

    @Test
    fun `collapsed with a zero or negative max is empty and an empty list stays empty`() {
        val list = listOf(usage(pct("five_hour", "5h", 50.0)))
        assertTrue(NotificationContent.collapsedItems(list, 0, ko).isEmpty())
        assertTrue(NotificationContent.collapsedItems(list, -2, ko).isEmpty())
        assertTrue(NotificationContent.collapsedItems(emptyList(), 3, ko).isEmpty())
        assertEquals(1, NotificationContent.collapsedItems(list, 99, ko).size)
    }

    @Test
    fun `collapsed levels follow the warn and danger thresholds and balances stay ok`() {
        fun level(left: Double) =
            NotificationContent.collapsedItems(listOf(usage(pct("five_hour", "5h", left))), 1, ko).single().level
        assertEquals(Level.OK, level(20.5))
        assertEquals(Level.WARN, level(20.0))
        assertEquals(Level.WARN, level(5.5))
        assertEquals(Level.DANGER, level(5.0))
        assertEquals(Level.DANGER, level(0.0))
        val bal = VendorUsage("id", ProviderType.DEEPSEEK, "acc", listOf(UsageWindow("b", "b", Remaining.Balance(0.01))))
        assertEquals(Level.OK, NotificationContent.collapsedItems(listOf(bal), 1, ko).single().level)
    }

    @Test
    fun `collapsed percent is clamped for out of range values`() {
        val item = NotificationContent.collapsedItems(listOf(usage(pct("five_hour", "5h", 140.0))), 1, ko).single()
        assertEquals(100, item.percent)
        assertEquals("100% 남음", item.label)
        val negative = NotificationContent.collapsedItems(listOf(usage(pct("five_hour", "5h", -8.0))), 1, ko).single()
        assertEquals(0, negative.percent)
        assertEquals(Level.DANGER, negative.level)
    }

    @Test
    fun `collapsed shows an errored vendor with no windows as check needed in english too`() {
        val err = VendorUsage("id", ProviderType.OLLAMA_CLOUD, "acc", emptyList(), VendorError.Parse)
        val item = NotificationContent.collapsedItems(listOf(err), 1, en).single()
        assertEquals("Ollama needs attention", item.label)
        assertTrue(item.isError)
    }

    // --- expanded ---

    @Test
    fun `an error wins over windows that are still present`() {
        val u = usage(pct("five_hour", "5h", 50.0)).copy(error = VendorError.RateLimited)
        val item = expanded(u)
        assertEquals("확인 필요", item.text)
        assertTrue(item.isError)
        assertNull(item.percent)
    }

    @Test
    fun `expanded levels follow the thresholds`() {
        assertEquals(Level.WARN, expanded(usage(pct("five_hour", "5h", 15.0))).level)
        assertEquals(Level.DANGER, expanded(usage(pct("five_hour", "5h", 4.0))).level)
        assertEquals(Level.OK, expanded(usage(pct("five_hour", "5h", 80.0))).level)
    }

    @Test
    fun `window labels cover weekly id named groups and blank labels`() {
        assertEquals("7D 30% 남음", expanded(usage(pct("weekly", "주간", 30.0))).text)
        assertEquals("Opus 30% 남음", expanded(usage(pct("opus", "Opus model group", 30.0))).text)
        assertEquals("Opus 30% 남음", expanded(usage(pct("opus", "Opus", 30.0))).text)
        assertEquals("30% 남음", expanded(usage(pct("other", "", 30.0))).text)
        assertEquals("30% 남음", expanded(usage(pct("other", "   ", 30.0))).text)
    }

    @Test
    fun `expanded english copy`() {
        val u = usage(pct("five_hour", "5h", 62.0, minutes(125)))
        assertEquals("5H 62% left · in 2h 5m", expanded(u, en).text)
        val bal = VendorUsage("id", ProviderType.DEEPSEEK, "acc", listOf(UsageWindow("b", "b", Remaining.Balance(3.5, "CNY"))))
        assertEquals("¥3.50 left", expanded(bal, en).text)
        assertEquals("Needs attention", expanded(usage().copy(error = VendorError.Network), en).text)
    }

    @Test
    fun `a reset that already passed adds no suffix`() {
        assertEquals("5H 62% 남음", expanded(usage(pct("five_hour", "5h", 62.0, minutes(-3)))).text)
        assertEquals("5H 62% 남음", expanded(usage(pct("five_hour", "5h", 62.0, now))).text)
    }

    @Test
    fun `stale balance and openrouter lines get the suffix and stale errors do not`() {
        val stale = now.minusSeconds(3600)
        val bal = VendorUsage("id", ProviderType.DEEPSEEK, "acc", listOf(UsageWindow("b", "b", Remaining.Balance(12.4))), staleSince = stale)
        assertEquals("$12.40 남음 (이전값)", expanded(bal).text)
        assertEquals("$7.00 남음 (이전값)", expanded(openRouter(12.4, 7.0).copy(staleSince = stale)).text)
        val err = usage().copy(error = VendorError.Network, staleSince = stale)
        assertEquals("확인 필요", expanded(err).text)
        assertEquals("$12.40 left (previous)", expanded(bal, en).text)
    }

    // --- OpenRouter view ---

    @Test
    fun `openrouter view is only built for openrouter with a credit window`() {
        val other = openRouter(5.0, 3.0).copy(providerType = ProviderType.DEEPSEEK)
        assertNull(NotificationContent.openRouterView(other))
        val keyOnly = VendorUsage(
            "id", ProviderType.OPENROUTER, "acc",
            listOf(UsageWindow("key", "키 한도 $20.00", Remaining.Balance(3.0))),
        )
        assertNull(NotificationContent.openRouterView(keyOnly))
        // Without a credit window the notification falls back to the first balance window.
        assertEquals("$3.00 남음", expanded(keyOnly).text)
    }

    @Test
    fun `openrouter amount is the smaller of credit and key remaining`() {
        val keyHigher = NotificationContent.openRouterView(openRouter(4.0, 9.0))!!
        assertEquals(4.0, keyHigher.amountLeft, 0.0)
        assertEquals(45, keyHigher.keyPercent)
        val keyLower = NotificationContent.openRouterView(openRouter(40.0, 5.0))!!
        assertEquals(5.0, keyLower.amountLeft, 0.0)
        assertEquals(25, keyLower.keyPercent)
    }

    @Test
    fun `openrouter bar is absent when the limit is unreadable or zero and clamped when overfull`() {
        assertNull(NotificationContent.openRouterView(openRouter(10.0, 5.0, "unlimited"))!!.keyPercent)
        assertNull(NotificationContent.openRouterView(openRouter(10.0, 5.0, "키 한도 $0.00"))!!.keyPercent)
        assertEquals(100, NotificationContent.openRouterView(openRouter(100.0, 30.0))!!.keyPercent)
        assertEquals("USD", NotificationContent.openRouterView(openRouter(10.0, 5.0))!!.currency)
    }

    // --- text fallback ---

    @Test
    fun `text fallback english copy empty list and stale handling`() {
        assertEquals("", NotificationContent.textFallback(emptyList(), ko))
        val stale = usage(pct("five_hour", "5h", 62.0)).copy(staleSince = now)
        val err = usage().copy(error = VendorError.Network)
        assertEquals("Claude 62% (previous) · Claude Needs attention", NotificationContent.textFallback(listOf(stale, err), en))
    }

    @Test
    fun `text fallback shows an openrouter amount and clamps percent`() {
        val over = usage(pct("five_hour", "5h", 150.0), provider = ProviderType.ZAI)
        assertEquals("OpenRouter $7.00 · GLM 100%", NotificationContent.textFallback(listOf(openRouter(12.4, 7.0), over), ko))
    }

    @Test
    fun `empty state text for english`() {
        assertEquals("No providers yet. Add one in the app.", NotificationContent.emptyStateText(0, en))
        assertEquals(en.emptyAllHidden, NotificationContent.emptyStateText(1, en))
    }
}
