package org.parkjw.apps.oot.update

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

const val MAX_APK_BYTES = 200L * 1024 * 1024
internal const val APK_FILE_NAME = "oot-update.apk"

class ApkStream(
    val contentLength: Long,
    val input: InputStream,
    val close: () -> Unit = {},
)

fun interface ApkTransport {
    @Throws(IOException::class)
    fun open(url: String): ApkStream
}

fun interface ApkDownloader {
    @Throws(IOException::class)
    fun download(asset: ReleaseAsset, onProgress: (Int) -> Unit): File
}

fun progressPercent(read: Long, total: Long): Int =
    if (total <= 0L) 0 else ((read * 100) / total).coerceIn(0L, 100L).toInt()

class FileApkDownloader(
    private val directory: File,
    private val transport: ApkTransport,
) : ApkDownloader {
    override fun download(asset: ReleaseAsset, onProgress: (Int) -> Unit): File {
        if (!asset.downloadUrl.startsWith(RELEASE_DOWNLOAD_PREFIX)) throw IOException("unexpected download url")
        if (asset.sizeBytes > MAX_APK_BYTES) throw IOException("apk too large")
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("cannot create $directory")
        directory.listFiles()?.forEach { it.delete() }
        val target = File(directory, APK_FILE_NAME)
        val partial = File(directory, "$APK_FILE_NAME.part")
        val stream = transport.open(asset.downloadUrl)
        try {
            val declared = asset.sizeBytes.takeIf { it > 0L }
            val served = stream.contentLength.takeIf { it > 0L }
            if (declared != null && served != null && declared != served) throw IOException("size mismatch")
            if ((served ?: 0L) > MAX_APK_BYTES) throw IOException("apk too large")
            val expected = declared ?: served ?: -1L
            val limit = if (expected > 0L) expected else MAX_APK_BYTES
            var read = 0L
            var lastPercent = -1
            stream.input.use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        read += n
                        if (read > limit) throw IOException("download larger than expected")
                        output.write(buffer, 0, n)
                        val percent = progressPercent(read, expected)
                        if (percent != lastPercent) {
                            lastPercent = percent
                            onProgress(percent)
                        }
                    }
                }
            }
            if (read == 0L || (expected > 0L && read != expected)) throw IOException("incomplete download")
            if (!partial.renameTo(target)) throw IOException("cannot move download into place")
        } catch (e: IOException) {
            partial.delete()
            target.delete()
            throw e
        } finally {
            stream.close()
        }
        return target
    }
}

class HttpsApkTransport(private val userAgent: String) : ApkTransport {
    override fun open(url: String): ApkStream {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", userAgent)
            setRequestProperty("Accept", "application/octet-stream")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299 || connection.url.protocol != "https") {
                throw IOException("download failed with HTTP $code")
            }
            return ApkStream(connection.contentLengthLong, connection.inputStream) { connection.disconnect() }
        } catch (e: IOException) {
            connection.disconnect()
            throw e
        }
    }
}
