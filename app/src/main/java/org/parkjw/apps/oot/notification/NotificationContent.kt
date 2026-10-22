package org.parkjw.apps.oot.notification

import org.parkjw.apps.oot.data.model.Level
import org.parkjw.apps.oot.data.model.ProviderType
import org.parkjw.apps.oot.data.model.Remaining
import org.parkjw.apps.oot.data.model.UsageWindow
import org.parkjw.apps.oot.data.model.VendorUsage
import org.parkjw.apps.oot.data.model.level
import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlin.math.roundToInt

// Pure builders for the persistent notification (issue #5).
// No Android types here so everything is unit-testable on the JVM; text comes in through [NotificationStrings].

/** Localized copy for the notification, read from string resources by the caller (see notificationStrings()). */
data class NotificationStrings(
    val needsCheck: String,
    /** "%1$s 확인 필요": vendor short name. */
    val vendorNeedsCheck: String,
    /** "%1$s 남음": amount or percent. */
    val left: String,
    val window5h: String,
    val window7d: String,
    val emptyNoProviders: String,
    val emptyAllHidden: String,
    /** "%1$d일 후" */
    val inDays: String,
    /** "%1$d일 %2$d시간 후" */
    val inDaysHours: String,
    /** "%1$d시간 후" */
    val inHours: String,
    /** "%1$d시간 %2$d분 후" */
    val inHoursMinutes: String,
    /** "%1$d분 후" */
    val inMinutes: String,
) {
    fun leftOf(value: String): String = left.format(value)
    fun vendorNeedsCheck(name: String): String = vendorNeedsCheck.format(name)
}

object NotificationContent {

    // User-invisible switch: set true to force the text-only fallback (option C)
    // on every device, e.g. when debugging custom RemoteViews issues.
    const val FORCE_TEXT_ONLY = false

    data class CollapsedItem(
        val provider: ProviderType,
        val mark: String,
        val label: String,
        val percent: Int?,
        val isError: Boolean,
        val level: Level
    )

    data class ExpandedItem(
        val provider: ProviderType,
        val mark: String,
        val name: String,
        val text: String,
        val percent: Int?,
        val isError: Boolean,
        val level: Level
    )

    fun shortName(provider: ProviderType): String = when (provider) {
        ProviderType.OPENROUTER -> "OpenRouter"
        ProviderType.CLAUDE -> "Claude"
        ProviderType.OPENAI -> "OpenAI"
        ProviderType.XAI -> "xAI"
        ProviderType.ZAI -> "GLM"
        ProviderType.DEEPSEEK -> "DeepSeek"
        ProviderType.OLLAMA_CLOUD -> "Ollama"
        ProviderType.OPENCODE_GO -> "OpenCode Go"
    }

    fun mark(provider: ProviderType): String = when (provider) {
        ProviderType.OPENROUTER -> "R"
        ProviderType.CLAUDE -> "Cl"
        ProviderType.OPENAI -> "O"
        ProviderType.XAI -> "X"
        ProviderType.ZAI -> "Z"
        ProviderType.DEEPSEEK -> "D"
        ProviderType.OLLAMA_CLOUD -> "OL"
        ProviderType.OPENCODE_GO -> "OG"
    }

    fun currencySymbol(currency: String): String = when (currency.uppercase()) {
        "CNY" -> "¥"
        else -> "$"
    }

    fun formatBalance(amount: Double, currency: String = "USD"): String =
        "${currencySymbol(currency)}${String.format(Locale.US, "%.2f", amount)}"

    fun formatPercent(remaining: Remaining.Percent): String =
        "${remaining.percentLeft.roundToInt().coerceIn(0, 100)}%"

    fun formatReset(resetsAt: Instant, now: Instant, strings: NotificationStrings): String {
        val totalMinutes = Duration.between(now, resetsAt).toMinutes()
        if (totalMinutes <= 0) return ""
        if (totalMinutes >= 1440) return strings.inDays.format(totalMinutes / 1440)
        if (totalMinutes >= 60) {
            val hours = totalMinutes / 60
            val minutes = totalMinutes % 60
            return if (minutes == 0L) strings.inHours.format(hours) else strings.inHoursMinutes.format(hours, minutes)
        }
        return strings.inMinutes.format(totalMinutes)
    }

    // Notification reset text (#58): days keep their hours, e.g. "2일 3시간 후"; shorter spans as formatReset.
    fun formatNotificationReset(resetsAt: Instant, now: Instant, strings: NotificationStrings): String {
        val totalMinutes = Duration.between(now, resetsAt).toMinutes()
        if (totalMinutes < 1440) return formatReset(resetsAt, now, strings)
        val days = totalMinutes / 1440
        val hours = (totalMinutes % 1440) / 60
        return if (hours == 0L) strings.inDays.format(days) else strings.inDaysHours.format(days, hours)
    }

    // Labels stay short here: the 5-hour window reads "5H" and the weekly one "7D" (issue #58, notification
    // only); other named windows (e.g. a model group) use their label without a trailing " model" word.
    private fun windowLabel(window: UsageWindow, strings: NotificationStrings): String =
        when {
            window.id == "five_hour" -> "${strings.window5h} "
            window.id == "seven_day" || window.id == "weekly" -> "${strings.window7d} "
            window.label == "primary" || window.label.isBlank() -> ""
            else -> "${window.label.substringBefore(' ')} "
        }

    // OpenRouter card view: amount = min(account balance, key remaining);
    // bar percent only when the key has a limit (parsed back from the "$N.NN" label).
    data class OpenRouterView(val amountLeft: Double, val currency: String, val keyPercent: Int?)

    fun keyLimitAmount(label: String): Double? =
        Regex("""\d+(\.\d+)?""").find(label)?.value?.toDoubleOrNull()

    fun openRouterView(usage: VendorUsage): OpenRouterView? {
        if (usage.providerType != ProviderType.OPENROUTER) return null
        val credit = usage.windows.firstOrNull { it.id == "credit" }?.remaining as? Remaining.Balance
            ?: return null
        val keyWindow = usage.windows.firstOrNull { it.id == "key" }
        val key = keyWindow?.remaining as? Remaining.Balance
        val amount = key?.let { minOf(credit.amountLeft, it.amountLeft) } ?: credit.amountLeft
        val limit = keyWindow?.let { keyLimitAmount(it.label) }
        val percent = if (key != null && limit != null && limit > 0) {
            ((key.amountLeft / limit) * 100).roundToInt().coerceIn(0, 100)
        } else {
            null
        }
        return OpenRouterView(amount, credit.currency, percent)
    }

    private fun openRouterLevel(percent: Int?): Level =
        percent?.let { Remaining.Percent(it.toDouble()).level } ?: Level.OK

    // Accounts switched off in Settings stay on the dashboard but leave every notification form; order is kept.
    // Shown when nothing is left to list: nothing registered, or every provider switched off in "Show in notification".
    fun emptyStateText(registeredCount: Int, strings: NotificationStrings): String =
        if (registeredCount == 0) strings.emptyNoProviders else strings.emptyAllHidden

    fun visibleUsages(usages: List<VendorUsage>, hiddenAccountIds: Set<String>): List<VendorUsage> =
        usages.filter { it.accountId !in hiddenAccountIds }

    // User order as given; errored vendors stay in place ("check needed").
    fun collapsedItems(usages: List<VendorUsage>, maxItems: Int, strings: NotificationStrings): List<CollapsedItem> =
        usages.take(maxItems.coerceAtLeast(0)).map { usage ->
            val p = usage.providerType
            openRouterView(usage)?.let { or ->
                return@map CollapsedItem(
                    p,
                    mark(p),
                    formatBalance(or.amountLeft, or.currency),
                    or.keyPercent,
                    false,
                    openRouterLevel(or.keyPercent)
                )
            }
            when (val remaining = usage.representative?.remaining) {
                null ->
                    CollapsedItem(p, mark(p), strings.vendorNeedsCheck(shortName(p)), null, true, Level.OK)
                is Remaining.Balance ->
                    CollapsedItem(p, mark(p), formatBalance(remaining.amountLeft, remaining.currency), null, false, Level.OK)
                is Remaining.Percent ->
                    CollapsedItem(
                        p,
                        mark(p),
                        strings.leftOf(formatPercent(remaining)),
                        remaining.percentLeft.roundToInt().coerceIn(0, 100),
                        false,
                        remaining.level
                    )
            }
        }

    // One line per registered vendor, user order.
    fun expandedItems(
        usages: List<VendorUsage>,
        strings: NotificationStrings,
        now: Instant = Instant.now(),
    ): List<ExpandedItem> =
        usages.map { usage ->
            val p = usage.providerType
            val itemMark = mark(p)
            val name = shortName(p)
            if (usage.error != null) {
                return@map ExpandedItem(p, itemMark, name, strings.needsCheck, null, true, Level.OK)
            }
            val representative = usage.representative
                ?: return@map ExpandedItem(p, itemMark, name, strings.needsCheck, null, true, Level.OK)
            openRouterView(usage)?.let { or ->
                return@map ExpandedItem(
                    p,
                    itemMark,
                    name,
                    strings.leftOf(formatBalance(or.amountLeft, or.currency)),
                    or.keyPercent,
                    false,
                    openRouterLevel(or.keyPercent)
                )
            }
            when (val remaining = representative.remaining) {
                is Remaining.Balance ->
                    ExpandedItem(p, itemMark, name, strings.leftOf(formatBalance(remaining.amountLeft, remaining.currency)), null, false, Level.OK)
                is Remaining.Percent -> {
                    // Representative window only (5-hour when present, else least remaining), plus its reset.
                    val text = windowLabel(representative, strings) + strings.leftOf(formatPercent(remaining))
                    val reset = representative.resetsAt
                        ?.let { formatNotificationReset(it, now, strings) }
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { " · $it" }
                        ?: ""
                    ExpandedItem(
                        p,
                        itemMark,
                        name,
                        text + reset,
                        remaining.percentLeft.roundToInt().coerceIn(0, 100),
                        false,
                        remaining.level
                    )
                }
            }
        }

    // Text-only fallback (option C): user order, e.g. "Claude 62% · GLM 98% · OpenRouter $12.40"
    fun textFallback(usages: List<VendorUsage>, strings: NotificationStrings): String =
        usages.joinToString(" · ") { usage ->
            val openRouter = openRouterView(usage)
            val label = when {
                openRouter != null -> formatBalance(openRouter.amountLeft, openRouter.currency)
                else -> when (val remaining = usage.representative?.remaining) {
                    is Remaining.Balance -> formatBalance(remaining.amountLeft, remaining.currency)
                    is Remaining.Percent -> formatPercent(remaining)
                    null -> strings.needsCheck
                }
            }
            "${shortName(usage.providerType)} $label"
        }
}
