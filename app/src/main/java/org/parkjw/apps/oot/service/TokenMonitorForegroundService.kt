package org.parkjw.apps.oot.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.parkjw.apps.oot.OotApp

class TokenMonitorForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var syncJob: Job? = null
    private var intervalWatchJob: Job? = null

    private val app by lazy { applicationContext as OotApp }

    override fun onCreate() {
        super.onCreate()
        val initialNotification = app.notificationManager.buildNotification(app.repository.vendorUsages.value)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                TokenNotificationManager.NOTIFICATION_ID,
                initialNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(TokenNotificationManager.NOTIFICATION_ID, initialNotification)
        }

        intervalWatchJob = serviceScope.launch {
            app.preferences.refreshIntervalMinutes.collectLatest { minutes ->
                applySyncStrategy(minutes)
            }
        }

        // A Settings toggle re-renders at once, no fetch. Tied to the service so it can't re-post a notification that is off.
        serviceScope.launch {
            app.preferences.notificationHiddenAccountIds.drop(1).collect {
                app.notificationManager.updateNotification(app.repository.vendorUsages.value)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_REFRESH -> {
                serviceScope.launch { refreshOnce() }
            }
            ACTION_STOP -> {
                stopPeriodicSync()
                TokenRefreshWork.cancel(this)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            else -> {
                serviceScope.launch { refreshOnce() }
            }
        }
        return START_STICKY
    }

    private fun applySyncStrategy(intervalMinutes: Int) {
        val minutes = intervalMinutes.coerceAtLeast(1)
        stopPeriodicSync()
        if (TokenRefreshWork.usesShortInterval(minutes)) {
            TokenRefreshWork.cancel(this)
            syncJob = serviceScope.launch {
                while (isActive) {
                    delay(minutes * 60_000L)
                    refreshOnce()
                }
            }
        } else {
            TokenRefreshWork.enqueue(this, minutes)
        }
    }

    private fun stopPeriodicSync() {
        syncJob?.cancel()
        syncJob = null
    }

    private suspend fun refreshOnce() {
        app.repository.refreshAll()
        app.notificationManager.updateNotification(app.repository.vendorUsages.value)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopPeriodicSync()
        intervalWatchJob?.cancel()
        TokenRefreshWork.cancel(this)
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "org.parkjw.apps.oot.action.START"
        const val ACTION_STOP = "org.parkjw.apps.oot.action.STOP"
        const val ACTION_REFRESH = "org.parkjw.apps.oot.action.REFRESH"

        fun startService(context: Context) {
            val intent = Intent(context, TokenMonitorForegroundService::class.java).apply {
                action = ACTION_START
            }
            context.startForegroundService(intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, TokenMonitorForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun triggerRefresh(context: Context) {
            val intent = Intent(context, TokenMonitorForegroundService::class.java).apply {
                action = ACTION_REFRESH
            }
            context.startService(intent)
        }
    }
}
