package org.parkjw.apps.oot.notification

import org.parkjw.apps.oot.data.model.Level
import org.parkjw.apps.oot.data.model.ProviderType
import org.parkjw.apps.oot.data.model.Remaining
import org.parkjw.apps.oot.data.model.UsageWindow
import org.parkjw.apps.oot.data.model.VendorUsage
import org.parkjw.apps.oot.data.model.level
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

    // OpenRouter card view: amount = min(account balance, key remaining);
    // bar percent only when the key has a limit (parsed back from the "키 한도 $N.NN" label).
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
    const val EMPTY_NO_PROVIDERS = "등록된 프로바이더가 없어요. 앱에서 추가해 주세요."
    const val EMPTY_ALL_HIDDEN = "알림에 표시할 프로바이더를 모두 껐어요. 설정에서 켜 주세요."

    // Shown when nothing is left to list: nothing registered, or every provider switched off in "알림에 표시".
    fun emptyStateText(registeredCount: Int): String =
        if (registeredCount == 0) EMPTY_NO_PROVIDERS else EMPTY_ALL_HIDDEN

    fun visibleUsages(usages: List<VendorUsage>, hiddenAccountIds: Set<String>): List<VendorUsage> =
        usages.filter { it.accountId !in hiddenAccountIds }

    // User order as given; errored vendors stay in place ("확인 필요").
    fun collapsedItems(usages: List<VendorUsage>, maxItems: Int): List<CollapsedItem> =
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
            when (val remaining = notificationWindow(usage)?.remaining) {
                null ->
                    CollapsedItem(p, mark(p), "${shortName(p)} 확인 필요", null, true, Level.OK)
                is Remaining.Balance ->
                    CollapsedItem(p, mark(p), formatBalance(remaining.amountLeft, remaining.currency), null, false, Level.OK)
                is Remaining.Percent ->
                    CollapsedItem(
                        p,
                        mark(p),
                        "${formatPercent(remaining)} 남음",
                        remaining.percentLeft.roundToInt().coerceIn(0, 100),
                        false,
                        remaining.level
                    )
            }
        }

    // One line per registered vendor, user order.
    fun expandedItems(usages: List<VendorUsage>, now: Instant = Instant.now()): List<ExpandedItem> =
        usages.map { usage ->
            val p = usage.providerType
            val itemMark = mark(p)
            val name = shortName(p)
            if (usage.error != null) {
                return@map ExpandedItem(p, itemMark, name, "확인 필요", null, true, Level.OK)
            }
            val representative = notificationWindow(usage)
                ?: return@map ExpandedItem(p, itemMark, name, "확인 필요", null, true, Level.OK)
            openRouterView(usage)?.let { or ->
                return@map ExpandedItem(
                    p,
                    itemMark,
                    name,
                    "${formatBalance(or.amountLeft, or.currency)} 남음",
                    or.keyPercent,
                    false,
                    openRouterLevel(or.keyPercent)
                )
            }
            when (val remaining = representative.remaining) {
                is Remaining.Balance ->
                    ExpandedItem(p, itemMark, name, "${formatBalance(remaining.amountLeft, remaining.currency)} 남음", null, false, Level.OK)
                is Remaining.Percent -> {
                    // Representative window only (5-hour when present, else least remaining), plus its reset.
                    val text = "${windowLabel(representative)}${formatPercent(remaining)} 남음"
                    val reset = representative.resetsAt
                        ?.let { formatReset(it, now) }
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { " · ${it} 리셋" }
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
    fun textFallback(usages: List<VendorUsage>): String =
        usages.joinToString(" · ") { usage ->
            val openRouter = openRouterView(usage)
            val label = when {
                openRouter != null -> formatBalance(openRouter.amountLeft, openRouter.currency)
                else -> when (val remaining = notificationWindow(usage)?.remaining) {
                    is Remaining.Balance -> formatBalance(remaining.amountLeft, remaining.currency)
                    is Remaining.Percent -> formatPercent(remaining)
                    null -> "확인 필요"
                }
            }
            "${shortName(usage.providerType)} $label"
        }
}
