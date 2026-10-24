package org.parkjw.apps.oot.diagnostics

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import org.parkjw.apps.oot.R
import org.parkjw.apps.oot.update.openBrowsableUrl

private const val EXPORT_DIR = "error-log"

/** Writes a redacted .txt to the cache and hands it to an app chooser through the crash-log provider. */
fun Context.shareErrorLog(records: List<CrashRecord>): Boolean {
    if (records.isEmpty()) return false
    val directory = File(cacheDir, EXPORT_DIR).apply { mkdirs() }
    directory.listFiles()?.forEach { it.delete() }
    val file = File(directory, "oot-error-log.txt").apply {
        writeText(ErrorReport.exportText(records))
    }
    val uri = try {
        FileProvider.getUriForFile(this, "$packageName.crashlogs", file)
    } catch (e: IllegalArgumentException) {
        return false
    }
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.error_log_share_subject))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    return try {
        startActivity(Intent.createChooser(send, getString(R.string.error_log_export)))
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }
}

fun Context.openCrashIssue(record: CrashRecord): Boolean =
    openBrowsableUrl(ErrorReport.issueUrl(record))
