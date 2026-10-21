package org.parkjw.apps.oot.update

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class MalformedReleaseJsonException(message: String, cause: Throwable? = null) : Exception(message, cause)

object ReleaseJson {
    fun parseList(json: String): List<Release> {
        val array = try {
            JSONArray(json)
        } catch (e: JSONException) {
            throw MalformedReleaseJsonException("expected a JSON array of releases", e)
        }
        val releases = (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let(::parseObjectOrNull)
        }
        if (array.length() > 0 && releases.isEmpty()) {
            throw MalformedReleaseJsonException("no release objects in a non-empty array")
        }
        return releases
    }

    private fun parseObjectOrNull(obj: JSONObject): Release? {
        val tag = obj.optStringOrNull("tag_name")?.trim().orEmpty()
        if (tag.isEmpty()) return null
        val assets = obj.optJSONArray("assets")?.let { array ->
            (0 until array.length()).mapNotNull { array.optJSONObject(it)?.let(::parseAsset) }
        }.orEmpty()
        return Release(
            tagName = tag,
            version = AppVersion.parse(tag),
            name = obj.optStringOrNull("name").orEmpty().ifBlank { tag },
            htmlUrl = obj.optStringOrNull("html_url").orEmpty(),
            notes = obj.optStringOrNull("body").orEmpty(),
            publishedAt = obj.optStringOrNull("published_at"),
            draft = obj.optBoolean("draft", false),
            prerelease = obj.optBoolean("prerelease", false),
            assets = assets,
        )
    }

    private fun parseAsset(obj: JSONObject): ReleaseAsset? {
        val name = obj.optStringOrNull("name")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return ReleaseAsset(
            name = name,
            downloadUrl = obj.optStringOrNull("browser_download_url").orEmpty(),
            sizeBytes = obj.optLong("size", 0L).coerceAtLeast(0L),
            contentType = obj.optStringOrNull("content_type").orEmpty(),
        )
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) opt(key) as? String else null
}
