package org.parkjw.apps.oot.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class TokenStatus(
    val accountId: String,
    val provider: ProviderType,
    val accountName: String,
    val remainingFormatted: String,
    val totalLimitFormatted: String? = null,
    val usagePercent: Float? = null, // 0.0 ~ 100.0
    val resetTimeFormatted: String? = null,
    val statusSummary: String, // Short ticker for notification (e.g. "$12.45 남음")
    val isWarning: Boolean = false,
    val isCritical: Boolean = false,
    val isError: Boolean = false,
    val errorMessage: String? = null,
    val lastUpdated: Long = System.currentTimeMillis(),
    /** Epoch ms of the last successful fetch these values came from; null = fresh. */
    val staleSince: Long? = null,
    /** True when the vendor responded with HTTP 429. */
    val isRateLimited: Boolean = false,
    /** Parsed Retry-After delay in ms, if the 429 response included one. */
    val rateLimitedRetryAfterMs: Long? = null,
    // Real usage windows for providers that report them (e.g. Claude 5-hour/weekly). Not persisted.
    @Transient val windows: List<UsageWindow> = emptyList()
)

/** These values were carried over from an earlier successful fetch; [lastUpdated] is its time. */
fun TokenStatus.asStale(): TokenStatus =
    if (staleSince != null) this else copy(staleSince = lastUpdated)
