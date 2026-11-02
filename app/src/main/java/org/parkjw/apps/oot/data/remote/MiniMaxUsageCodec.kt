package org.parkjw.apps.oot.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import org.parkjw.apps.oot.data.model.Remaining
import org.parkjw.apps.oot.data.model.UsageWindow
import java.time.Instant
import kotlin.math.abs

data class MiniMaxWindowUsage(
    val id: String,
    val label: String,
    val percentLeft: Double,
    val resetsAt: Instant?
) {
    val percentUsed: Double get() = (100.0 - percentLeft).coerceIn(0.0, 100.0)
}

fun MiniMaxWindowUsage.toUsageWindow() = UsageWindow(id, label, Remaining.Percent(percentLeft), resetsAt)

sealed interface MiniMaxUsageResult {
    data class Success(val windows: List<MiniMaxWindowUsage>) : MiniMaxUsageResult
    data class ApiError(val statusCode: Int, val statusMessage: String) : MiniMaxUsageResult
    data object InvalidResponse : MiniMaxUsageResult
}

private const val PERCENT_MATCH_TOLERANCE = 1.0
private const val TEXT_MODEL_PREFIX = "MiniMax-M"

// Parses /v1/token_plan/remains into the 5-hour and weekly windows, in that order.
// Count resolution follows the official client (MiniMax-AI/cli resolveQuotaCounts): the ambiguous
// *_usage_count is remaining by legacy, but when *_remaining_percent is present it selects which
// interpretation matches within 1 point. Anything unresolved is unknown instead of a made-up 0.
// Never throws: any other body shape is InvalidResponse or a response without windows.
fun parseMiniMaxUsage(
    body: String,
    json: Json = Json,
    now: Instant = Instant.now()
): MiniMaxUsageResult {
    val root = try { json.parseToJsonElement(body) as? JsonObject } catch (e: Exception) { null }
        ?: return MiniMaxUsageResult.InvalidResponse

    val baseResp = root["base_resp"] as? JsonObject
    val statusCode = (baseResp?.get("status_code") as? JsonPrimitive)?.intOrNull
    if (baseResp != null && statusCode != null && statusCode != 0) {
        val message = (baseResp["status_msg"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        return MiniMaxUsageResult.ApiError(statusCode, message)
    }

    val models = (root["model_remains"] as? JsonArray)
        ?.mapNotNull { it as? JsonObject }
        .orEmpty()
    val bucket = models.firstOrNull { model ->
        model.modelName()?.startsWith(TEXT_MODEL_PREFIX) == true && !model.isNotInPlan()
    } ?: models.firstOrNull { !it.isNotInPlan() }
        ?: return MiniMaxUsageResult.Success(emptyList())

    return MiniMaxUsageResult.Success(bucket.windows(now))
}

private fun JsonObject.windows(now: Instant): List<MiniMaxWindowUsage> = listOfNotNull(
    intervalWindow(now),
    weeklyWindow(now)
)

private fun JsonObject.intervalWindow(now: Instant): MiniMaxWindowUsage? {
    val total = count("current_interval_total_count") ?: return null
    if (total <= 0) return null
    val reported = count("current_interval_usage_count") ?: return null
    val left = percentLeft(
        reportedCount = reported,
        total = total,
        remainingPercent = double("current_interval_remaining_percent")
    ) ?: return null
    return MiniMaxWindowUsage(
        id = "five_hour",
        label = "5h",
        percentLeft = left,
        resetsAt = resetAt("end_time", "remains_time", now)
    )
}

private fun JsonObject.weeklyWindow(now: Instant): MiniMaxWindowUsage? {
    val resetsAt = resetAt("weekly_end_time", "weekly_remains_time", now)
    // Status 3 alone is unlimited weekly; a zero total without it is a window the API did not report.
    if (int("current_weekly_status") == 3) {
        return MiniMaxWindowUsage("seven_day", "7d", 100.0, resetsAt)
    }
    val total = count("current_weekly_total_count") ?: return null
    if (total <= 0) return null
    val reported = count("current_weekly_usage_count") ?: return null
    val left = percentLeft(
        reportedCount = reported,
        total = total,
        remainingPercent = double("current_weekly_remaining_percent"),
        boostPermille = double("weekly_boost_permille")
    ) ?: return null
    return MiniMaxWindowUsage("seven_day", "7d", left, resetsAt)
}

// A bucket with no counts and status 3 on both windows is a model outside the current plan.
private fun JsonObject.isNotInPlan(): Boolean =
    count("current_interval_total_count") == 0.0 &&
        count("current_weekly_total_count") == 0.0 &&
        int("current_interval_status") == 3 &&
        int("current_weekly_status") == 3

private fun percentLeft(
    reportedCount: Double,
    total: Double,
    remainingPercent: Double?,
    boostPermille: Double? = null
): Double? {
    if (!reportedCount.isFinite() || !total.isFinite()) return null
    if (total <= 0 || reportedCount < 0 || reportedCount > total) return null

    var remaining = reportedCount
    if (remainingPercent != null && remainingPercent.isFinite()) {
        val reportedAsRemaining = reportedCount / total * 100.0
        val reportedAsUsed = (total - reportedCount) / total * 100.0
        val remainingDistance = abs(reportedAsRemaining - remainingPercent)
        val usedDistance = abs(reportedAsUsed - remainingPercent)
        if (minOf(remainingDistance, usedDistance) > PERCENT_MATCH_TOLERANCE) return null
        if (usedDistance < remainingDistance) remaining = total - reportedCount
    }

    val boost = boostPermille?.takeIf { it.isFinite() }?.coerceAtLeast(0.0)?.div(1000.0) ?: 1.0
    return (remaining / total * 100.0 * boost).coerceIn(0.0, 100.0)
}

private fun JsonObject.modelName(): String? = (this["model_name"] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

private fun JsonObject.count(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull

private fun JsonObject.double(key: String): Double? =
    (this[key] as? JsonPrimitive)?.doubleOrNull?.takeIf { it.isFinite() }

private fun JsonObject.resetAt(endKey: String, remainsKey: String, now: Instant): Instant? {
    val end = (this[endKey] as? JsonPrimitive)?.longOrNull?.takeIf { it > 0 }
    if (end != null) return Instant.ofEpochMilli(end)
    val remains = (this[remainsKey] as? JsonPrimitive)?.longOrNull?.takeIf { it > 0 }
    return remains?.let { now.plusMillis(it) }
}
