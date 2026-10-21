package org.parkjw.apps.oot.update

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ReleaseJsonTest {
    @Test
    fun parsesGitHubReleaseFields() {
        val releases = ReleaseJson.parseList(ReleaseFixtures.list(ReleaseFixtures.release("v1.1.0")))
        val release = releases.single()
        assertEquals("v1.1.0", release.tagName)
        assertEquals(AppVersion(1, 1, 0), release.version)
        assertEquals("v1.1.0", release.name)
        assertEquals("https://github.com/parkjangwon/oot/releases/tag/v1.1.0", release.htmlUrl)
        assertEquals("notes for v1.1.0", release.notes)
        assertEquals("2026-10-17T19:25:20Z", release.publishedAt)
        assertFalse(release.draft)
        assertFalse(release.prerelease)
        val asset = release.assets.single()
        assertEquals("oot-v1.1.0.apk", asset.name)
        assertEquals(
            "https://github.com/parkjangwon/oot/releases/download/v1.1.0/oot-v1.1.0.apk",
            asset.downloadUrl,
        )
        assertEquals(5_242_880L, asset.sizeBytes)
        assertEquals(asset, release.apkAsset())
    }

    @Test
    fun toleratesMissingAndNullFields() {
        val json = """[{"tag_name":"v1.1.0","name":null,"body":null,"published_at":null,"assets":null}]"""
        val release = ReleaseJson.parseList(json).single()
        assertEquals("v1.1.0", release.name)
        assertEquals("", release.notes)
        assertEquals("", release.htmlUrl)
        assertNull(release.publishedAt)
        assertFalse(release.draft)
        assertFalse(release.prerelease)
        assertTrue(release.assets.isEmpty())
        assertNull(release.apkAsset())
    }

    @Test
    fun skipsEntriesWithoutTagAndKeepsUnparseableTagsWithoutVersion() {
        val json = """[{"name":"no tag"},{"tag_name":""},42,{"tag_name":"nightly"},{"tag_name":"v1.1.0"}]"""
        val releases = ReleaseJson.parseList(json)
        assertEquals(listOf("nightly", "v1.1.0"), releases.map { it.tagName })
        assertNull(releases[0].version)
        assertFalse(releases[0].isStable)
    }

    @Test
    fun malformedBodiesThrow() {
        listOf("", "not json", "{}", """{"message":"Not Found"}""", "[{", "[1,2]", """[{"message":"x"}]""").forEach { body ->
            assertThrows(body, MalformedReleaseJsonException::class.java) { ReleaseJson.parseList(body) }
        }
        assertTrue(ReleaseJson.parseList("[]").isEmpty())
    }

    @Test
    fun apkAssetPrefersExpectedNameAndIgnoresOthers() {
        val json = ReleaseFixtures.list(
            ReleaseFixtures.release(
                "v1.1.0",
                assets = listOf(
                    ReleaseFixtures.asset("checksums.txt", "text/plain"),
                    ReleaseFixtures.asset("oot-v1.1.0-debug.apk"),
                    ReleaseFixtures.asset("oot-v1.1.0.apk"),
                ),
            ),
        )
        assertEquals("oot-v1.1.0.apk", ReleaseJson.parseList(json).single().apkAsset()?.name)
    }

    @Test
    fun apkAssetRequiresExactNameAndHttpsUrl() {
        val wrongName = ReleaseFixtures.release("v1.1.0", assets = listOf(ReleaseFixtures.asset("app-release.apk")))
        val blankName = ReleaseFixtures.release("v1.1.0", assets = listOf(ReleaseFixtures.asset(" ")))
        val http = """[{"tag_name":"v1.1.0","assets":[{"name":"oot-v1.1.0.apk","browser_download_url":"http://example.com/a.apk"}]}]"""
        assertNull(ReleaseJson.parseList(ReleaseFixtures.list(wrongName)).single().apkAsset())
        assertTrue(ReleaseJson.parseList(ReleaseFixtures.list(blankName)).single().assets.isEmpty())
        assertNull(ReleaseJson.parseList(http).single().apkAsset())
    }
}
