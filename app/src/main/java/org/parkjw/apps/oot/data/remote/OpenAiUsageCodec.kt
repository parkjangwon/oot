package org.parkjw.apps.oot.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.parkjw.apps.oot.data.model.ProviderAccount
import org.parkjw.apps.oot.data.model.Remaining
import org.parkjw.apps.oot.data.model.TokenStatus
import org.parkjw.apps.oot.data.model.UsageWindow
import org.parkjw.apps.oot.data.model.VendorError
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
    VendorError.Unauthorized -> "다시 로그인 필요"
    VendorError.ScopeMissing -> "다시 로그인 필요(범위)"
    VendorError.RateLimited -> "요청 제한(429)"
    VendorError.Network -> "네트워크 오류"
    VendorError.Parse -> "응답 해석 실패"
    is VendorError.Http -> "조회 실패 (HTTP ${error.code})"
    is VendorError.Unknown -> error.message ?: "오류"
}

/**
 * Parses ChatGPT `GET /backend-api/wham/usage` body (Codex backend-client RateLimitStatusPayload).
 * primary_window -> five_hour / "5시간"; secondary_window -> seven_day / "주간".
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

    parseWindow(rateLimit["primary_window"]?.jsonObject, id = "five_hour", label = "5시간")
        ?.let { windows.add(it) }
    parseWindow(rateLimit["secondary_window"]?.jsonObject, id = "seven_day", label = "주간")
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
        remainingFormatted = String.format(
            Locale.US,
            "5시간: %.0f%% 남음 | 주간: %.0f%% 남음",
            fiveLeft,
            weekLeft
        ),
        totalLimitFormatted = String.format(
            Locale.US,
            "5h 사용: %.0f%% | 주간 사용: %.0f%%",
            fiveUsed,
            weekUsed
        ),
        usagePercent = fiveUsed.toFloat(),
        statusSummary = String.format(Locale.US, "5h %.0f%% | 주 %.0f%%", fiveLeft, weekLeft),
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
