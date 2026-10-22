package org.parkjw.apps.oot.data.remote

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.parkjw.apps.oot.R
import org.parkjw.apps.oot.data.model.ProviderAccount
import org.parkjw.apps.oot.data.model.TokenStatus
import org.parkjw.apps.oot.i18n.AppText

/**
 * A blank custom endpoint means the provider default. Anything else must be an https URL
 * without embedded credentials, query or fragment.
 */
fun isAllowedCustomEndpoint(raw: String): Boolean {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return true
    val url = trimmed.toHttpUrlOrNull() ?: return false
    return url.scheme == "https" &&
        url.host.isNotEmpty() &&
        url.username.isEmpty() &&
        url.password.isEmpty() &&
        url.query == null &&
        url.fragment == null
}

/**
 * Request-time endpoint policy: blank custom endpoint falls back to [default], a disallowed
 * custom endpoint returns null so callers can refuse the request.
 */
fun resolveBaseUrl(customEndpoint: String?, default: String): String? {
    val custom = customEndpoint?.trim().orEmpty()
    if (custom.isEmpty()) return default
    return custom.takeIf { isAllowedCustomEndpoint(it) }
}

/** Error status for an account whose stored custom endpoint fails the request-time policy. */
fun customEndpointErrorStatus(account: ProviderAccount): TokenStatus = TokenStatus(
    accountId = account.id,
    provider = account.provider,
    accountName = account.name,
    remainingFormatted = AppText.get(R.string.error_custom_endpoint_not_https),
    statusSummary = AppText.get(R.string.status_needs_check),
    isError = true,
    errorMessage = AppText.get(R.string.error_custom_endpoint_not_https)
)
