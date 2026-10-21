package org.parkjw.apps.oot.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.parkjw.apps.oot.data.model.Remaining
import org.parkjw.apps.oot.data.model.UsageWindow
import org.parkjw.apps.oot.data.model.VendorError

// Pure mapping of GET https://ollama.com/api/balance responses (legacy and credit plan shapes).
object OllamaCloudCodec {

    // Returns the usage windows, or null when the body is not the expected shape.
    // An empty list means the body parsed but carries no usable quota data.
    fun parse(body: String, json: Json = Json): List<UsageWindow>? {
        val root = try { json.parseToJsonElement(body).jsonObject } catch (e: Exception) { return null }
        val included = root["included"]?.jsonObject ?: return null
        val windows = mutableListOf<UsageWindow>()

        val creditBalance = included["balance_usd"]?.jsonPrimitive?.doubleOrNull
        val allowanceUsd = included["allowance_usd"]?.jsonPrimitive?.doubleOrNull
        if (creditBalance != null && allowanceUsd != null && allowanceUsd > 0.0) {
            // Credit plans: monthly allowance window from balance/allowance.
            val until = included["period"]?.jsonObject?.get("until")?.jsonPrimitive?.content
            windows += UsageWindow(
                id = "monthly",
                label = "월간",
                remaining = Remaining.Percent(((creditBalance / allowanceUsd) * 100).coerceIn(0.0, 100.0)),
                resetsAt = parseClaudeInstant(until)
            )
        } else {
            // Legacy plans: session and weekly remaining percent.
            included["session"]?.jsonObject?.let { session ->
                session["remaining_percent"]?.jsonPrimitive?.doubleOrNull?.let { pct ->
                    windows += UsageWindow(
                        id = "session",
                        label = "세션",
                        remaining = Remaining.Percent(pct.coerceIn(0.0, 100.0)),
                        resetsAt = parseClaudeInstant(session["resets_at"]?.jsonPrimitive?.content)
                    )
                }
            }
            included["weekly"]?.jsonObject?.let { weekly ->
                weekly["remaining_percent"]?.jsonPrimitive?.doubleOrNull?.let { pct ->
                    windows += UsageWindow(
                        id = "weekly",
                        label = "주간",
                        remaining = Remaining.Percent(pct.coerceIn(0.0, 100.0)),
                        resetsAt = parseClaudeInstant(weekly["resets_at"]?.jsonPrimitive?.content)
                    )
                }
            }
        }

        // Purchased credit: shown only when it exists and is positive.
        root["purchased"]?.jsonObject?.get("balance_usd")?.jsonPrimitive?.doubleOrNull
            ?.takeIf { it > 0.0 }
            ?.let { windows += UsageWindow("credit", "credit", Remaining.Balance(it, "USD")) }

        return windows
    }

    fun httpError(code: Int): VendorError = when (code) {
        401 -> VendorError.Unauthorized
        429 -> VendorError.RateLimited
        else -> VendorError.Http(code)
    }
}
