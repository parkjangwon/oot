package org.parkjw.apps.oot.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.parkjw.apps.oot.OotApp
import java.util.concurrent.TimeUnit

class TokenRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? OotApp ?: return Result.failure()
        app.repository.refreshAll()
        if (app.preferences.isServiceEnabled.value) {
            app.notificationManager.updateNotification(app.repository.vendorUsages.value)
        }
        return Result.success()
    }
}

object TokenRefreshWork {
    const val UNIQUE_NAME = "oot_token_refresh"
    const val MIN_PERIODIC_MINUTES = 15

    fun usesShortInterval(minutes: Int): Boolean = minutes < MIN_PERIODIC_MINUTES

    fun enqueue(context: Context, intervalMinutes: Int) {
        val minutes = intervalMinutes.coerceAtLeast(MIN_PERIODIC_MINUTES).toLong()
        val request = PeriodicWorkRequestBuilder<TokenRefreshWorker>(minutes, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
    }
}
