package org.parkjw.apps.oot.update

data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val contentType: String,
)

data class Release(
    val tagName: String,
    val version: AppVersion?,
    val name: String,
    val htmlUrl: String,
    val notes: String,
    val publishedAt: String?,
    val draft: Boolean,
    val prerelease: Boolean,
    val assets: List<ReleaseAsset>,
) {
    val isStable: Boolean
        get() = !draft && !prerelease && version != null && !version.isPreRelease

    fun apkAsset(): ReleaseAsset? {
        val expected = version?.let { "oot-v$it.apk" } ?: return null
        return assets.firstOrNull { it.name == expected && it.downloadUrl.startsWith(RELEASE_DOWNLOAD_PREFIX) }
    }
}

fun latestStableRelease(releases: List<Release>): Release? =
    releases.filter { it.isStable }.maxWithOrNull(compareBy { it.version })
