package org.parkjw.apps.oot.data.remote

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * A blank custom endpoint means the provider default. Anything else must be an https URL
 * without embedded credentials.
 */
fun isAllowedCustomEndpoint(raw: String): Boolean {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return true
    val url = trimmed.toHttpUrlOrNull() ?: return false
    return url.scheme == "https" &&
        url.host.isNotEmpty() &&
        url.username.isEmpty() &&
        url.password.isEmpty()
}
