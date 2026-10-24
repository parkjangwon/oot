package org.parkjw.apps.oot.diagnostics

import java.net.URLDecoder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorReportTest {

    private val record = CrashRecord(
        id = "1",
        timestamp = 1_760_000_000_000L,
        kind = CrashKind.CRASH,
        summary = "java.lang.IllegalStateException: token sk-abcdefgh12345678",
        detail = "Authorization: Bearer sk-abcdefgh12345678\nuser demo@example.com\n" + "at stack(File.kt:1)\n".repeat(400),
        appVersion = "1.4.0",
        appVersionCode = 10400,
        androidVersion = "15 (API 35)",
        device = "Google Pixel 8",
        thread = "main",
    )

    @Test
    fun exportTextIsRedacted() {
        val text = ErrorReport.exportText(listOf(record))

        assertTrue(text.contains("1.4.0 (10400)"))
        assertTrue(text.contains("Google Pixel 8"))
        assertTrue(text.contains("Thread: main"))
        assertFalse(text.contains("sk-abcdefgh12345678"))
        assertFalse(text.contains("demo@example.com"))
        assertTrue(text.contains("[REDACTED]"))
        assertTrue(text.contains("[EMAIL]"))
    }

    @Test
    fun issueUrlStaysUnderTheLimitAndCarriesVersionAndDevice() {
        val url = ErrorReport.issueUrl(record)

        assertTrue("url length ${url.length}", url.length <= ErrorReport.MAX_ISSUE_URL_LENGTH)
        assertTrue(url.startsWith(ErrorReport.ISSUE_NEW_URL))
        val decoded = URLDecoder.decode(url.substringAfter("?"), Charsets.UTF_8.name())
        assertTrue(decoded.contains("Version: 1.4.0 (10400)"))
        assertTrue(decoded.contains("Android: 15 (API 35)"))
        assertTrue(decoded.contains("Device: Google Pixel 8"))
        assertTrue(decoded.contains("at stack(File.kt:1)"))
        assertFalse(decoded.contains("sk-abcdefgh12345678"))
        assertFalse(decoded.contains("demo@example.com"))
    }

    @Test
    fun previewShowsTheExactRedactedIssueThatTheUrlCarries() {
        val preview = ErrorReport.issuePreview(record)

        assertFalse(preview.title.contains("sk-abcdefgh12345678"))
        assertFalse(preview.body.contains("sk-abcdefgh12345678"))
        assertFalse(preview.body.contains("demo@example.com"))
        assertTrue(preview.body.contains("[REDACTED]"))
        assertTrue(preview.body.contains("[EMAIL]"))

        val url = ErrorReport.issueUrl(record)
        val decoded = URLDecoder.decode(url.substringAfter("?"), Charsets.UTF_8.name())
        assertTrue(decoded.contains(preview.title))
        assertTrue(decoded.contains(preview.body))
        assertTrue(url.length <= ErrorReport.MAX_ISSUE_URL_LENGTH)
    }

    @Test
    fun previewAlsoFitsForAHugeStackTrace() {
        val huge = record.copy(detail = "x".repeat(100_000))
        assertTrue(ErrorReport.issuePreview(huge).body.length < 2_000)
        assertTrue(ErrorReport.issueUrl(huge).length <= ErrorReport.MAX_ISSUE_URL_LENGTH)
    }

    @Test
    fun issueUrlAlsoFitsForAHugeStackTrace() {
        val huge = record.copy(detail = "x".repeat(100_000))
        assertTrue(ErrorReport.issueUrl(huge).length <= ErrorReport.MAX_ISSUE_URL_LENGTH)
    }
}
