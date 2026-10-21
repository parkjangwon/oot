package org.parkjw.apps.oot.notification

import org.parkjw.apps.oot.data.model.Level
import org.parkjw.apps.oot.data.model.ProviderType
import org.parkjw.apps.oot.data.model.Remaining
import org.parkjw.apps.oot.data.model.UsageWindow
import org.parkjw.apps.oot.data.model.VendorUsage
import org.parkjw.apps.oot.data.model.level
import org.parkjw.apps.oot.data.model.sortedByRemaining
import org.parkjw.apps.oot.data.remote.ANTIGRAVITY_GOOGLE_WINDOW_ID
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
        ProviderType.OPENROUTER -> "OpenRouter"
        ProviderType.CLAUDE -> "Claude"
        ProviderType.OPENAI -> "OpenAI"
        ProviderType.XAI -> "xAI"
        ProviderType.ZAI -> "GLM"
        ProviderType.ANTIGRAVITY -> "Antigravity"
        ProviderType.DEEPSEEK -> "DeepSeek"
        ProviderType.OLLAMA_CLOUD -> "Ollama"
        ProviderType.OPENCODE_GO -> "OpenCode Go"
    }

    fun mark(provider: ProviderType): String = when (provider) {
        ProviderType.OPENROUTER -> "R"
        ProviderType.CLAUDE -> "Cl"
        ProviderType.OPENAI -> "O"
        ProviderType.XAI -> "X"
        ProviderType.ZAI -> "G"
        ProviderType.ANTIGRAVITY -> "A"
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

    // Labels stay short here: the "Google 모델" window reads "Google".
    private fun windowLabel(window: UsageWindow): String =
        if (window.label == "primary" || window.label.isBlank()) "" else "${window.label.removeSuffix(" 모델")} "

    // Antigravity shows only its Google-model window, whatever the other window holds.
    private fun notificationWindow(usage: VendorUsage): UsageWindow? =
        if (usage.providerType == ProviderType.ANTIGRAVITY) {
            usage.windows.firstOrNull { it.id == ANTIGRAVITY_GOOGLE_WINDOW_ID } ?: usage.representative
        } else {
            usage.representative
        }

    // Errored vendors first ("확인 필요"), then lowest remaining ascending.
    fun collapsedItems(usages: List<VendorUsage>, maxItems: Int): List<CollapsedItem> =
        usages.sortedByRemaining().take(maxItems.coerceAtLeast(0)).map { usage ->
            when (val remaining = notificationWindow(usage)?.remaining) {
                null ->
                    CollapsedItem(mark(usage.providerType), "${shortName(usage.providerType)} 확인 필요", null, true, Level.OK)
                is Remaining.Balance ->
                    CollapsedItem(mark(usage.providerType), formatBalance(remaining.amountLeft, remaining.currency), null, false, Level.OK)
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
            val representative = notificationWindow(usage)
                ?: return@map ExpandedItem(itemMark, name, "확인 필요", null, true, Level.OK)
            when (val remaining = representative.remaining) {
                is Remaining.Balance ->
                    ExpandedItem(itemMark, name, "${formatBalance(remaining.amountLeft, remaining.currency)} 남음", null, false, Level.OK)
                is Remaining.Percent -> {
                    // Representative window only: the one with the least remaining, plus its reset.
                    val text = "${windowLabel(representative)}${formatPercent(remaining)} 남음"
                    val reset = representative.resetsAt
                        ?.let { formatReset(it, now) }
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { " · ${it} 리셋" }
                        ?: ""
                    ExpandedItem(
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

    // Text-only fallback (option C): "Claude 62% · GLM 98% · OpenRouter $12.40"
    fun textFallback(usages: List<VendorUsage>): String =
        usages.sortedByRemaining().joinToString(" · ") { usage ->
            val label = when (val remaining = notificationWindow(usage)?.remaining) {
                is Remaining.Balance -> formatBalance(remaining.amountLeft, remaining.currency)
                is Remaining.Percent -> formatPercent(remaining)
                null -> "확인 필요"
            }
            "${shortName(usage.providerType)} $label"
        }
}
