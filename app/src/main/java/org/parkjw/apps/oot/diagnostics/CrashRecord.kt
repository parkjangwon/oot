package org.parkjw.apps.oot.diagnostics

import android.content.Context
import android.os.Build
import kotlinx.serialization.Serializable
import org.parkjw.apps.oot.BuildConfig

@Serializable
data class CrashRecord(
    val id: String = "",
    val timestamp: Long,
    val kind: String,
    val summary: String = "",
    val detail: String = "",
    val appVersion: String = "",
    val appVersionCode: Int = 0,
    val androidVersion: String = "",
    val device: String = "",
    val thread: String? = null,
)

object CrashKind {
    const val CRASH = "crash"
    const val ANR = "anr"
    const val NATIVE_CRASH = "native_crash"
    const val EXCESSIVE_RESOURCE_USAGE = "excessive_resource_usage"
    const val SIGNALED = "signaled"
    const val LOW_MEMORY = "low_memory"
    const val INITIALIZATION_FAILURE = "initialization_failure"
    const val OTHER = "other"
}

data class DeviceInfo(
    val appVersion: String,
    val appVersionCode: Int,
    val androidVersion: String,
    val device: String,
) {
    val versionLabel: String get() = "$appVersion ($appVersionCode)"

    companion object {
        fun from(context: Context): DeviceInfo {
            val manufacturer = Build.MANUFACTURER?.trim().orEmpty()
            val model = Build.MODEL?.trim().orEmpty()
            val device = when {
                manufacturer.isBlank() -> model
                model.startsWith(manufacturer, ignoreCase = true) -> model
                else -> "$manufacturer $model"
            }.trim()
            return DeviceInfo(
                appVersion = BuildConfig.VERSION_NAME,
                appVersionCode = BuildConfig.VERSION_CODE,
                androidVersion = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                device = device,
            )
        }
    }
}
