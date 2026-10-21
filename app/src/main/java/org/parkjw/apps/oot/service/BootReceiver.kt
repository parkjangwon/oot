package org.parkjw.apps.oot.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.parkjw.apps.oot.OotApp

class BootReceiver : BroadcastReceiver {
    constructor() : super()

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val app = context.applicationContext as? OotApp ?: return
            if (app.preferences.isServiceEnabled.value) {
                TokenMonitorForegroundService.startService(context)
            }
        }
    }
}
