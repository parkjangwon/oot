package org.parkjw.apps.oot.update

import org.json.JSONObject

object ReleaseFixtures {
    fun asset(name: String, contentType: String = "application/vnd.android.package-archive"): String =
        """
        {
          "name": ${JSONObject.quote(name)},
          "content_type": ${JSONObject.quote(contentType)},
          "size": 5242880,
          "state": "uploaded",
          "browser_download_url": "https://github.com/parkjangwon/oot/releases/download/{{tag}}/$name"
        }
        """.trimIndent()

    fun release(
        tag: String,
        draft: Boolean = false,
        prerelease: Boolean = false,
        assets: List<String>? = null,
    ): String {
        val plain = tag.removePrefix("v")
        val assetJson = (assets ?: listOf(asset("oot-v$plain.apk"))).joinToString(",").replace("{{tag}}", tag)
        return """
            {
              "url": "https://api.github.com/repos/parkjangwon/oot/releases/1",
              "html_url": "https://github.com/parkjangwon/oot/releases/tag/$tag",
              "id": 1,
              "tag_name": ${JSONObject.quote(tag)},
              "target_commitish": "main",
              "name": ${JSONObject.quote(tag)},
              "draft": $draft,
              "immutable": false,
              "prerelease": $prerelease,
              "created_at": "2026-10-17T19:21:06Z",
              "published_at": "2026-10-17T19:25:20Z",
              "assets": [$assetJson],
              "body": ${JSONObject.quote("notes for $tag")}
            }
        """.trimIndent()
    }

    fun list(vararg releases: String): String = releases.joinToString(",", prefix = "[", postfix = "]")
}
