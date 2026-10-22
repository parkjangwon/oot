package org.parkjw.apps.oot.data.model

import java.time.Instant

sealed interface Remaining {
    data class Percent(val percentLeft: Double) : Remaining
    data class Balance(val amountLeft: Double, val currency: String = "USD") : Remaining
}

enum class Level { OK, WARN, DANGER }

val Remaining.level: Level
    get() = when (this) {
        is Remaining.Balance -> Level.OK
        is Remaining.Percent -> when {
            percentLeft <= 5.0 -> Level.DANGER
            percentLeft <= 20.0 -> Level.WARN
            else -> Level.OK
        }
    }

data class UsageWindow(
    val id: String,
    val label: String,
    val remaining: Remaining,
    val resetsAt: Instant? = null
)

sealed interface VendorError {
    data object Unauthorized : VendorError
    data object ScopeMissing : VendorError
    data object RateLimited : VendorError
    data object Network : VendorError
    data object Parse : VendorError
    data class Http(val code: Int) : VendorError
    data class Unknown(val message: String? = null) : VendorError
}

data class VendorUsage(
    val accountId: String,
    val providerType: ProviderType,
    val displayName: String,
    val windows: List<UsageWindow>,
    val error: VendorError? = null,
    /** The last successful fetch these values came from; null = fresh. */
    val staleSince: Instant? = null
) {
    // The notification shows the 5-hour window when the vendor has one, else the least-remaining window.
    val representative: UsageWindow?
        get() = windows.firstOrNull { it.id == "five_hour" && it.remaining is Remaining.Percent }
            ?: windows.filter { it.remaining is Remaining.Percent }
                .minByOrNull { (it.remaining as Remaining.Percent).percentLeft }
            ?: windows.firstOrNull { it.remaining is Remaining.Balance }
}

private fun Remaining.sortKey(): Double = when (this) {
    is Remaining.Percent -> percentLeft
    is Remaining.Balance -> Double.MAX_VALUE
}

fun List<VendorUsage>.sortedByRemaining(): List<VendorUsage> =
    sortedWith(
        compareBy(
            { it.error == null },
            { it.representative?.remaining?.sortKey() ?: Double.MAX_VALUE }
        )
    )

// Only a currency-marked number is a balance; bare numbers are request/token counts.
private val amountRegex = Regex("""[$¥€£]\s?([\d,]+(?:\.\d+)?)""")

// A stale snapshot must not keep showing a window whose reset has passed since it was converted.
fun VendorUsage.withStaleRollover(now: Instant): VendorUsage {
    if (staleSince == null) return this
    val shown = windows.filter { it.resetsAt == null || it.resetsAt > now }
    if (shown.size == windows.size) return this
    return copy(
        windows = shown,
        error = if (shown.isEmpty()) error ?: VendorError.RateLimited else error
    )
}

fun TokenStatus.toVendorUsage(now: Instant = Instant.now()): VendorUsage {
    val staleAt = staleSince?.let { Instant.ofEpochMilli(it) }
    if (isError) {
        return VendorUsage(
            accountId = accountId,
            providerType = provider,
            displayName = accountName,
            windows = emptyList(),
            error = VendorError.Unknown(errorMessage),
            staleSince = staleAt
        )
    }
    // Providers that report real windows (e.g. Claude 5-hour/weekly with resetsAt) carry them directly.
    val usageWindows = windows.ifEmpty {
        listOf(
            usagePercent?.let { used ->
                UsageWindow("primary", "primary", Remaining.Percent(100.0 - used))
            } ?: amountRegex.find(remainingFormatted)?.groupValues?.get(1)
                ?.replace(",", "")?.toDoubleOrNull()?.let { amount ->
                    UsageWindow("primary", remainingFormatted, Remaining.Balance(amount))
                } ?: UsageWindow("primary", "primary", Remaining.Percent(100.0))
        )
    }
    // A stale snapshot must not keep showing a window whose reset already passed: its remaining is not current.
    val shownWindows = if (staleAt == null) {
        usageWindows
    } else {
        usageWindows.filter { it.resetsAt == null || it.resetsAt > now }
    }
    val error = if (staleAt != null && shownWindows.isEmpty()) {
        if (isRateLimited) VendorError.RateLimited else VendorError.Unknown(errorMessage)
    } else {
        null
    }
    return VendorUsage(
        accountId = accountId,
        providerType = provider,
        displayName = accountName,
        windows = shownWindows,
        error = error,
        staleSince = staleAt
    )
}
