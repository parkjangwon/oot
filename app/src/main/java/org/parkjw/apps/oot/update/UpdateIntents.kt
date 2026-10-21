package org.parkjw.apps.oot.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import java.io.File

const val UPDATE_CACHE_DIR = "updates"
private const val APK_MIME = "application/vnd.android.package-archive"

private fun Context.startSafely(intent: Intent): Boolean =
    try {
        startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    } catch (e: IllegalArgumentException) {
        false
    }

fun Context.launchApkInstaller(apk: File): Boolean {
    val uri = try {
        FileProvider.getUriForFile(this, "$packageName.updates", apk)
    } catch (e: IllegalArgumentException) {
        return false
    }
    return startSafely(
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
    )
}

fun Context.openInstallPermissionSettings(): Boolean =
    startSafely(
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.fromParts("package", packageName, null)),
    )

fun Context.openBrowsableUrl(url: String): Boolean =
    startSafely(Intent(Intent.ACTION_VIEW, url.toUri()).addCategory(Intent.CATEGORY_BROWSABLE))

fun Context.openReleasePage(url: String): Boolean = openBrowsableUrl(url)
