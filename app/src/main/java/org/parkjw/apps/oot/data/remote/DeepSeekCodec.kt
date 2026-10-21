package org.parkjw.apps.oot.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.parkjw.apps.oot.data.model.Remaining
import org.parkjw.apps.oot.data.model.UsageWindow
import org.parkjw.apps.oot.data.model.VendorError

// Pure mapping of GET https://api.deepseek.com/user/balance responses.
object DeepSeekCodec {

    // One balance window per balance_infos entry; null when the body is not the expected shape.
    fun parse(body: String, json: Json = Json): List<UsageWindow>? {
        val root = try { json.parseToJsonElement(body).jsonObject } catch (e: Exception) { return null }
        val infos = root["balance_infos"]?.jsonArray ?: return null
        return infos.mapNotNull { element ->
            val info = element.jsonObject
            val total = info["total_balance"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            val currency = info["currency"]?.jsonPrimitive?.content ?: "USD"
            UsageWindow("credit", "credit", Remaining.Balance(total, currency))
        }
    }

    fun httpError(code: Int): VendorError = when (code) {
        401 -> VendorError.Unauthorized
        429 -> VendorError.RateLimited
        else -> VendorError.Http(code)
    }
}
