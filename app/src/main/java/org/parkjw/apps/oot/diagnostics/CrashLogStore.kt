package org.parkjw.apps.oot.diagnostics

import java.io.File
import kotlinx.serialization.json.Json

/**
 * One JSON file per record under `filesDir/crash-logs/`. Newest [maxRecords] win and the directory
 * never grows past [maxBytes]; the newest record is truncated if it alone would exceed that.
 */
class CrashLogStore(
    private val directory: File,
    private val maxRecords: Int = DEFAULT_MAX_RECORDS,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
    private val maxDetailChars: Int = DEFAULT_MAX_DETAIL_CHARS,
) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Synchronized
    fun add(record: CrashRecord): CrashRecord {
        directory.mkdirs()
        val prepared = fit(record.copy(
            summary = record.summary.take(MAX_SUMMARY_CHARS),
            detail = record.detail.take(maxDetailChars),
        ))
        val file = write(prepared)
        rotate()
        return prepared.copy(id = file.nameWithoutExtension)
    }

    @Synchronized
    fun list(): List<CrashRecord> = files()
        .mapNotNull { file ->
            runCatching {
                val record = json.decodeFromString<CrashRecord>(file.readText())
                record.copy(id = file.nameWithoutExtension)
            }.getOrNull()
        }
        .sortedByDescending { it.timestamp }

    @Synchronized
    fun hasTimestamp(timestamp: Long): Boolean =
        files().any { file ->
            runCatching { json.decodeFromString<CrashRecord>(file.readText()).timestamp == timestamp }
                .getOrDefault(false)
        }

    @Synchronized
    fun clear() {
        files().forEach { it.delete() }
        directory.listFiles { file -> file.name.endsWith(TEMP_SUFFIX) }?.forEach { it.delete() }
    }

    private fun files(): List<File> =
        directory.listFiles { file -> file.isFile && file.name.endsWith(FILE_SUFFIX) }
            ?.toList()
            .orEmpty()

    private fun serialize(record: CrashRecord): String = json.encodeToString(CrashRecord.serializer(), record)

    private fun write(record: CrashRecord): File {
        val base = "${record.timestamp}-${record.kind.ifBlank { CrashKind.OTHER }}"
        var candidate = File(directory, "$base$FILE_SUFFIX")
        var index = 1
        while (candidate.exists()) {
            candidate = File(directory, "$base-$index$FILE_SUFFIX")
            index++
        }
        val temp = File(directory, "${candidate.name}$TEMP_SUFFIX")
        temp.writeText(serialize(record))
        if (!temp.renameTo(candidate)) {
            candidate.writeText(serialize(record))
            temp.delete()
        }
        return candidate
    }

    private fun rotate() {
        val ordered = files()
            .mapNotNull { file -> runCatching { file to json.decodeFromString<CrashRecord>(file.readText()) }.getOrNull() }
            .sortedByDescending { (_, record) -> record.timestamp }

        ordered.drop(maxRecords).forEach { (file, _) -> file.delete() }

        val kept = ordered.take(maxRecords).toMutableList()
        var total = kept.sumOf { (file, _) -> file.length() }
        while (total > maxBytes && kept.size > 1) {
            val (file, _) = kept.removeAt(kept.lastIndex)
            total -= file.length()
            file.delete()
        }
    }

    /** Keeps the newest record inside [maxBytes] by shortening its detail, without breaking the JSON. */
    private fun fit(record: CrashRecord): CrashRecord {
        if (serialize(record).toByteArray().size <= maxBytes) return record

        var low = 0
        var high = record.detail.length
        var best = record.copy(detail = "")
        while (low <= high) {
            val middle = (low + high) / 2
            val candidate = record.copy(detail = record.detail.take(middle))
            if (serialize(candidate).toByteArray().size <= maxBytes) {
                best = candidate
                low = middle + 1
            } else {
                high = middle - 1
            }
        }
        return best
    }

    companion object {
        const val DIR_NAME = "crash-logs"
        const val DEFAULT_MAX_RECORDS = 20
        const val DEFAULT_MAX_BYTES = 1_000_000L
        const val DEFAULT_MAX_DETAIL_CHARS = 200_000
        private const val MAX_SUMMARY_CHARS = 500
        private const val FILE_SUFFIX = ".json"
        private const val TEMP_SUFFIX = ".tmp"
    }
}
