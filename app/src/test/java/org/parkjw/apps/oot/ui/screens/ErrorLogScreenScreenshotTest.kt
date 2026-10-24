package org.parkjw.apps.oot.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
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
                    onClear = {},
                )
            }
        }
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
        composeRule.onNodeWithText("GitHub 이슈로 신고").assertExists()
        composeRule.onNodeWithText("기록 지우기").assertExists()
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/error-log-records-light-3x.png")
    }

    @Test
    @Config(qualifiers = "ko-w412dp-h1040dp-night-xxhdpi")
    fun recordsDark() {
        setContent(darkTheme = true)
        composeRule.onNodeWithText("오류 기록").assertExists()
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/error-log-records-dark-3x.png")
    }
}
