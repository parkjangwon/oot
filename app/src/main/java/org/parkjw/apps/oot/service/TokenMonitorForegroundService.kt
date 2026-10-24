package org.parkjw.apps.oot.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.ServiceCompat
import androidx.work.ExistingPeriodicWorkPolicy
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

    /** Whether this instance became a foreground service; a failed start must not run the monitor. */
    private var foregroundStarted = false

    /** Set when the service hands monitoring over to periodic work, which onDestroy must then not cancel. */
    private var handedOverToWork = false

    private val app by lazy { applicationContext as OotApp }

    override fun onCreate() {
        super.onCreate()
        val initialNotification = app.notificationManager.buildNotification(app.repository.vendorUsages.value)

        try {
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
        } catch (e: IllegalStateException) {
            // The dataSync budget is spent or Android refused the background start: work keeps refreshing.
            handOverToWork()
            return
        }
        foregroundStarted = true

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
        if (!foregroundStarted) return START_NOT_STICKY
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

    // API 34: the foreground service time limit was reached.
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onTimeout(startId: Int) {
        handOverToWork()
    }

    // API 35: the daily dataSync budget (or another type's limit) is exhausted.
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun onTimeout(startId: Int, fgsType: Int) {
        handOverToWork()
    }

    /**
     * Ends the foreground service without crashing and leaves periodic work behind, so refreshes continue
     * until the app runs in the foreground and can start the service again.
     */
    private fun handOverToWork() {
        handedOverToWork = true
        stopPeriodicSync()
        intervalWatchJob?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        TokenRefreshWork.enqueue(
            this,
            app.preferences.refreshIntervalMinutes.value,
            ExistingPeriodicWorkPolicy.KEEP,
        )
        stopSelf()
    }

    private suspend fun refreshOnce() {
        app.repository.refreshAll()
        app.notificationManager.updateNotification(app.repository.vendorUsages.value)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopPeriodicSync()
        intervalWatchJob?.cancel()
        if (!handedOverToWork) TokenRefreshWork.cancel(this)
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "org.parkjw.apps.oot.action.START"
        const val ACTION_STOP = "org.parkjw.apps.oot.action.STOP"
        const val ACTION_REFRESH = "org.parkjw.apps.oot.action.REFRESH"

        /**
         * Android only allows a new foreground service from the foreground; a background start (boot, worker,
         * widget, process restart) throws. Fall back to periodic work instead of crashing the app.
         */
        fun startService(context: Context) {
            val intent = Intent(context, TokenMonitorForegroundService::class.java).apply {
                action = ACTION_START
            }
            try {
                context.startForegroundService(intent)
            } catch (e: IllegalStateException) {
                (context.applicationContext as? OotApp)?.let {
                    TokenRefreshWork.enqueue(
                        context,
                        it.preferences.refreshIntervalMinutes.value,
                        ExistingPeriodicWorkPolicy.KEEP,
                    )
                }
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
