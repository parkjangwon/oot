package org.parkjw.apps.oot

import android.app.Application
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.parkjw.apps.oot.data.local.AccountPreferences
import org.parkjw.apps.oot.data.oauth.OAuthManager
import org.parkjw.apps.oot.data.remote.TokenSyncRepository
import org.parkjw.apps.oot.service.TokenMonitorForegroundService
import org.parkjw.apps.oot.service.TokenNotificationManager
import java.util.concurrent.TimeUnit

class OotApp : Application() {

    lateinit var preferences: AccountPreferences
        private set

    lateinit var oauthManager: OAuthManager
        private set

    lateinit var repository: TokenSyncRepository
        private set

    lateinit var notificationManager: TokenNotificationManager
        private set

    override fun onCreate() {
        super.onCreate()

        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        val json = Json { ignoreUnknownKeys = true; isLenient = true }

        preferences = AccountPreferences(this)
        oauthManager = OAuthManager(this, client, json)
        repository = TokenSyncRepository(preferences, oauthManager, client, json)
        notificationManager = TokenNotificationManager(this)

        if (preferences.isServiceEnabled.value) {
            TokenMonitorForegroundService.startService(this)
        }
    }
}
