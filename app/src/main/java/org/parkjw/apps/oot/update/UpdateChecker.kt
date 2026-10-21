package org.parkjw.apps.oot.update

import java.io.IOException

data class ReleaseResponse(
    val statusCode: Int,
    val body: String,
    val headers: Map<String, String> = emptyMap(),
) {
    fun header(name: String): String? =
        headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
}

fun interface ReleaseSource {
    @Throws(IOException::class)
    fun fetchReleases(): ReleaseResponse
}

sealed interface UpdateFailure {
    data object Network : UpdateFailure
    data object RateLimited : UpdateFailure
    data class Http(val statusCode: Int) : UpdateFailure
    data object MalformedResponse : UpdateFailure
    data object UnknownCurrentVersion : UpdateFailure
}

sealed interface UpdateCheckResult {
    data class UpToDate(val current: AppVersion, val latest: Release?) : UpdateCheckResult
    data class Available(val current: AppVersion, val release: Release, val apk: ReleaseAsset?) : UpdateCheckResult
    data class Failed(val reason: UpdateFailure) : UpdateCheckResult
}

class UpdateChecker(private val source: ReleaseSource) {
    fun check(currentVersionName: String?): UpdateCheckResult {
        val current = AppVersion.parse(currentVersionName)
            ?: return UpdateCheckResult.Failed(UpdateFailure.UnknownCurrentVersion)
        val response = try {
            source.fetchReleases()
        } catch (e: IOException) {
            return UpdateCheckResult.Failed(UpdateFailure.Network)
        }
        httpFailure(response)?.let { return UpdateCheckResult.Failed(it) }
        val releases = try {
            ReleaseJson.parseList(response.body)
        } catch (e: MalformedReleaseJsonException) {
            return UpdateCheckResult.Failed(UpdateFailure.MalformedResponse)
        }
        val latest = latestStableRelease(releases)
        return if (latest?.version != null && latest.version > current) {
            UpdateCheckResult.Available(current, latest, latest.apkAsset())
        } else {
            UpdateCheckResult.UpToDate(current, latest)
        }
    }

    private fun httpFailure(response: ReleaseResponse): UpdateFailure? {
        val code = response.statusCode
        if (code in 200..299) return null
        val rateLimited = code == 429 ||
            (code == 403 && (response.header("X-RateLimit-Remaining")?.trim() == "0" || response.header("Retry-After") != null))
        return if (rateLimited) UpdateFailure.RateLimited else UpdateFailure.Http(code)
    }
}
