package org.parkjw.apps.oot

import android.app.Application
import org.parkjw.apps.oot.data.local.AccountPreferences
import org.parkjw.apps.oot.data.remote.TokenSyncRepository
import org.parkjw.apps.oot.service.TokenMonitorForegroundService
import org.parkjw.apps.oot.service.TokenNotificationManager

class OotApp : Application() {

    lateinit var preferences: AccountPreferences
        private set

    lateinit var repository: TokenSyncRepository
        private set

    lateinit var notificationManager: TokenNotificationManager
        private set

    override fun onCreate() {
        super.onCreate()
        preferences = AccountPreferences(this)
        repository = TokenSyncRepository(preferences)
        notificationManager = TokenNotificationManager(this)

        if (preferences.isServiceEnabled.value) {
            TokenMonitorForegroundService.startService(this)
        }
    }
}
