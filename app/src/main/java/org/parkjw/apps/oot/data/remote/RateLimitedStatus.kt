package org.parkjw.apps.oot.data.remote

import org.parkjw.apps.oot.R
import org.parkjw.apps.oot.data.model.ProviderAccount
import org.parkjw.apps.oot.data.model.ProviderType
import org.parkjw.apps.oot.data.model.TokenStatus
import org.parkjw.apps.oot.i18n.AppText

/** Builds a 429 TokenStatus, keeping [cached] when available. */
fun rateLimitedTokenStatus(
    account: ProviderAccount,
    provider: ProviderType = account.provider,
    retryAfterHeader: String?,
    cached: TokenStatus? = null,
    nowMs: Long = System.currentTimeMillis(),
): TokenStatus {
    val retryAfterMs = parseRetryAfterMs(retryAfterHeader, nowMs)
    return if (cached != null && !cached.isError) {
        cached.copy(
            remainingFormatted = "${cached.remainingFormatted} ${AppText.get(R.string.data_previous_value)}",
            statusSummary = "${cached.statusSummary} ${AppText.get(R.string.data_previous_value_short)}",
            isWarning = true,
            isError = false,
            errorMessage = AppText.get(R.string.data_rate_limited_cached),
            // Keep the time of the fetch these values came from; the card marks them stale.
            staleSince = cached.staleSince ?: cached.lastUpdated,
            isRateLimited = true,
            rateLimitedRetryAfterMs = retryAfterMs,
        )
    } else {
        TokenStatus(
            accountId = account.id,
            provider = provider,
            accountName = account.name,
            remainingFormatted = AppText.get(R.string.data_rate_limited_short),
            statusSummary = AppText.get(R.string.status_needs_check),
            isError = true,
            errorMessage = AppText.get(R.string.data_rate_limited_retry),
            lastUpdated = nowMs,
            isRateLimited = true,
            rateLimitedRetryAfterMs = retryAfterMs,
        )
    }
}
