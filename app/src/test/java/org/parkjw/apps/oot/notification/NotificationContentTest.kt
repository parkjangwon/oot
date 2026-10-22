package org.parkjw.apps.oot.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

class NotificationContentTest {

    private val now: Instant = Instant.parse("2026-10-21T12:00:00Z")

    private fun vendor(
        percent: Double,
        provider: ProviderType = ProviderType.CLAUDE,
        resetsAt: Instant? = null,
        vararg otherWindows: UsageWindow
    ) = VendorUsage(
        accountId = "id",
        providerType = provider,
        displayName = "acc",
        windows = listOf(UsageWindow("five_hour", "5시간", Remaining.Percent(percent), resetsAt)) + otherWindows
    )

    private fun balance(amount: Double, provider: ProviderType = ProviderType.OPENROUTER) =
        VendorUsage("id", provider, "acc", listOf(UsageWindow("b", "credit", Remaining.Balance(amount))))

    private fun errored(provider: ProviderType = ProviderType.XAI) =
        VendorUsage("id", provider, "acc", emptyList(), VendorError.Network)

    @Test
    fun `collapsed follows user order and keeps errors in place`() {
        val high = vendor(90.0)
        val low = vendor(10.0, ProviderType.ZAI)
        val bal = balance(12.4)
        val err = errored()
        val items = NotificationContent.collapsedItems(listOf(high, bal, err, low), maxItems = 3)
        assertEquals(listOf("Cl", "R", "X"), items.map { it.mark })
        assertTrue(items[2].isError)
    }

    @Test
    fun `collapsed respects max item count`() {
        val usages = listOf(vendor(10.0), vendor(50.0), vendor(80.0))
        assertEquals(2, NotificationContent.collapsedItems(usages, maxItems = 2).size)
        assertEquals(3, NotificationContent.collapsedItems(usages, maxItems = 3).size)
    }

    @Test
    fun `collapsed percent label and bar value`() {
        val item = NotificationContent.collapsedItems(listOf(vendor(61.6)), maxItems = 3).single()
        assertEquals("Cl", item.mark)
        assertEquals("62% 남음", item.label)
        assertEquals(62, item.percent)
        assertFalse(item.isError)
    }

    @Test
    fun `collapsed balance label has no bar`() {
        val item = NotificationContent.collapsedItems(listOf(balance(12.4)), maxItems = 3).single()
        assertEquals("R", item.mark)
        assertEquals("$12.40", item.label)
        assertNull(item.percent)
    }

    @Test
    fun `collapsed error label`() {
        val item = NotificationContent.collapsedItems(listOf(errored()), maxItems = 3).single()
        assertEquals("X", item.mark)
        assertEquals("xAI 확인 필요", item.label)
        assertNull(item.percent)
        assertTrue(item.isError)
    }

    @Test
    fun `marks are unique per provider`() {
        val marks = ProviderType.entries.map { NotificationContent.mark(it) }
        assertEquals(marks.size, marks.distinct().size)
    }

    @Test
    fun `balance formats with two decimals`() {
        assertEquals("$12.40", NotificationContent.formatBalance(12.4))
        assertEquals("$0.00", NotificationContent.formatBalance(0.0))
        assertEquals("$123.46", NotificationContent.formatBalance(123.456))
        assertEquals("¥12.40", NotificationContent.formatBalance(12.4, "CNY"))
        assertEquals("$12.40", NotificationContent.formatBalance(12.4, "EUR"))
    }

    @Test
    fun `reset text formats`() {
        assertEquals("1시간 40분 후", NotificationContent.formatReset(now.plusSeconds(100 * 60), now))
        assertEquals("2시간 후", NotificationContent.formatReset(now.plusSeconds(120 * 60), now))
        assertEquals("3일 후", NotificationContent.formatReset(now.plusSeconds(72 * 3600), now))
        assertEquals("15분 후", NotificationContent.formatReset(now.plusSeconds(15 * 60), now))
        assertEquals("", NotificationContent.formatReset(now.minusSeconds(60), now))
    }

    @Test
    fun `text fallback joins vendors in user order`() {
        val text = NotificationContent.textFallback(
            listOf(balance(12.4), errored(), vendor(62.0), vendor(98.0, ProviderType.ZAI))
        )
        assertEquals("OpenRouter $12.40 · xAI 확인 필요 · Claude 62% · GLM 98%", text)
    }

    @Test
    fun `expanded line shows representative window and its reset`() {
        val resetsAt = now.plusSeconds(120 * 60)
        val weekly = UsageWindow("w2", "주간", Remaining.Percent(81.0), null)
        val item = NotificationContent.expandedItems(
            listOf(vendor(62.0, ProviderType.CLAUDE, resetsAt, weekly)),
            now = now
        ).single()
        assertEquals("Claude", item.name)
        assertEquals("5시간 62% 남음 · 2시간 후 리셋", item.text)
        assertEquals(62, item.percent)
        assertFalse(item.isError)
    }

    @Test
    fun `expanded line uses the 5-hour window even when weekly has less remaining`() {
        val weekly = UsageWindow("seven_day", "주간", Remaining.Percent(14.0), now.plusSeconds(3 * 24 * 3600))
        val usage = vendor(62.0, ProviderType.CLAUDE, now.plusSeconds(100 * 60), weekly)
        val item = NotificationContent.expandedItems(listOf(usage), now = now).single()
        assertEquals("5시간 62% 남음 · 1시간 40분 후 리셋", item.text)
        assertEquals(62, item.percent)
    }

    @Test
    fun `collapsed item uses the 5-hour window even when weekly has less remaining`() {
        val weekly = UsageWindow("seven_day", "주간", Remaining.Percent(14.0), null)
        val item = NotificationContent.collapsedItems(listOf(vendor(62.0, ProviderType.CLAUDE, null, weekly)), maxItems = 3).single()
        assertEquals("62% 남음", item.label)
        assertEquals(62, item.percent)
    }

    @Test
    fun `without a 5-hour window the least remaining window is used with its reset`() {
        val weeklyResetsAt = now.plusSeconds(3 * 24 * 3600)
        val weekly = UsageWindow("seven_day", "주간", Remaining.Percent(14.0), weeklyResetsAt)
        val monthly = UsageWindow("monthly", "월간", Remaining.Percent(50.0), now.plusSeconds(20 * 24 * 3600))
        val usage = VendorUsage("id", ProviderType.OPENAI, "acc", listOf(monthly, weekly))
        val item = NotificationContent.expandedItems(listOf(usage), now = now).single()
        assertEquals("주간 14% 남음 · 3일 후 리셋", item.text)
        assertEquals(14, item.percent)
    }

    @Test
    fun `expanded line shows the reset for GLM and xAI too`() {
        val glm = VendorUsage(
            "id", ProviderType.ZAI, "acc",
            listOf(
                UsageWindow("five_hour", "5시간", Remaining.Percent(98.0), now.plusSeconds((3 * 60 + 27) * 60)),
                UsageWindow("seven_day", "주간", Remaining.Percent(71.0), now.plusSeconds(4 * 24 * 3600))
            )
        )
        val xai = VendorUsage(
            "id", ProviderType.XAI, "acc",
            listOf(UsageWindow("weekly", "주간", Remaining.Percent(14.0), now.plusSeconds(26 * 3600)))
        )
        val items = NotificationContent.expandedItems(listOf(glm, xai), now = now)
        assertEquals("GLM", items[0].name)
        assertEquals("5시간 98% 남음 · 3시간 27분 후 리셋", items[0].text)
        assertEquals("xAI", items[1].name)
        assertEquals("주간 14% 남음 · 1일 후 리셋", items[1].text)
    }

    @Test
    fun `expanded line has no reset suffix when the representative has no reset`() {
        val usage = vendor(62.0, ProviderType.ZAI)
        assertEquals("5시간 62% 남음", NotificationContent.expandedItems(listOf(usage), now = now).single().text)
    }

    @Test
    fun `every expanded non-error line says 남음 and errors do not`() {
        val items = NotificationContent.expandedItems(
            listOf(vendor(62.0), balance(12.4), errored())
        )
        assertEquals(3, items.size)
        assertTrue(items[0].text.endsWith("남음"))
        assertTrue(items[0].text.contains("%"))
        assertTrue(items[1].text.endsWith("남음"))
        assertEquals("$12.40 남음", items[1].text)
        assertEquals("확인 필요", items[2].text)
        assertFalse(items[2].text.contains("남음"))
    }

    @Test
    fun `expanded error line`() {
        val item = NotificationContent.expandedItems(listOf(errored())).single()
        assertEquals("xAI", item.name)
        assertEquals("확인 필요", item.text)
        assertNull(item.percent)
        assertTrue(item.isError)
    }

    @Test
    fun `expanded balance line has no bar`() {
        val item = NotificationContent.expandedItems(listOf(balance(12.4))).single()
        assertEquals("OpenRouter", item.name)
        assertEquals("$12.40 남음", item.text)
        assertNull(item.percent)
    }

    private fun openRouterWithKeyLimit(
        credit: Double,
        keyRemaining: Double,
        keyLimitLabel: String = "키 한도 $20.00"
    ) = VendorUsage(
        "id", ProviderType.OPENROUTER, "acc",
        listOf(
            UsageWindow("credit", "credit", Remaining.Balance(credit)),
            UsageWindow("key", keyLimitLabel, Remaining.Balance(keyRemaining))
        )
    )

    @Test
    fun `openrouter notification shows smaller of balance and key remaining`() {
        val item = NotificationContent.expandedItems(listOf(openRouterWithKeyLimit(12.4, 7.6))).single()
        assertEquals("$7.60 남음", item.text)
        assertEquals(38, item.percent)
        assertEquals(Level.OK, item.level)
    }

    @Test
    fun `openrouter key bar uses warning and danger levels`() {
        val warn = NotificationContent.expandedItems(listOf(openRouterWithKeyLimit(100.0, 3.0))).single()
        assertEquals(15, warn.percent)
        assertEquals(Level.WARN, warn.level)
        val danger = NotificationContent.expandedItems(listOf(openRouterWithKeyLimit(100.0, 0.4))).single()
        assertEquals(2, danger.percent)
        assertEquals(Level.DANGER, danger.level)
    }

    @Test
    fun `openrouter without key limit has no bar`() {
        val usage = VendorUsage(
            "id", ProviderType.OPENROUTER, "acc",
            listOf(UsageWindow("credit", "credit", Remaining.Balance(12.4)))
        )
        val expanded = NotificationContent.expandedItems(listOf(usage)).single()
        assertEquals("$12.40 남음", expanded.text)
        assertNull(expanded.percent)
        val collapsed = NotificationContent.collapsedItems(listOf(usage), 3).single()
        assertEquals("$12.40", collapsed.label)
        assertNull(collapsed.percent)
    }

    @Test
    fun `primary window label is hidden`() {
        val v = VendorUsage(
            "id", ProviderType.OPENAI, "acc",
            listOf(UsageWindow("primary", "primary", Remaining.Percent(40.0)))
        )
        val item = NotificationContent.expandedItems(listOf(v)).single()
        assertEquals("40% 남음", item.text)
    }

    @Test
    fun `excluded fields never appear`() {
        val usages = listOf(
            vendor(62.0),
            balance(12.4),
            errored(),
            VendorUsage("id", ProviderType.XAI, "user@example.com", listOf(UsageWindow("w", "주간", Remaining.Percent(14.0))))
        )
        val texts = buildList {
            add(NotificationContent.textFallback(usages))
            NotificationContent.collapsedItems(usages, 3).forEach { add(it.label) }
            NotificationContent.expandedItems(usages).forEach { add(it.text) }
        }
        texts.forEach { text ->
            assertFalse(text.contains("@"))
            assertFalse(text.contains("사용량"))
            assertFalse(text.contains("갱신"))
            assertFalse(text.contains("Bot"))
            assertFalse(text.contains("sonnet"))
            assertFalse(text.contains("tok"))
        }
    }

    private fun antigravity(googlePercent: Double?, thirdPartyPercent: Double): VendorUsage = VendorUsage(
        "id", ProviderType.ANTIGRAVITY, "acc",
        listOfNotNull(
            googlePercent?.let {
                UsageWindow("google", "Google 모델", Remaining.Percent(it), now.plusSeconds(120 * 60))
            },
            UsageWindow("third_party", "타사 모델", Remaining.Percent(thirdPartyPercent), now.plusSeconds(30 * 60))
        )
    )

    @Test
    fun `antigravity expanded line shows only the Google window and its reset`() {
        val item = NotificationContent.expandedItems(listOf(antigravity(62.0, 10.0)), now = now).single()
        assertEquals("Antigravity", item.name)
        assertEquals("Google 62% 남음 · 2시간 후 리셋", item.text)
        assertEquals(62, item.percent)
        assertFalse(item.text.contains("타사"))
    }

    @Test
    fun `antigravity collapsed and text fallback use the Google window`() {
        val usage = antigravity(62.0, 10.0)
        val item = NotificationContent.collapsedItems(listOf(usage), maxItems = 3).single()
        assertEquals("62% 남음", item.label)
        assertEquals(62, item.percent)
        assertEquals("Antigravity 62%", NotificationContent.textFallback(listOf(usage)))
    }

    @Test
    fun `antigravity without a Google window falls back to its lowest window`() {
        val item = NotificationContent.expandedItems(listOf(antigravity(null, 10.0)), now = now).single()
        assertEquals("타사 10% 남음 · 30분 후 리셋", item.text)
    }
}
