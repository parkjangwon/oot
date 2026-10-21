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
    val error: VendorError? = null
) {
    val representative: UsageWindow?
        get() = windows.filter { it.remaining is Remaining.Percent }
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

private val amountRegex = Regex("""\d+(\.\d+)?""")

fun TokenStatus.toVendorUsage(): VendorUsage {
    if (isError) {
        return VendorUsage(
            accountId = accountId,
            providerType = provider,
            displayName = accountName,
            windows = emptyList(),
            error = VendorError.Unknown(errorMessage)
        )
    }
    val window = usagePercent?.let { used ->
        UsageWindow("primary", "primary", Remaining.Percent(100.0 - used))
    } ?: amountRegex.find(remainingFormatted)?.value?.toDoubleOrNull()?.let { amount ->
        UsageWindow("primary", remainingFormatted, Remaining.Balance(amount))
    } ?: UsageWindow("primary", "primary", Remaining.Percent(100.0))
    return VendorUsage(
        accountId = accountId,
        providerType = provider,
        displayName = accountName,
        windows = listOf(window)
    )
}
