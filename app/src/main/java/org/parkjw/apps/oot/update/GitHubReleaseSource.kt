package org.parkjw.apps.oot.update

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

const val RELEASES_PAGE_URL = "https://github.com/parkjangwon/oot/releases/latest"
internal const val RELEASE_PAGE_PREFIX = "https://github.com/parkjangwon/oot/releases/"
internal const val RELEASE_DOWNLOAD_PREFIX = "https://github.com/parkjangwon/oot/releases/download/"
internal const val RELEASES_API_URL = "https://api.github.com/repos/parkjangwon/oot/releases?per_page=20"
internal const val CONNECT_TIMEOUT_MS = 15_000
internal const val READ_TIMEOUT_MS = 30_000
private const val MAX_BODY_BYTES = 2 * 1024 * 1024

class GitHubReleaseSource(private val userAgent: String) : ReleaseSource {
    override fun fetchReleases(): ReleaseResponse {
        val connection = (URL(RELEASES_API_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", userAgent)
        }
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val bytes = stream?.use { it.readBytesBounded(MAX_BODY_BYTES + 1) } ?: ByteArray(0)
            if (bytes.size > MAX_BODY_BYTES) throw IOException("release response too large")
            val headers = connection.headerFields
                .filterKeys { it != null }
                .mapValues { (_, values) -> values.firstOrNull().orEmpty() }
            return ReleaseResponse(code, bytes.toString(Charsets.UTF_8), headers)
        } finally {
            connection.disconnect()
        }
    }
}

private fun java.io.InputStream.readBytesBounded(maxBytes: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(8192)
    var total = 0
    while (total < maxBytes) {
        val n = read(buf, 0, minOf(buf.size, maxBytes - total))
        if (n < 0) break
        out.write(buf, 0, n)
        total += n
    }
    return out.toByteArray()
}
