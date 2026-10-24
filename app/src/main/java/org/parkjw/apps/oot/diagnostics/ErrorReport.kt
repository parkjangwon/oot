package org.parkjw.apps.oot.diagnostics

import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class IssuePreview(val title: String, val body: String)

object ErrorReport {

    const val ISSUE_NEW_URL = "https://github.com/parkjangwon/oot/issues/new"
    const val MAX_ISSUE_URL_LENGTH = 6_000

    private const val MAX_STACK_CHARS = 1_600
    private val timeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun exportText(records: List<CrashRecord>): String = buildString {
        records.sortedByDescending { it.timestamp }.forEachIndexed { index, record ->
            if (index > 0) appendLine()
            appendLine("===== ${formatTime(record.timestamp)} =====")
            appendLine("Kind: ${record.kind}")
            appendLine("App: ${record.appVersion} (${record.appVersionCode})")
            appendLine("Android: ${record.androidVersion}")
            appendLine("Device: ${record.device}")
            record.thread?.let { appendLine("Thread: $it") }
            if (record.summary.isNotBlank()) appendLine("Reason: ${Redactor.redact(record.summary)}")
            appendLine("---")
            appendLine(Redactor.redact(record.detail))
        }
    }

    fun issuePreview(record: CrashRecord): IssuePreview {
        val title = Redactor.redact(
            "[${record.kind}] " + record.summary.ifBlank { "oot error" },
        ).take(120)
        val summary = Redactor.redact(record.summary)
        val stack = Redactor.redact(record.detail)
        val header = buildString {
            appendLine("### What happened")
            appendLine(summary.ifBlank { record.kind })
            appendLine()
            appendLine("### App")
            appendLine("- Version: ${record.appVersion} (${record.appVersionCode})")
            appendLine("- Android: ${record.androidVersion}")
            appendLine("- Device: ${record.device}")
            record.thread?.let { appendLine("- Thread: $it") }
            appendLine()
            appendLine("### Stack trace")
            appendLine("```")
        }
        val footer = "\n```\n"

        var limit = MAX_STACK_CHARS
        while (true) {
            val preview = IssuePreview(title, header + stack.take(limit) + footer)
            if (issueUrl(preview).length <= MAX_ISSUE_URL_LENGTH || limit == 0) return preview
            limit = (limit / 2).coerceAtMost(MAX_STACK_CHARS)
        }
    }

    fun issueUrl(record: CrashRecord): String = issueUrl(issuePreview(record))

    private fun issueUrl(preview: IssuePreview): String =
        "$ISSUE_NEW_URL?title=${encode(preview.title)}&body=${encode(preview.body)}"

    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

    private fun formatTime(timestamp: Long): String =
        timeFormat.format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))
}
