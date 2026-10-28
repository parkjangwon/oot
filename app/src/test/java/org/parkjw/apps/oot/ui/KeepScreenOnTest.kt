package org.parkjw.apps.oot.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeepScreenOnTest {

    @Test
    fun `the flag is on only while the setting is on and the app is in the foreground`() {
        assertTrue(shouldKeepScreenOn(settingEnabled = true, isForeground = true))
        assertFalse(shouldKeepScreenOn(settingEnabled = true, isForeground = false))
        assertFalse(shouldKeepScreenOn(settingEnabled = false, isForeground = true))
        assertFalse(shouldKeepScreenOn(settingEnabled = false, isForeground = false))
    }
}
