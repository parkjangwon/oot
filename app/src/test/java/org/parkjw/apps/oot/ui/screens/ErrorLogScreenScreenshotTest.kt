package org.parkjw.apps.oot.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import java.io.File
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.parkjw.apps.oot.diagnostics.CrashKind
import org.parkjw.apps.oot.diagnostics.CrashRecord
import org.parkjw.apps.oot.ui.theme.OotTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.GraphicsMode.Mode.NATIVE

@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(NATIVE)
@Config(sdk = [35], qualifiers = "ko-w412dp-h1040dp-notnight-xxhdpi")
class ErrorLogScreenScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val records = listOf(
        CrashRecord(
            id = "1",
            timestamp = Instant.parse("2026-10-24T08:12:00Z").toEpochMilli(),
            kind = CrashKind.CRASH,
            summary = "java.lang.NullPointerException: demo value",
            detail = "java.lang.NullPointerException: demo value\n" +
                "    at org.parkjw.apps.oot.ui.MainActivity.onCreate(MainActivity.kt:42)\n" +
                "    at android.app.Activity.performCreate(Activity.java:8000)\n" +
                "Authorization: Bearer sk-demo-abcdefgh12345678\n" +
                "contact demo@example.com",
            appVersion = "1.4.0",
            appVersionCode = 10400,
            androidVersion = "15 (API 35)",
            device = "Google Pixel 8",
            thread = "main",
        ),
        CrashRecord(
            id = "2",
            timestamp = Instant.parse("2026-10-23T21:40:00Z").toEpochMilli(),
            kind = CrashKind.ANR,
            summary = "ANR in org.parkjw.apps.oot (Input dispatching timed out)",
            detail = "reason: ANR\nprocess: org.parkjw.apps.oot",
            appVersion = "1.4.0",
            appVersionCode = 10400,
            androidVersion = "15 (API 35)",
            device = "Google Pixel 8",
        ),
        CrashRecord(
            id = "3",
            timestamp = Instant.parse("2026-10-22T05:03:00Z").toEpochMilli(),
            kind = CrashKind.SIGNALED,
            summary = "Killed by the system",
            detail = "reason: SIGNALED",
            appVersion = "1.4.0",
            appVersionCode = 10400,
            androidVersion = "15 (API 35)",
            device = "Google Pixel 8",
        ),
    )

    private fun setContent(darkTheme: Boolean = false, items: List<CrashRecord> = records) {
        composeRule.setContent {
            OotTheme(darkTheme = darkTheme) {
                ErrorLogScreen(
                    records = items,
                    onBack = {},
                    onExport = { true },
                    onReport = { true },
                    onDeleteRecord = {},
                    onClear = {},
                )
            }
        }
    }

    private fun openDetail() {
        composeRule.onNodeWithTag("error_log_row_1").performClick()
    }

    private fun captureDetail(fileName: String, darkTheme: Boolean = false) {
        setContent(darkTheme = darkTheme)
        openDetail()
        composeRule.onNodeWithTag("error_log_detail").assertExists()
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$fileName")
    }

    private fun captureReport(
        fileName: String,
        darkTheme: Boolean = false,
        english: Boolean = false,
    ) {
        setContent(darkTheme = darkTheme)
        openDetail()
        composeRule.onNodeWithTag("error_log_report_action").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("error_log_report_preview").assertExists()
        val notice = if (english) "This will be posted as a public issue" else "공개 이슈로 올라가요"
        composeRule.onNodeWithText(notice).assertExists()
        captureScreenRoboImage(File(roborazziSystemPropertyOutputDirectory(), fileName))
    }

    @Test
    fun emptyLight() {
        setContent(items = emptyList())
        composeRule.onNodeWithText("오류 기록이 없어요").assertExists()
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/error-log-empty-light-3x.png")
    }

    @Test
    @Config(qualifiers = "ko-w412dp-h1040dp-night-xxhdpi")
    fun emptyDark() {
        setContent(darkTheme = true, items = emptyList())
        composeRule.onNodeWithText("오류 기록이 없어요").assertExists()
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/error-log-empty-dark-3x.png")
    }

    @Test
    fun recordsLight() {
        setContent()
        composeRule.onNodeWithText("오류 기록").assertExists()
        composeRule.onNodeWithText("로그 파일 내보내기").assertExists()
        composeRule.onNodeWithText("기록 지우기").assertExists()
        composeRule.onNodeWithText("GitHub 이슈로 신고").assertDoesNotExist()
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/error-log-records-light-3x.png")
    }

    @Test
    @Config(qualifiers = "ko-w412dp-h1040dp-night-xxhdpi")
    fun recordsDark() {
        setContent(darkTheme = true)
        composeRule.onNodeWithText("오류 기록").assertExists()
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/error-log-records-dark-3x.png")
    }

    @Test
    fun detailLight() = captureDetail("error-log-detail-light-3x.png")

    @Test
    @Config(qualifiers = "ko-w412dp-h1040dp-night-xxhdpi")
    fun detailDark() = captureDetail("error-log-detail-dark-3x.png", darkTheme = true)

    @Test
    @Config(qualifiers = "en-w412dp-h1040dp-notnight-xxhdpi")
    fun detailEnglish() = captureDetail("error-log-detail-en-3x.png")

    @Test
    @Config(qualifiers = "ko-w320dp-h1040dp-notnight-xxhdpi")
    fun detailNarrow() = captureDetail("error-log-detail-light-320dp.png")

    @Test
    @Config(qualifiers = "ko-w320dp-h1040dp-night-xxhdpi")
    fun detailNarrowDark() = captureDetail("error-log-detail-dark-320dp.png", darkTheme = true)

    @Test
    @Config(qualifiers = "en-w320dp-h1040dp-notnight-xxhdpi")
    fun detailNarrowEnglish() = captureDetail("error-log-detail-en-320dp.png")

    @Test
    fun reportDialogLight() = captureReport("error-log-report-light-3x.png")

    @Test
    @Config(qualifiers = "ko-w412dp-h1040dp-night-xxhdpi")
    fun reportDialogDark() = captureReport("error-log-report-dark-3x.png", darkTheme = true)

    @Test
    @Config(qualifiers = "en-w412dp-h1040dp-notnight-xxhdpi")
    fun reportDialogEnglish() = captureReport("error-log-report-en-3x.png", english = true)

    @Test
    @Config(qualifiers = "ko-w320dp-h1040dp-notnight-xxhdpi")
    fun reportDialogNarrow() = captureReport("error-log-report-light-320dp.png")

    @Test
    @Config(qualifiers = "ko-w320dp-h1040dp-night-xxhdpi")
    fun reportDialogNarrowDark() = captureReport("error-log-report-dark-320dp.png", darkTheme = true)

    @Test
    @Config(qualifiers = "en-w320dp-h1040dp-notnight-xxhdpi")
    fun reportDialogNarrowEnglish() = captureReport("error-log-report-en-320dp.png", english = true)
}
