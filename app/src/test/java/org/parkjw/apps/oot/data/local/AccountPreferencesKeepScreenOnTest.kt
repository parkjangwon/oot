package org.parkjw.apps.oot.data.local

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

// A fresh AccountPreferences on the same application stands in for an app restart.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AccountPreferencesKeepScreenOnTest {

    private fun preferences() = testAccountPreferences(RuntimeEnvironment.getApplication())

    @Test
    fun `keeping the screen on is off by default`() {
        assertFalse(preferences().isKeepScreenOnEnabled.value)
    }

    @Test
    fun `a change survives a restart`() {
        val prefs = preferences()
        prefs.setKeepScreenOnEnabled(true)
        assertTrue(prefs.isKeepScreenOnEnabled.value)
        assertTrue(preferences().isKeepScreenOnEnabled.value)
    }

    @Test
    fun `turning it back off survives a restart`() {
        val prefs = preferences()
        prefs.setKeepScreenOnEnabled(true)
        prefs.setKeepScreenOnEnabled(false)
        assertFalse(preferences().isKeepScreenOnEnabled.value)
    }
}
