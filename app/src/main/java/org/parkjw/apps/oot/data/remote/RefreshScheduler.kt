package org.parkjw.apps.oot.data.remote

import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Per-vendor refresh backoff after HTTP 429.
 * Base wait = [baseIntervalMs], doubles on each subsequent 429, capped at 60 minutes.
 * When Retry-After is present it is honored instead of the exponential value.
 */
class RefreshScheduler(
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val maxBackoffMs: Long = 60L * 60L * 1000L,
) {
    private data class Entry(val notBeforeMs: Long, val strike: Int)

    private val entries = mutableMapOf<String, Entry>()

    fun shouldFetch(key: String): Boolean {
        val entry = entries[key] ?: return true
        return clock() >= entry.notBeforeMs
    }

    fun notBeforeMs(key: String): Long? = entries[key]?.notBeforeMs

    fun onSuccess(key: String) {
        entries.remove(key)
    }

    fun onRateLimited(key: String, baseIntervalMs: Long, retryAfterMs: Long?) {
        val now = clock()
        val strike = (entries[key]?.strike ?: 0) + 1
        val shift = (strike - 1).coerceIn(0, 20)
        val exponential = (baseIntervalMs * (1L shl shift)).coerceAtMost(maxBackoffMs)
        val waitMs = when {
            retryAfterMs != null && retryAfterMs > 0L -> retryAfterMs.coerceAtMost(maxBackoffMs)
            else -> exponential.coerceAtLeast(0L)
        }
        entries[key] = Entry(now + waitMs, strike)
    }
}

/** Parses Retry-After as delta-seconds or HTTP-date; returns delay in ms from [nowMs]. */
fun parseRetryAfterMs(raw: String?, nowMs: Long = System.currentTimeMillis()): Long? {
    if (raw.isNullOrBlank()) return null
    val trimmed = raw.trim()
    trimmed.toLongOrNull()?.let { seconds ->
        return seconds.coerceAtLeast(0L) * 1000L
    }
    return try {
        val instant = Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(trimmed))
        (instant.toEpochMilli() - nowMs).coerceAtLeast(0L)
    } catch (_: DateTimeParseException) {
        null
    } catch (_: Exception) {
        null
    }
}
