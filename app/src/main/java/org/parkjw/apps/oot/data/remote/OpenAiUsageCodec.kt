package org.parkjw.apps.oot.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.parkjw.apps.oot.R
import org.parkjw.apps.oot.data.model.ProviderAccount
import org.parkjw.apps.oot.data.model.Remaining
import org.parkjw.apps.oot.data.model.TokenStatus
import org.parkjw.apps.oot.data.model.UsageWindow
import org.parkjw.apps.oot.data.model.VendorError
import org.parkjw.apps.oot.i18n.AppText
import java.time.Instant
import java.util.Locale

data class OpenAiWindowUsage(
    val id: String,
    val label: String,
    val usedPercent: Double,
    val resetsAt: Instant?
)

fun mapOpenAiHttpError(code: Int): VendorError = when (code) {
    401 -> VendorError.Unauthorized
    403 -> VendorError.ScopeMissing
    429 -> VendorError.RateLimited
    else -> VendorError.Http(code)
}

fun openAiErrorText(error: VendorError): String = when (error) {
    VendorError.Unauthorized -> AppText.get(R.string.data_error_sign_in_again)
    VendorError.ScopeMissing -> AppText.get(R.string.data_error_sign_in_again_scope)
    VendorError.RateLimited -> AppText.get(R.string.data_error_rate_limited)
    VendorError.Network -> AppText.get(R.string.data_error_network)
    VendorError.Parse -> AppText.get(R.string.data_error_parse)
    is VendorError.Http -> AppText.get(R.string.data_error_http, error.code)
    is VendorError.Unknown -> error.message ?: AppText.get(R.string.data_error_generic)
}

/**
 * Parses ChatGPT `GET /backend-api/wham/usage` body (Codex backend-client RateLimitStatusPayload).
 * primary_window -> five_hour; secondary_window -> seven_day (labels come from resources in the UI).
 * Only fields confirmed in codex-backend-openapi-models RateLimitWindowSnapshot:
 * used_percent, limit_window_seconds, reset_after_seconds, reset_at (unix seconds).
 */
fun parseOpenAiWhamUsage(body: String, json: Json = Json): List<OpenAiWindowUsage>? {
    val root = try {
        json.parseToJsonElement(body).jsonObject
    } catch (_: Exception) {
        return null
    }
    val rateLimit = root["rate_limit"]?.jsonObject ?: return null
    val windows = mutableListOf<OpenAiWindowUsage>()

    parseWindow(rateLimit["primary_window"]?.jsonObject, id = "five_hour", label = "5h")
        ?.let { windows.add(it) }
    parseWindow(rateLimit["secondary_window"]?.jsonObject, id = "seven_day", label = "7d")
        ?.let { windows.add(it) }

    return windows.takeIf { it.isNotEmpty() }
}

private fun parseWindow(
    obj: kotlinx.serialization.json.JsonObject?,
    id: String,
    label: String
): OpenAiWindowUsage? {
    if (obj == null) return null
    val used = obj["used_percent"]?.jsonPrimitive?.intOrNull
        ?: obj["used_percent"]?.jsonPrimitive?.longOrNull?.toInt()
        ?: return null
    val resetAtSec = obj["reset_at"]?.jsonPrimitive?.longOrNull
        ?: obj["reset_at"]?.jsonPrimitive?.intOrNull?.toLong()
    val resetsAt = resetAtSec?.let { Instant.ofEpochSecond(it) }
    return OpenAiWindowUsage(
        id = id,
        label = label,
        usedPercent = used.toDouble().coerceIn(0.0, 100.0),
        resetsAt = resetsAt
    )
}

fun openAiOAuthSuccessStatus(account: ProviderAccount, windows: List<OpenAiWindowUsage>): TokenStatus {
    val fiveHour = windows.firstOrNull { it.id == "five_hour" }
    val weekly = windows.firstOrNull { it.id == "seven_day" }
    val fiveUsed = fiveHour?.usedPercent ?: 0.0
    val weekUsed = weekly?.usedPercent ?: 0.0
    val fiveLeft = (100.0 - fiveUsed).coerceIn(0.0, 100.0)
    val weekLeft = (100.0 - weekUsed).coerceIn(0.0, 100.0)

    return TokenStatus(
        accountId = account.id,
        provider = account.provider,
        accountName = account.name,
        remainingFormatted = AppText.get(
            R.string.data_claude_usage_left,
            String.format(Locale.US, "%.0f", fiveLeft),
            String.format(Locale.US, "%.0f", weekLeft)
        ),
        totalLimitFormatted = AppText.get(
            R.string.data_usage_detail_no_reset,
            String.format(Locale.US, "%.0f", fiveUsed),
            String.format(Locale.US, "%.0f", weekUsed)
        ),
        usagePercent = fiveUsed.toFloat(),
        statusSummary = AppText.get(
            R.string.data_claude_summary,
            String.format(Locale.US, "%.0f", fiveLeft),
            String.format(Locale.US, "%.0f", weekLeft)
        ),
        isWarning = fiveUsed > 80.0 || weekUsed > 80.0,
        isCritical = fiveUsed > 95.0 || weekUsed > 95.0,
        windows = listOfNotNull(
            fiveHour?.let {
                UsageWindow(it.id, it.label, Remaining.Percent(fiveLeft), it.resetsAt)
            },
            weekly?.let {
                UsageWindow(it.id, it.label, Remaining.Percent(weekLeft), it.resetsAt)
            }
        )
    )
}
