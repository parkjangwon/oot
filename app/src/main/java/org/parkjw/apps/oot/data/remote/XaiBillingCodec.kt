package org.parkjw.apps.oot.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import org.parkjw.apps.oot.data.model.Remaining
import org.parkjw.apps.oot.data.model.UsageWindow
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

data class XaiBilling(
    val usedPercent: Double,
    // currentPeriod.end: when the weekly credits reset; null when the response has no usable end.
    val periodEnd: Instant?,
    val productUsage: List<Pair<String, Double>>,
    // Only reported for unified-billing users.
    val prepaidBalance: Double?
) {
    val percentLeft: Double get() = (100.0 - usedPercent).coerceIn(0.0, 100.0)
}

fun XaiBilling.toUsageWindows(): List<UsageWindow> =
    listOf(UsageWindow("weekly", "weekly", Remaining.Percent(percentLeft), periodEnd))

// Parses the Grok CLI billing body (https://cli-chat-proxy.grok.com/v1/billing?format=credits).
// Returns null when the body shows no weekly period (error bodies, other shapes), so callers fall back
// instead of showing an invented 100% remaining. Never throws.
fun parseXaiBilling(body: String, json: Json = Json): XaiBilling? {
    val parsed = try { json.parseToJsonElement(body) } catch (e: Exception) { return null }
    val root = parsed as? JsonObject ?: return null
    val config = root["config"] as? JsonObject ?: root
    val currentPeriod = config["currentPeriod"] as? JsonObject
    val rawUsage = config.number("creditUsagePercent")
    if (currentPeriod == null && rawUsage == null) return null

    // creditUsagePercent is a 0.0..1.0 ratio; it may be omitted right after a weekly reset (0% used).
    val usedPercent = percentOf(rawUsage ?: 0.0).coerceIn(0.0, 100.0)
    val products = (config["productUsage"] as? JsonArray)?.mapNotNull { item ->
        val product = item as? JsonObject ?: return@mapNotNull null
        val name = (product["product"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
        name.removePrefix("Grok") to percentOf(product.number("usagePercent") ?: 0.0)
    }.orEmpty()
    val isUnified = (config["isUnifiedBillingUser"] as? JsonPrimitive)?.booleanOrNull == true
    val prepaid = (config["prepaidBalance"] as? JsonObject)?.number("val")?.takeIf { isUnified }

    return XaiBilling(
        usedPercent = usedPercent,
        periodEnd = parseXaiInstant((currentPeriod?.get("end") as? JsonPrimitive)?.contentOrNull),
        productUsage = products,
        prepaidBalance = prepaid
    )
}

private fun JsonObject.number(key: String): Double? =
    (this[key] as? JsonPrimitive)?.doubleOrNull?.takeIf { it.isFinite() }

private fun percentOf(value: Double): Double = if (value in 0.0..1.0) value * 100.0 else value

// RFC 3339 instant; a value without a zone is read as UTC (fractional seconds dropped), as the period end always was.
private fun parseXaiInstant(raw: String?): Instant? {
    if (raw.isNullOrBlank()) return null
    parseClaudeInstant(raw)?.let { return it }
    return try {
        LocalDateTime.parse(raw.substringBefore(".")).toInstant(ZoneOffset.UTC)
    } catch (e: DateTimeParseException) {
        null
    }
}
