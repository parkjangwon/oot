package org.parkjw.apps.oot

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.parkjw.apps.oot.data.local.AccountPreferences
import org.parkjw.apps.oot.data.oauth.OAuthManager
import org.parkjw.apps.oot.data.oauth.TokenRevoker
import org.parkjw.apps.oot.data.remote.TokenSyncRepository
import org.parkjw.apps.oot.i18n.AppLanguage
import org.parkjw.apps.oot.i18n.AppLocale
import org.parkjw.apps.oot.i18n.LanguageStore
import org.parkjw.apps.oot.service.TokenNotificationManager
import java.util.concurrent.TimeUnit

class OotApp : Application() {

    lateinit var preferences: AccountPreferences
        private set

    lateinit var oauthManager: OAuthManager
        private set

    lateinit var tokenRevoker: TokenRevoker
        private set

    lateinit var repository: TokenSyncRepository
        private set

    lateinit var notificationManager: TokenNotificationManager
        private set

    lateinit var languageStore: LanguageStore
        private set

    /** Work that must outlive an Activity (e.g. the refresh after a language change recreates it). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()

        // Language first: everything below (notification channel, data-layer messages) reads strings.
        languageStore = LanguageStore(this)
        AppLocale.apply(this, startLanguage())

        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        val json = Json { ignoreUnknownKeys = true; isLenient = true }

        preferences = AccountPreferences(this)
        oauthManager = OAuthManager(this, client, json)
        tokenRevoker = TokenRevoker(TokenRevoker.http(client), clientIdFor = oauthManager::getClientIdForProvider)
        repository = TokenSyncRepository(preferences, oauthManager, client, json)
        notificationManager = TokenNotificationManager(this, hiddenAccountIds = { preferences.notificationHiddenAccountIds.value })

        // The monitor is started from the foreground (MainActivity) only: Android rejects a foreground
        // service start from the background, and a process restart or widget/boot start lands here first.
    }

    // The stored choice (Korean when unset), unless a language was picked in the system's per-app language
    // settings (Android 13+); that one wins and is stored so both stay in sync.
    private fun startLanguage(): AppLanguage = runBlocking {
        val stored = languageStore.current()
        val system = AppLocale.systemSelection(this@OotApp)
        if (system != null && system != stored) {
            languageStore.set(system)
            system
        } else {
            stored
        }
    }

    /** Saves [language], applies it, and redraws the notification; data-layer messages follow on the refresh. */
    fun setLanguage(language: AppLanguage) {
        // Applied synchronously so an activity recreated right after already reads the new language.
        AppLocale.apply(this, language)
        notificationManager.createNotificationChannel()
        if (preferences.isServiceEnabled.value) {
            notificationManager.updateNotification(repository.vendorUsages.value)
        }
        appScope.launch {
            languageStore.set(language)
            repository.refreshAll()
            if (preferences.isServiceEnabled.value) {
                notificationManager.updateNotification(repository.vendorUsages.value)
            }
        }
    }
}
