package org.parkjw.apps.oot.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat

@Suppress("DEPRECATION")
fun Context.installedVersionName(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager
            .getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            .versionName.orEmpty()
    } else {
        packageManager
            .getPackageInfo(packageName, 0)
            .versionName.orEmpty()
    }

@Suppress("DEPRECATION")
fun Context.installedVersionCode(): Long =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager
            .getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            .longVersionCode
    } else {
        PackageInfoCompat.getLongVersionCode(packageManager.getPackageInfo(packageName, 0))
    }
