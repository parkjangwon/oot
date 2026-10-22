package org.parkjw.apps.oot.i18n

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.parkjw.apps.oot.OotApp
import org.parkjw.apps.oot.R
import org.parkjw.apps.oot.ui.MainActivity
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The activity and the notification follow the language applied from Settings, not the device locale (#74). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en")
class MainActivityLanguageTest {

    private val app: OotApp = ApplicationProvider.getApplicationContext()

    @After
    fun restoreDefault() {
        AppLocale.apply(app, AppLanguage.DEFAULT)
    }

    @Test
    fun koreanByDefaultOnAnEnglishDevice() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { assertEquals("설정", it.getString(R.string.settings_title)) }
        }
        assertEquals("새로고침", app.notificationManager.buildNotification(emptyList()).actions.single().title.toString())
    }

    @Test
    fun englishWhenSelected() {
        AppLocale.apply(app, AppLanguage.ENGLISH)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { assertEquals("Settings", it.getString(R.string.settings_title)) }
        }
        assertEquals("Refresh", app.notificationManager.buildNotification(emptyList()).actions.single().title.toString())
    }
}
