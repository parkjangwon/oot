package org.parkjw.apps.oot.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import org.parkjw.apps.oot.data.model.Remaining
import org.parkjw.apps.oot.data.model.UsageWindow
import org.parkjw.apps.oot.data.model.VendorError

// Pure mapping of GET https://opencode.ai/zen/go/v1/usage responses.
// "percent" is the USED percent (0..100); remaining = 100 - percent.
object OpenCodeGoCodec {

    // Rolling/weekly/monthly windows; any of them may be missing.
    // Returns null when the body is not the expected shape, an empty list when no window has data.
    fun parse(body: String, json: Json = Json): List<UsageWindow>? {
        val root = try { json.parseToJsonElement(body).jsonObject } catch (e: Exception) { return null }
        val usage = root["usage"] as? JsonObject ?: return null
        return listOfNotNull(
            window(usage["rolling"] as? JsonObject, "five_hour", "5h"),
            window(usage["weekly"] as? JsonObject, "weekly", "weekly"),
            window(usage["monthly"] as? JsonObject, "monthly", "monthly")
        )
    }

    private fun window(obj: JsonObject?, id: String, label: String): UsageWindow? {
        val source = obj ?: return null
        val used = (source["percent"] as? JsonPrimitive)?.doubleOrNull ?: return null
        return UsageWindow(
            id = id,
            label = label,
            remaining = Remaining.Percent((100.0 - used).coerceIn(0.0, 100.0)),
            resetsAt = parseClaudeInstant((source["resetsAt"] as? JsonPrimitive)?.content)
        )
    }

    fun httpError(code: Int): VendorError = when (code) {
        401 -> VendorError.Unauthorized
        // OpenCodeGoCodec runs in plain JVM tests; the API layer sets the 403 message from resources.
        403 -> VendorError.Unknown(null)
        429 -> VendorError.RateLimited
        else -> VendorError.Http(code)
    }
}
