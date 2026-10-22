package org.parkjw.apps.oot.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.parkjw.apps.oot.R
import org.parkjw.apps.oot.data.model.ProviderAccount
import org.parkjw.apps.oot.data.model.TokenStatus
import org.parkjw.apps.oot.data.model.VendorError
import org.parkjw.apps.oot.i18n.AppText
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.util.Locale

data class ClaudeWindowUsage(
    val id: String,
    val label: String,
    val utilization: Double,
    val resetsAt: Instant?
)

// Pure mapping of /api/oauth/usage HTTP outcomes (401 handled by refresh+retry in ClaudeApi).
fun mapClaudeHttpError(code: Int): VendorError = when (code) {
    403 -> VendorError.ScopeMissing
    429 -> VendorError.RateLimited
    else -> VendorError.Http(code)
}

fun claudeErrorText(error: VendorError): String = when (error) {
    VendorError.Unauthorized -> AppText.get(R.string.data_error_sign_in_again)
    VendorError.ScopeMissing -> AppText.get(R.string.data_error_sign_in_again_scope)
    VendorError.RateLimited -> AppText.get(R.string.data_error_rate_limited)
    VendorError.Network -> AppText.get(R.string.data_error_network)
    VendorError.Parse -> AppText.get(R.string.data_error_parse)
    is VendorError.Http -> AppText.get(R.string.data_error_http, error.code)
    is VendorError.Unknown -> error.message ?: AppText.get(R.string.data_error_generic)
}

fun parseClaudeInstant(raw: String?): Instant? {
    if (raw.isNullOrBlank()) return null
    return try {
        Instant.parse(raw)
    } catch (e: DateTimeParseException) {
        try {
            OffsetDateTime.parse(raw).toInstant()
        } catch (e: DateTimeParseException) {
            null
        }
    }
}

// Parses the /api/oauth/usage body into the 5-hour and weekly windows.
// Returns null when the body is not the expected JSON shape (parse failure).
fun parseClaudeUsage(body: String, json: Json = Json): List<ClaudeWindowUsage>? {
    val root = try { json.parseToJsonElement(body).jsonObject } catch (e: Exception) { return null }
    val fiveHour = root["five_hour"]?.jsonObject ?: return null
    val sevenDay = root["seven_day"]?.jsonObject ?: return null
    val fiveUtil = fiveHour["utilization"]?.jsonPrimitive?.doubleOrNull ?: return null
    val weekUtil = sevenDay["utilization"]?.jsonPrimitive?.doubleOrNull ?: return null
    return listOf(
        ClaudeWindowUsage(
            id = "five_hour",
            label = "5h",
            utilization = fiveUtil,
            resetsAt = parseClaudeInstant(fiveHour["resets_at"]?.jsonPrimitive?.content)
        ),
        ClaudeWindowUsage(
            id = "seven_day",
            label = "7d",
            utilization = weekUtil,
            resetsAt = parseClaudeInstant(sevenDay["resets_at"]?.jsonPrimitive?.content)
        )
    )
}

// 429: keep the last successful value (shown as stale) and retry next cycle.
fun claudeRateLimitedStatus(
    account: ProviderAccount,
    cached: TokenStatus?,
    retryAfterHeader: String? = null,
): TokenStatus = rateLimitedTokenStatus(account, account.provider, retryAfterHeader, cached)

// Diagnosis summary for logs: path, HTTP status, window labels + utilization + resets_at.
// Tokens/headers/emails are never passed in, so they can never leak into the output.
fun claudeLogSummary(endpointPath: String, httpCode: Int, windows: List<ClaudeWindowUsage>): String =
    buildString {
        append("path=$endpointPath http=$httpCode")
        windows.forEach { w ->
            append(String.format(Locale.US, " | %s util=%.1f%%", w.label, w.utilization))
            append(" resets_at=${w.resetsAt ?: "null"}")
        }
    }
