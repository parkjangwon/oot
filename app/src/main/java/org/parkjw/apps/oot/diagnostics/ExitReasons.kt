package org.parkjw.apps.oot.diagnostics

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import java.io.InputStream
import java.io.InputStreamReader

data class ProcessExit(
    val timestamp: Long,
    val reason: Int,
    val description: String? = null,
    val trace: String? = null,
    val processName: String? = null,
)

/** Historical process exits the system kept for this package (API 30+). */
object HistoricalExits {

    private const val MAX_EXITS = 20
    private const val MAX_TRACE_CHARS = 60_000

    fun read(context: Context): List<ProcessExit> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return emptyList()
        val manager = context.getSystemService(ActivityManager::class.java) ?: return emptyList()
        return runCatching {
            manager.getHistoricalProcessExitReasons(context.packageName, 0, MAX_EXITS).map { info ->
                ProcessExit(
                    timestamp = info.timestamp,
                    reason = info.reason,
                    description = info.description,
                    trace = if (info.reason == ApplicationExitInfo.REASON_ANR) readTrace(info.traceInputStream) else null,
                    processName = info.processName,
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun readTrace(input: InputStream?): String? {
        if (input == null) return null
        return input.use {
            val reader = InputStreamReader(it)
            val text = StringBuilder()
            val buffer = CharArray(8_192)
            var truncated = false
            while (text.length < MAX_TRACE_CHARS) {
                val read = reader.read(buffer, 0, minOf(buffer.size, MAX_TRACE_CHARS - text.length))
                if (read <= 0) break
                text.append(buffer, 0, read)
            }
            if (text.length >= MAX_TRACE_CHARS && reader.read() >= 0) truncated = true
            if (truncated) text.append("\n[truncated]")
            text.toString()
        }
    }
}

/**
 * Turns historical exits into records, one per timestamp. Running it on every app start is safe:
 * an exit already on disk is skipped. Only reasons that point at a real app failure are kept.
 */
class ExitReasonCollector(
    private val store: CrashLogStore,
    private val device: DeviceInfo,
    private val maxTraceChars: Int = 60_000,
) {

    fun collect(exits: List<ProcessExit>): Int {
        var added = 0
        exits.sortedBy { it.timestamp }.forEach { exit ->
            val kind = kindFor(exit.reason) ?: return@forEach
            if (store.hasTimestamp(exit.timestamp)) return@forEach
            store.add(
                CrashRecord(
                    timestamp = exit.timestamp,
                    kind = kind,
                    summary = exit.description?.trim()?.take(MAX_DESCRIPTION_CHARS).orEmpty(),
                    detail = detailFor(exit),
                    appVersion = device.appVersion,
                    appVersionCode = device.appVersionCode,
                    androidVersion = device.androidVersion,
                    device = device.device,
                    thread = null,
                ),
            )
            added++
        }
        return added
    }

    private fun detailFor(exit: ProcessExit): String = buildString {
        appendLine("reason: ${reasonName(exit.reason)}")
        exit.processName?.let { appendLine("process: $it") }
        exit.description?.takeIf { it.isNotBlank() }?.let { appendLine("description: $it") }
        val trace = exit.trace?.take(maxTraceChars)
        if (!trace.isNullOrBlank()) {
            appendLine()
            append(trace)
        }
    }.trimEnd()

    companion object {
        private const val MAX_DESCRIPTION_CHARS = 500

        /** Reason numbers are stable, so a stored record stays readable on every Android version. */
        fun kindFor(reason: Int): String? = when (reason) {
            ApplicationExitInfo.REASON_CRASH -> CrashKind.CRASH
            ApplicationExitInfo.REASON_ANR -> CrashKind.ANR
            ApplicationExitInfo.REASON_CRASH_NATIVE -> CrashKind.NATIVE_CRASH
            ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> CrashKind.EXCESSIVE_RESOURCE_USAGE
            ApplicationExitInfo.REASON_SIGNALED -> CrashKind.SIGNALED
            ApplicationExitInfo.REASON_LOW_MEMORY -> CrashKind.LOW_MEMORY
            ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> CrashKind.INITIALIZATION_FAILURE
            else -> null
        }

        fun reasonName(reason: Int): String = when (reason) {
            ApplicationExitInfo.REASON_CRASH -> "CRASH"
            ApplicationExitInfo.REASON_ANR -> "ANR"
            ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
            ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
            ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
            ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
            ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INITIALIZATION_FAILURE"
            else -> "OTHER"
        }
    }
}
