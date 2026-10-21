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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.parkjw.apps.oot.OotApp

class TokenMonitorForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var syncJob: Job? = null

    private val app by lazy { applicationContext as OotApp }

    override fun onCreate() {
        super.onCreate()
        val initialNotification = app.notificationManager.buildNotification(app.repository.statuses.value)

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

        startPeriodicSync()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_REFRESH -> {
                serviceScope.launch {
                    val updated = app.repository.refreshAll()
                    app.notificationManager.updateNotification(updated)
                }
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            else -> {
                // Trigger immediate initial check
                serviceScope.launch {
                    val updated = app.repository.refreshAll()
                    app.notificationManager.updateNotification(updated)
                }
            }
        }
        return START_STICKY
    }

    private fun startPeriodicSync() {
        syncJob?.cancel()
        syncJob = serviceScope.launch {
            while (isActive) {
                val intervalMinutes = app.preferences.refreshIntervalMinutes.value.coerceAtLeast(1)
                delay(intervalMinutes * 60 * 1000L)

                val updated = app.repository.refreshAll()
                app.notificationManager.updateNotification(updated)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        const val ACTION_START = "org.parkjw.apps.oot.action.START"
        const val ACTION_STOP = "org.parkjw.apps.oot.action.STOP"
        const val ACTION_REFRESH = "org.parkjw.apps.oot.action.REFRESH"

        fun startService(context: Context) {
            val intent = Intent(context, TokenMonitorForegroundService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
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
