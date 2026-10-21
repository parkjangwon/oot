package org.parkjw.apps.oot.update

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ApkDownloaderTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val bytes = ByteArray(300_000) { (it % 251).toByte() }
    private val url = "https://github.com/parkjangwon/oot/releases/download/v1.1.0/oot-v1.1.0.apk"

    private fun asset(size: Long = bytes.size.toLong(), downloadUrl: String = url) =
        ReleaseAsset("oot-v1.1.0.apk", downloadUrl, size, "application/vnd.android.package-archive")

    private fun dir() = File(tmp.root, "updates")

    private fun downloader(length: Long = -1, input: () -> InputStream = { ByteArrayInputStream(bytes) }) =
        FileApkDownloader(dir()) { ApkStream(length, input()) }

    @Test
    fun writesFileReportsProgressAndClosesConnection() {
        val requested = mutableListOf<String>()
        val progress = mutableListOf<Int>()
        var closed = 0
        val file = FileApkDownloader(dir()) { requestedUrl ->
            requested += requestedUrl
            ApkStream(bytes.size.toLong(), ByteArrayInputStream(bytes)) { closed++ }
        }.download(asset()) { progress += it }
        assertEquals(listOf(url), requested)
        assertArrayEquals(bytes, file.readBytes())
        assertEquals(APK_FILE_NAME, file.name)
        assertEquals(100, progress.last())
        assertEquals(progress.sorted(), progress)
        assertEquals(progress.distinct(), progress)
        assertEquals(listOf(file), dir().listFiles()!!.toList())
        assertEquals(1, closed)
    }

    @Test
    fun removesOlderDownloads() {
        dir().mkdirs()
        File(dir(), "oot-v1.0.0.apk").writeText("old")
        downloader().download(asset()) {}
        assertEquals(listOf(APK_FILE_NAME), dir().list()!!.toList())
    }

    @Test
    fun truncatedOversizedOrMismatchedDownloadsFailAndLeaveNothing() {
        assertThrows(IOException::class.java) {
            downloader { ByteArrayInputStream(bytes.copyOf(1000)) }.download(asset()) {}
        }
        assertTrue(dir().list()!!.isEmpty())
        assertThrows(IOException::class.java) { downloader().download(asset(size = 10)) {} }
        assertTrue(dir().list()!!.isEmpty())
        assertThrows(IOException::class.java) { downloader(length = 12345).download(asset()) {} }
        assertTrue(dir().list()!!.isEmpty())
        assertThrows(IOException::class.java) {
            downloader(length = 0) { ByteArrayInputStream(ByteArray(0)) }.download(asset(size = 0)) {}
        }
        assertThrows(IOException::class.java) { downloader().download(asset(size = MAX_APK_BYTES + 1)) {} }
        assertThrows(IOException::class.java) { downloader(length = MAX_APK_BYTES + 1).download(asset(size = 0)) {} }
    }

    @Test
    fun unknownSizeIsCappedAndStillWorksBelowCap() {
        val file = downloader().download(asset(size = 0)) {}
        assertArrayEquals(bytes, file.readBytes())
    }

    @Test
    fun streamErrorsPropagateAndCloseConnection() {
        var closed = false
        val failing = object : InputStream() {
            override fun read(): Int = throw IOException("reset")
            override fun read(b: ByteArray, off: Int, len: Int): Int = throw IOException("reset")
        }
        assertThrows(IOException::class.java) {
            FileApkDownloader(dir()) { ApkStream(-1, failing) { closed = true } }.download(asset()) {}
        }
        assertTrue(dir().list()!!.isEmpty())
        assertTrue(closed)
    }

    @Test
    fun refusesUrlsOutsideThisRepositoryReleases() {
        listOf(
            "http://github.com/parkjangwon/oot/releases/download/v1.1.0/oot-v1.1.0.apk",
            "https://example.com/oot-v1.1.0.apk",
            "https://github.com/someone/else/releases/download/v1.1.0/oot-v1.1.0.apk",
        ).forEach { bad ->
            var opened = false
            val d = FileApkDownloader(dir()) {
                opened = true
                ApkStream(-1, ByteArrayInputStream(bytes))
            }
            assertThrows(IOException::class.java) { d.download(asset(downloadUrl = bad)) {} }
            assertTrue(!opened)
        }
    }

    @Test
    fun progressPercentIsClamped() {
        assertEquals(0, progressPercent(10, 0))
        assertEquals(0, progressPercent(0, 100))
        assertEquals(33, progressPercent(1, 3))
        assertEquals(100, progressPercent(150, 100))
    }
}
