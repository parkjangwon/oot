package org.parkjw.apps.oot.ui

/** The window flag is on only while the setting is on and the app is in the foreground. */
internal fun shouldKeepScreenOn(settingEnabled: Boolean, isForeground: Boolean): Boolean =
    settingEnabled && isForeground
