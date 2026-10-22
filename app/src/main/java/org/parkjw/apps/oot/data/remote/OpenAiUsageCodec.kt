package org.parkjw.apps.oot.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
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
import kotlin.math.roundToInt

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
 * Window labels come from `limit_window_seconds` (18000 -> 5h, 604800 -> 7d), falling back to the
 * position (primary -> five_hour, secondary -> seven_day) when it is absent.
 * A null or absent window is skipped; returns null when no window can be read.
 * Only fields confirmed in codex-backend-openapi-models RateLimitWindowSnapshot:
 * used_percent, limit_window_seconds, reset_after_seconds, reset_at (unix seconds).
 */
fun parseOpenAiWhamUsage(body: String, json: Json = Json): List<OpenAiWindowUsage>? {
    val root = try {
        json.parseToJsonElement(body).jsonObject
    } catch (_: Exception) {
        return null
    }
    val rateLimit = root["rate_limit"] as? JsonObject ?: return null
    val windows = listOfNotNull(
        parseWindow(rateLimit["primary_window"] as? JsonObject, id = "five_hour", label = "5h"),
        parseWindow(rateLimit["secondary_window"] as? JsonObject, id = "seven_day", label = "7d")
    )
    return windows.takeIf { it.isNotEmpty() }
}

private fun parseWindow(
    obj: JsonObject?,
    id: String,
    label: String
): OpenAiWindowUsage? {
    if (obj == null) return null
    val used = obj["used_percent"]?.jsonPrimitive?.doubleOrNull
        ?: obj["used_percent"]?.jsonPrimitive?.intOrNull?.toDouble()
        ?: return null
    val resetAtSec = obj["reset_at"]?.jsonPrimitive?.longOrNull
        ?: obj["reset_at"]?.jsonPrimitive?.intOrNull?.toLong()
    val resetsAt = resetAtSec?.let { Instant.ofEpochSecond(it) }
    val windowSeconds = obj["limit_window_seconds"]?.jsonPrimitive?.longOrNull
        ?: obj["limit_window_seconds"]?.jsonPrimitive?.intOrNull?.toLong()
    val (windowId, windowLabel) = when (windowSeconds) {
        18_000L -> "five_hour" to "5h"
        604_800L -> "seven_day" to "7d"
        else -> id to label
    }
    return OpenAiWindowUsage(
        id = windowId,
        label = windowLabel,
        usedPercent = used.coerceIn(0.0, 100.0),
        resetsAt = resetsAt
    )
}

fun openAiOAuthSuccessStatus(account: ProviderAccount, windows: List<OpenAiWindowUsage>): TokenStatus {
    if (windows.isEmpty()) {
        val text = openAiErrorText(VendorError.Parse)
        return TokenStatus(
            accountId = account.id,
            provider = account.provider,
            accountName = account.name,
            remainingFormatted = text,
            statusSummary = AppText.get(R.string.status_needs_check),
            isError = true,
            errorMessage = text
        )
    }
    // Build everything from the windows the response actually had; a missing window is not 0% used.
    val usageWindows = windows.map { w ->
        UsageWindow(
            id = w.id,
            label = w.label,
            remaining = Remaining.Percent((100.0 - w.usedPercent).coerceIn(0.0, 100.0)),
            resetsAt = w.resetsAt
        )
    }
    val remainingText = usageWindows.joinToString(" | ") { window ->
        val left = (window.remaining as Remaining.Percent).percentLeft
        AppText.get(R.string.data_window_left, window.label, left.roundToInt())
    }
    val primary = windows.firstOrNull { it.id == "five_hour" } ?: windows.first()
    val worstUsed = windows.maxOfOrNull { it.usedPercent } ?: 0.0

    return TokenStatus(
        accountId = account.id,
        provider = account.provider,
        accountName = account.name,
        remainingFormatted = remainingText,
        totalLimitFormatted = windows.joinToString(" | ") {
            AppText.get(R.string.data_window_used, it.label, it.usedPercent.roundToInt())
        },
        usagePercent = primary.usedPercent.toFloat(),
        statusSummary = usageWindows.joinToString(" | ") { window ->
            val left = (window.remaining as Remaining.Percent).percentLeft
            AppText.get(R.string.data_window_percent_left, window.label, left.roundToInt())
        },
        isWarning = worstUsed > 80.0,
        isCritical = worstUsed > 95.0,
        windows = usageWindows
    )
}
