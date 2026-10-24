package org.parkjw.apps.oot.diagnostics

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CrashLogStoreTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun store(
        maxRecords: Int = CrashLogStore.DEFAULT_MAX_RECORDS,
        maxBytes: Long = CrashLogStore.DEFAULT_MAX_BYTES,
    ) = CrashLogStore(
        directory = File(temporaryFolder.root, "crash-logs"),
        maxRecords = maxRecords,
        maxBytes = maxBytes,
    )

    private fun record(timestamp: Long, detail: String = "at demo.Stack.demo(Stack.kt:1)") = CrashRecord(
        timestamp = timestamp,
        kind = CrashKind.CRASH,
        summary = "java.lang.IllegalStateException: demo",
        detail = detail,
        appVersion = "1.4.0",
        appVersionCode = 10400,
        androidVersion = "15 (API 35)",
        device = "Google Pixel 8",
        thread = "main",
    )

    @Test
    fun keepsNewestTwentyRecords() {
        val store = store()
        (1L..25L).forEach { store.add(record(it)) }

        val records = store.list()
        assertEquals(20, records.size)
        assertEquals((25L downTo 6L).toList(), records.map { it.timestamp })
    }

    @Test
    fun keepsTotalSizeUnderTheCap() {
        val store = store(maxBytes = 3_000)
        (1L..10L).forEach { store.add(record(it, detail = "x".repeat(900))) }

        val files = File(temporaryFolder.root, "crash-logs").listFiles { file -> file.extension == "json" }.orEmpty()
        assertTrue(files.isNotEmpty())
        assertTrue("total ${files.sumOf { it.length() }}", files.sumOf { it.length() } <= 3_000)
        assertEquals(10L, store.list().first().timestamp)
    }

    @Test
    fun truncatesTheNewestRecordWhenItAloneExceedsTheCap() {
        val store = store(maxBytes = 2_000)
        store.add(record(1L, detail = "y".repeat(50_000)))

        val files = File(temporaryFolder.root, "crash-logs").listFiles { file -> file.extension == "json" }.orEmpty()
        assertEquals(1, files.size)
        assertTrue("file ${files.single().length()}", files.single().length() <= 2_000)
        assertTrue(store.list().single().detail.length < 50_000)
    }

    @Test
    fun keepsMetadataAndThreadAfterReload() {
        val store = store()
        store.add(record(42L))

        val loaded = store.list().single()
        assertEquals("1.4.0", loaded.appVersion)
        assertEquals(10400, loaded.appVersionCode)
        assertEquals("15 (API 35)", loaded.androidVersion)
        assertEquals("Google Pixel 8", loaded.device)
        assertEquals("main", loaded.thread)
        assertTrue(loaded.detail.contains("Stack.kt:1"))
        assertTrue(loaded.id.isNotBlank())
    }

    @Test
    fun ignoresCorruptFiles() {
        val directory = File(temporaryFolder.root, "crash-logs").apply { mkdirs() }
        File(directory, "broken.json").writeText("not json")
        val store = store()
        store.add(record(7L))

        assertEquals(listOf(7L), store.list().map { it.timestamp })
    }

    @Test
    fun clearRemovesEveryRecord() {
        val store = store()
        store.add(record(1L))
        store.add(record(2L))

        store.clear()
        assertTrue(store.list().isEmpty())
        assertFalse(store.hasTimestamp(2L))
    }

    @Test
    fun deleteRemovesOnlyThatRecord() {
        val store = store()
        val first = store.add(record(1L))
        val second = store.add(record(2L))

        assertTrue(store.delete(first.id))
        assertEquals(listOf(2L), store.list().map { it.timestamp })
        assertFalse(store.delete(first.id))

        store.delete(second.id)
        assertTrue(store.list().isEmpty())
    }

    @Test
    fun hasTimestampSeesStoredRecords() {
        val store = store()
        store.add(record(123L))

        assertTrue(store.hasTimestamp(123L))
        assertFalse(store.hasTimestamp(124L))
    }
}
