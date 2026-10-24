package org.parkjw.apps.oot.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import java.time.Instant
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.parkjw.apps.oot.diagnostics.CrashKind
import org.parkjw.apps.oot.diagnostics.CrashRecord
import org.parkjw.apps.oot.ui.theme.OotTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "ko-w412dp-h1040dp")
class ErrorLogScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val record = CrashRecord(
        id = "1",
        timestamp = Instant.parse("2026-10-24T08:12:00Z").toEpochMilli(),
        kind = CrashKind.CRASH,
        summary = "java.lang.IllegalStateException: token sk-demo-abcdefgh12345678",
        detail = "Authorization: Bearer sk-demo-abcdefgh12345678\ncontact demo@example.com",
        appVersion = "1.4.0",
        appVersionCode = 10400,
        androidVersion = "15 (API 35)",
        device = "Google Pixel 8",
        thread = "main",
    )

    private fun setContent(onClear: () -> Unit = {}) {
        composeRule.setContent {
            OotTheme {
                ErrorLogScreen(
                    records = listOf(record),
                    onBack = {},
                    onExport = { true },
                    onReport = { true },
                    onClear = onClear,
                )
            }
        }
    }

    @Test
    fun detailShowsRedactedTextAndNoSecrets() {
        setContent()
        composeRule.onNodeWithTag("error_log_row_1").performClick()

        composeRule.onNodeWithText("오류 상세").assertExists()
        composeRule.onNodeWithText("상세 내용").assertExists()
        composeRule.onNodeWithText("[REDACTED]", substring = true).assertExists()
        composeRule.onNodeWithText("[EMAIL]", substring = true).assertExists()
        composeRule.onNodeWithText("sk-demo-abcdefgh12345678", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("demo@example.com", substring = true).assertDoesNotExist()
    }

    @Test
    fun clearAsksForConfirmationFirst() {
        var cleared = false
        setContent(onClear = { cleared = true })

        composeRule.onNodeWithText("기록 지우기").performClick()
        composeRule.onNodeWithText("기록을 지울까요?").assertExists()
        assertTrue(!cleared)

        composeRule.onNodeWithText("지우기").performClick()
        assertTrue(cleared)
    }
}
