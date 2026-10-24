package org.parkjw.apps.oot.ui.screens

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import java.time.Instant
import org.junit.Assert.assertEquals
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

    private val older = record.copy(
        id = "2",
        timestamp = Instant.parse("2026-10-23T08:12:00Z").toEpochMilli(),
        kind = CrashKind.ANR,
        summary = "ANR in org.parkjw.apps.oot",
        detail = "reason: ANR",
    )

    private fun setContent(
        records: List<CrashRecord> = listOf(record),
        onReport: (CrashRecord) -> Boolean = { true },
        onDeleteRecord: (CrashRecord) -> Unit = {},
        onClear: () -> Unit = {},
    ) {
        composeRule.setContent {
            OotTheme {
                ErrorLogScreen(
                    records = records,
                    onBack = {},
                    onExport = { true },
                    onReport = onReport,
                    onDeleteRecord = onDeleteRecord,
                    onClear = onClear,
                )
            }
        }
    }

    private fun openDetail(id: String = "1") {
        composeRule.onNodeWithTag("error_log_row_$id").performClick()
    }

    @Test
    fun detailShowsRedactedTextAndNoSecrets() {
        setContent()
        openDetail()

        composeRule.onNodeWithText("오류 상세").assertExists()
        composeRule.onNodeWithText("상세 내용").assertExists()
        composeRule.onNodeWithText("[REDACTED]", substring = true).assertExists()
        composeRule.onNodeWithText("[EMAIL]", substring = true).assertExists()
        composeRule.onNodeWithText("sk-demo-abcdefgh12345678", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("demo@example.com", substring = true).assertDoesNotExist()
    }

    @Test
    fun listScreenHasNoReportAction() {
        setContent()

        composeRule.onNodeWithTag("error_log_report_action").assertDoesNotExist()
        composeRule.onNodeWithText("GitHub 이슈로 신고").assertDoesNotExist()
        composeRule.onNodeWithText("기록 지우기").assertExists()
        composeRule.onNodeWithTag("error_log_delete_action").assertExists()
    }

    @Test
    fun reportShowsPreviewBeforeOpeningTheBrowser() {
        val reported = mutableListOf<CrashRecord>()
        setContent(onReport = { reported += it; true })
        openDetail()

        composeRule.onNodeWithTag("error_log_report_action").performClick()

        composeRule.onNodeWithText("공개 이슈로 올라가요").assertExists()
        composeRule.onNodeWithTag("error_log_report_preview").assertExists()
        composeRule.onNodeWithText("제목").assertExists()
        composeRule.onNodeWithText("본문").assertExists()
        composeRule.onNodeWithTag("error_log_report_title")
            .assertTextContains("[REDACTED]", substring = true)
        composeRule.onNodeWithTag("error_log_report_body")
            .assertTextContains("[REDACTED]", substring = true)
        composeRule.onNodeWithTag("error_log_report_body")
            .assertTextContains("[EMAIL]", substring = true)
        composeRule.onNodeWithText("sk-demo-abcdefgh12345678", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("demo@example.com", substring = true).assertDoesNotExist()
        assertTrue(reported.isEmpty())

        composeRule.onNodeWithText("브라우저에서 열기").performClick()
        assertEquals(listOf(record.id), reported.map { it.id })
    }

    @Test
    fun reportCancelDoesNotOpenTheBrowser() {
        val reported = mutableListOf<CrashRecord>()
        setContent(onReport = { reported += it; true })
        openDetail()

        composeRule.onNodeWithTag("error_log_report_action").performClick()
        composeRule.onNodeWithText("취소").performClick()

        composeRule.onNodeWithTag("error_log_report_preview").assertDoesNotExist()
        assertTrue(reported.isEmpty())
    }

    @Test
    fun deleteRecordAsksForConfirmationAndDeletesOnlyThatRecord() {
        val deleted = mutableListOf<CrashRecord>()
        setContent(records = listOf(record, older), onDeleteRecord = { deleted += it })
        openDetail("1")

        composeRule.onNodeWithTag("error_log_delete_action").performClick()
        composeRule.onNodeWithText("이 기록을 지울까요?").assertExists()
        assertTrue(deleted.isEmpty())

        composeRule.onNodeWithText("지우기").performClick()
        assertEquals(listOf("1"), deleted.map { it.id })
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
