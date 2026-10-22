package org.parkjw.apps.oot.i18n

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.parkjw.apps.oot.R
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppLocaleTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun restoreDefault() {
        AppLocale.apply(context, AppLanguage.DEFAULT)
    }

    @Test
    fun wrapReadsTheChosenLanguageWhateverTheDeviceLocale() {
        assertEquals("설정", AppLocale.wrap(context, AppLanguage.KOREAN).getString(R.string.settings_title))
        assertEquals("Settings", AppLocale.wrap(context, AppLanguage.ENGLISH).getString(R.string.settings_title))
    }

    @Test
    fun appTextFollowsTheAppliedLanguage() {
        AppLocale.apply(context, AppLanguage.KOREAN)
        assertEquals("확인 필요", AppText.get(R.string.status_needs_check))
        AppLocale.apply(context, AppLanguage.ENGLISH)
        assertEquals("Needs attention", AppText.get(R.string.status_needs_check))
        assertEquals("Claude needs attention", AppText.get(R.string.notification_vendor_needs_check, "Claude"))
    }

    @Test
    @Config(qualifiers = "en")
    fun koreanIsTheDefaultEvenOnAnEnglishDevice() {
        AppLocale.apply(context, AppLanguage.DEFAULT)
        assertEquals("확인 필요", AppText.get(R.string.status_needs_check))
    }
}
