package org.parkjw.apps.oot.service

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.parkjw.apps.oot.OotApp
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/** Boot/package-replace restarts the monitor only when the user left the service on. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BootReceiverTest {

    private val app: OotApp = ApplicationProvider.getApplicationContext()

    @Before
    fun drainServicesStartedByAppCreation() {
        Shadows.shadowOf(app).clearStartedServices()
    }

    private fun receive(action: String?) {
        val intent = Intent(app, BootReceiver::class.java).apply { this.action = action }
        BootReceiver().onReceive(app, intent)
    }

    @Test
    fun bootCompletedStartsTheMonitorWhenEnabled() {
        receive(Intent.ACTION_BOOT_COMPLETED)

        val started = Shadows.shadowOf(app).nextStartedService
        assertEquals(TokenMonitorForegroundService::class.java.name, started.component?.className)
        assertEquals(TokenMonitorForegroundService.ACTION_START, started.action)
    }

    @Test
    fun packageReplaceStartsTheMonitorWhenEnabled() {
        receive(Intent.ACTION_MY_PACKAGE_REPLACED)

        assertEquals(
            TokenMonitorForegroundService.ACTION_START,
            Shadows.shadowOf(app).nextStartedService?.action,
        )
    }

    @Test
    fun bootIsIgnoredWhenTheServiceIsDisabled() {
        app.preferences.setServiceEnabled(false)

        receive(Intent.ACTION_BOOT_COMPLETED)

        assertNull(Shadows.shadowOf(app).nextStartedService)
    }

    @Test
    fun unrelatedIntentsAreIgnored() {
        receive(Intent.ACTION_TIME_TICK)
        receive(Intent.ACTION_LOCALE_CHANGED)

        assertNull(Shadows.shadowOf(app).nextStartedService)
    }
}
