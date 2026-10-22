package org.parkjw.apps.oot.service

import android.app.NotificationManager
import android.content.Context
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * WorkManager may already be initialized by the manifest InitializationProvider under Robolectric;
 * otherwise initialize it here. The default executors are kept: WorkManager's cancel-and-reenqueue
 * path deadlocks on a direct executor.
 */
internal fun initWorkManager(context: Context): WorkManager =
    try {
        WorkManager.getInstance(context)
    } catch (e: IllegalStateException) {
        WorkManager.initialize(context, Configuration.Builder().build())
        WorkManager.getInstance(context)
    }

internal fun uniqueWorkInfos(context: Context): List<WorkInfo> =
    WorkManager.getInstance(context)
        .getWorkInfosForUniqueWork(TokenRefreshWork.UNIQUE_NAME)
        .get(10, TimeUnit.SECONDS)

/** Work that WorkManager would still run; CANCELLED leftovers and an empty list both count as stopped. */
internal fun activeWorkInfos(context: Context): List<WorkInfo> =
    uniqueWorkInfos(context).filter {
        it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING
    }

internal fun notificationManager(context: Context): NotificationManager =
    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

internal fun awaitUntil(
    timeoutMs: Long = 10_000,
    describe: () -> String = { "" },
    condition: () -> Boolean,
) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (!condition()) {
        if (System.currentTimeMillis() >= deadline) {
            error("condition not met within ${timeoutMs}ms ${describe()}".trim())
        }
        Thread.sleep(25)
    }
}
