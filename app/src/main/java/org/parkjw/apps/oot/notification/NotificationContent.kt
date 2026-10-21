package org.parkjw.apps.oot.notification

import org.parkjw.apps.oot.data.model.Level
import org.parkjw.apps.oot.data.model.ProviderType
import org.parkjw.apps.oot.data.model.Remaining
import org.parkjw.apps.oot.data.model.UsageWindow
import org.parkjw.apps.oot.data.model.VendorUsage
import org.parkjw.apps.oot.data.model.level
import org.parkjw.apps.oot.data.model.sortedByRemaining
import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlin.math.roundToInt

// Pure builders for the persistent notification (issue #5).
// No Android types here so everything is unit-testable on the JVM.

object NotificationContent {

    // User-invisible switch: set true to force the text-only fallback (option C)
    // on every device, e.g. when debugging custom RemoteViews issues.
    const val FORCE_TEXT_ONLY = false

    data class CollapsedItem(
        val mark: String,
        val label: String,
        val percent: Int?,
        val isError: Boolean,
        val level: Level
    )

    data class ExpandedItem(
        val mark: String,
        val name: String,
        val text: String,
        val percent: Int?,
        val isError: Boolean,
        val level: Level
    )

    fun shortName(provider: ProviderType): String = when (provider) {
        ProviderType.OPENROUTER -> "OR"
        ProviderType.CLAUDE -> "Claude"
        ProviderType.OPENAI -> "OpenAI"
        ProviderType.XAI -> "xAI"
        ProviderType.CURSOR -> "Cursor"
        ProviderType.ZAI -> "GLM"
        ProviderType.ANTIGRAVITY -> "Antigravity"
    }

    fun mark(provider: ProviderType): String = when (provider) {
        ProviderType.OPENROUTER -> "R"
        ProviderType.CLAUDE -> "Cl"
        ProviderType.OPENAI -> "O"
        ProviderType.XAI -> "X"
        ProviderType.CURSOR -> "Cu"
        ProviderType.ZAI -> "G"
        ProviderType.ANTIGRAVITY -> "A"
    }

    fun formatBalance(amount: Double): String = String.format(Locale.US, "$%.2f", amount)

    fun formatPercent(remaining: Remaining.Percent): String =
        "${remaining.percentLeft.roundToInt().coerceIn(0, 100)}%"

    fun formatReset(resetsAt: Instant, now: Instant): String {
        val totalMinutes = Duration.between(now, resetsAt).toMinutes()
        if (totalMinutes <= 0) return ""
        if (totalMinutes >= 1440) return "${totalMinutes / 1440}일 후"
        if (totalMinutes >= 60) {
            val hours = totalMinutes / 60
            val minutes = totalMinutes % 60
            return if (minutes == 0L) "${hours}시간 후" else "${hours}시간 ${minutes}분 후"
        }
        return "${totalMinutes}분 후"
    }

    private fun windowLabel(window: UsageWindow): String =
        if (window.label == "primary" || window.label.isBlank()) "" else "${window.label} "

    // Errored vendors first ("확인 필요"), then lowest remaining ascending.
    fun collapsedItems(usages: List<VendorUsage>, maxItems: Int): List<CollapsedItem> =
        usages.sortedByRemaining().take(maxItems.coerceAtLeast(0)).map { usage ->
            when (val remaining = usage.representative?.remaining) {
                null ->
                    CollapsedItem(mark(usage.providerType), "${shortName(usage.providerType)} 확인 필요", null, true, Level.OK)
                is Remaining.Balance ->
                    CollapsedItem(mark(usage.providerType), formatBalance(remaining.amountLeft), null, false, Level.OK)
                is Remaining.Percent ->
                    CollapsedItem(
                        mark(usage.providerType),
                        "${formatPercent(remaining)} 남음",
                        remaining.percentLeft.roundToInt().coerceIn(0, 100),
                        false,
                        remaining.level
                    )
            }
        }

    // One line per registered vendor, registration order.
    fun expandedItems(usages: List<VendorUsage>, now: Instant = Instant.now()): List<ExpandedItem> =
        usages.map { usage ->
            val itemMark = mark(usage.providerType)
            val name = shortName(usage.providerType)
            if (usage.error != null) {
                return@map ExpandedItem(itemMark, name, "확인 필요", null, true, Level.OK)
            }
            val representative = usage.representative
                ?: return@map ExpandedItem(itemMark, name, "확인 필요", null, true, Level.OK)
            when (val remaining = representative.remaining) {
                is Remaining.Balance ->
                    ExpandedItem(itemMark, name, formatBalance(remaining.amountLeft), null, false, Level.OK)
                is Remaining.Percent -> {
                    val parts = usage.windows
                        .filter { it.remaining is Remaining.Percent }
                        .map { "${windowLabel(it)}${formatPercent(it.remaining as Remaining.Percent)}" }
                    val reset = representative.resetsAt
                        ?.let { formatReset(it, now) }
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { " · ${it} 리셋" }
                        ?: ""
                    ExpandedItem(
                        itemMark,
                        name,
                        parts.joinToString(" · ") + reset,
                        remaining.percentLeft.roundToInt().coerceIn(0, 100),
                        false,
                        remaining.level
                    )
                }
            }
        }

    // Text-only fallback (option C): "Claude 62% · GLM 98% · OR $12.40"
    fun textFallback(usages: List<VendorUsage>): String =
        usages.sortedByRemaining().joinToString(" · ") { usage ->
            val label = when (val remaining = usage.representative?.remaining) {
                is Remaining.Balance -> formatBalance(remaining.amountLeft)
                is Remaining.Percent -> formatPercent(remaining)
                null -> "확인 필요"
            }
            "${shortName(usage.providerType)} $label"
        }
}
