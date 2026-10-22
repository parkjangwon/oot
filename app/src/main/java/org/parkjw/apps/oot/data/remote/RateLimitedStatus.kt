package org.parkjw.apps.oot.data.remote

import org.parkjw.apps.oot.data.model.ProviderAccount
import org.parkjw.apps.oot.data.model.ProviderType
import org.parkjw.apps.oot.data.model.TokenStatus

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
            remainingFormatted = "${cached.remainingFormatted} (이전 값)",
            statusSummary = "${cached.statusSummary} (이전값)",
            isWarning = true,
            isError = false,
            errorMessage = "조회 제한(429) - 마지막 확인 값 표시",
            lastUpdated = nowMs,
            isRateLimited = true,
            rateLimitedRetryAfterMs = retryAfterMs,
        )
    } else {
        TokenStatus(
            accountId = account.id,
            provider = provider,
            accountName = account.name,
            remainingFormatted = "요청 제한 (429)",
            statusSummary = "확인 필요",
            isError = true,
            errorMessage = "사용량 조회 제한(429) - 다음 주기에 재시도",
            lastUpdated = nowMs,
            isRateLimited = true,
            rateLimitedRetryAfterMs = retryAfterMs,
        )
    }
}
