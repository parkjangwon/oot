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

data class ZaiWindowUsage(
    val id: String,
    val label: String,
    val percentUsed: Double,
    val resetsAt: Instant?
) {
    val percentLeft: Double get() = (100.0 - percentUsed).coerceIn(0.0, 100.0)
}

data class ZaiUsage(val level: String, val windows: List<ZaiWindowUsage>)

fun ZaiWindowUsage.toUsageWindow() = UsageWindow(id, label, Remaining.Percent(percentLeft), resetsAt)

// Parses /api/monitor/usage/quota/limit into the 5-hour (limit unit 3) and weekly (unit 6) windows, in that order.
// A limit that is absent, or has no numeric "percentage", gives no window, so a plan with one limit shows one row.
// Never throws: a body of any other shape just has no windows.
fun parseZaiUsage(body: String, json: Json = Json): ZaiUsage {
    val root = try { json.parseToJsonElement(body) as? JsonObject } catch (e: Exception) { null }
    val data = root?.get("data") as? JsonObject ?: root
    val limits = ((data?.get("limits") ?: root?.get("limits")) as? JsonArray)
        ?.mapNotNull { it as? JsonObject }
        .orEmpty()
    return ZaiUsage(
        level = (data?.get("level") as? JsonPrimitive)?.contentOrNull ?: "GLM",
        windows = listOfNotNull(
            zaiWindow(limits, unit = 3, id = "five_hour", label = "5h"),
            zaiWindow(limits, unit = 6, id = "seven_day", label = "7d")
        )
    )
}

private fun zaiWindow(limits: List<JsonObject>, unit: Int, id: String, label: String): ZaiWindowUsage? {
    val limit = limits.firstOrNull { (it["unit"] as? JsonPrimitive)?.intOrNull == unit } ?: return null
    val used = (limit["percentage"] as? JsonPrimitive)?.doubleOrNull?.takeIf { it.isFinite() } ?: return null
    val resetMs = limit.epochMillis("nextResetTime") ?: limit.epochMillis("expireTime")
    return ZaiWindowUsage(id, label, used, resetMs?.let(Instant::ofEpochMilli))
}

private fun JsonObject.epochMillis(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull
