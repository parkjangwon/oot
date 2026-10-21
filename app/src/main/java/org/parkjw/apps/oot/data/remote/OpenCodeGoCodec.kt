package org.parkjw.apps.oot.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
        val usage = root["usage"]?.jsonObject ?: return null
        return listOfNotNull(
            window(usage["rolling"]?.jsonObject, "five_hour", "5시간"),
            window(usage["weekly"]?.jsonObject, "weekly", "주간"),
            window(usage["monthly"]?.jsonObject, "monthly", "월간")
        )
    }

    private fun window(obj: JsonObject?, id: String, label: String): UsageWindow? {
        val used = obj?.get("percent")?.jsonPrimitive?.doubleOrNull ?: return null
        return UsageWindow(
            id = id,
            label = label,
            remaining = Remaining.Percent((100.0 - used).coerceIn(0.0, 100.0)),
            resetsAt = parseClaudeInstant(obj["resetsAt"]?.jsonPrimitive?.content)
        )
    }

    fun httpError(code: Int): VendorError = when (code) {
        401 -> VendorError.Unauthorized
        403 -> VendorError.Unknown("OpenCode Go 구독이 없어요")
        429 -> VendorError.RateLimited
        else -> VendorError.Http(code)
    }
}
