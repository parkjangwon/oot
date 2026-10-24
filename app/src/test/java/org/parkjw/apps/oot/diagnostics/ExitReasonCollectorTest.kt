package org.parkjw.apps.oot.diagnostics

import android.app.ApplicationExitInfo
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ExitReasonCollectorTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val device = DeviceInfo("1.4.0", 10400, "15 (API 35)", "Google Pixel 8")

    private fun collector(store: CrashLogStore, maxTraceChars: Int = 200) =
        ExitReasonCollector(store, device, maxTraceChars)

    private fun store() = CrashLogStore(File(temporaryFolder.root, "crash-logs"))

    @Test
    fun storesOnlyFailureReasonsAndTruncatesTheAnrTrace() {
        val store = store()
        val trace = "trace-start " + "z".repeat(5_000) + " trace-end"
        val exits = listOf(
            ProcessExit(100L, ApplicationExitInfo.REASON_CRASH, description = "crash from system"),
            ProcessExit(200L, ApplicationExitInfo.REASON_ANR, description = "ANR in oot", trace = trace),
            ProcessExit(300L, ApplicationExitInfo.REASON_USER_REQUESTED, description = "user closed the app"),
            ProcessExit(400L, ApplicationExitInfo.REASON_CRASH_NATIVE, description = "native"),
            ProcessExit(500L, ApplicationExitInfo.REASON_SIGNALED, description = "killed"),
        )

        assertEquals(4, collector(store).collect(exits))

        val records = store.list().associateBy { it.timestamp }
        assertEquals(CrashKind.CRASH, records.getValue(100L).kind)
        assertEquals("crash from system", records.getValue(100L).summary)
        assertEquals(CrashKind.NATIVE_CRASH, records.getValue(400L).kind)
        assertEquals(CrashKind.SIGNALED, records.getValue(500L).kind)
        assertFalse(records.containsKey(300L))

        val anr = records.getValue(200L)
        assertEquals(CrashKind.ANR, anr.kind)
        assertTrue(anr.detail.contains("reason: ANR"))
        assertTrue(anr.detail.contains("trace-start"))
        assertFalse(anr.detail.contains("trace-end"))
    }

    @Test
    fun dedupesByTimestampAcrossRuns() {
        val store = store()
        val exits = listOf(
            ProcessExit(100L, ApplicationExitInfo.REASON_CRASH, description = "crash"),
            ProcessExit(100L, ApplicationExitInfo.REASON_CRASH, description = "crash duplicate"),
        )

        assertEquals(1, collector(store).collect(exits))
        assertEquals(1, store.list().size)
        assertEquals(0, collector(store).collect(exits))
        assertEquals(1, store.list().size)
    }

    @Test
    fun keepsDescriptionAndProcessNameInTheDetail() {
        val store = store()
        collector(store).collect(
            listOf(
                ProcessExit(
                    timestamp = 10L,
                    reason = ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE,
                    description = "too much CPU",
                    processName = "org.parkjw.apps.oot",
                ),
            ),
        )

        val record = store.list().single()
        assertEquals(CrashKind.EXCESSIVE_RESOURCE_USAGE, record.kind)
        assertTrue(record.detail.contains("reason: EXCESSIVE_RESOURCE_USAGE"))
        assertTrue(record.detail.contains("process: org.parkjw.apps.oot"))
        assertTrue(record.detail.contains("description: too much CPU"))
    }
}
