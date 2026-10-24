package org.parkjw.apps.oot.service

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.parkjw.apps.oot.OotApp
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Crashes seen on Android 15/16 (#fgs): the dataSync time limit fires onTimeout, a background start is
 * refused, and startForeground throws once the daily budget is spent. None of them may kill the app.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TokenMonitorForegroundServiceCrashTest {

    private val app: OotApp = ApplicationProvider.getApplicationContext()

    @Before
    fun ensureWorkManager() {
        initWorkManager(app)
    }

    private fun createService() = Robolectric.buildService(
        TokenMonitorForegroundService::class.java,
        Intent(app, TokenMonitorForegroundService::class.java)
            .apply { action = TokenMonitorForegroundService.ACTION_START },
    ).create()

    /** onCreate already schedules periodic work; drop it so only the timeout can enqueue work again. */
    private fun clearScheduledWork() {
        TokenRefreshWork.cancel(app)
        awaitUntil { activeWorkInfos(app).isEmpty() }
    }

    @Test
    fun timeoutApi35StopsTheServiceAndLeavesPeriodicWork() {
        val controller = createService()
        val shadow = Shadows.shadowOf(controller.get())
        clearScheduledWork()

        controller.get().onTimeout(1, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)

        assertTrue(shadow.isForegroundStopped)
        assertTrue(shadow.getNotificationShouldRemoved())
        assertTrue(shadow.isStoppedBySelf)
        awaitUntil { activeWorkInfos(app).isNotEmpty() }

        controller.destroy()

        // onDestroy must not cancel the work it handed monitoring over to.
        awaitUntil { activeWorkInfos(app).isNotEmpty() }
    }

    @Test
    @Suppress("DEPRECATION")
    fun timeoutApi34StopsTheServiceAndLeavesPeriodicWork() {
        val controller = createService()
        val shadow = Shadows.shadowOf(controller.get())
        clearScheduledWork()

        controller.get().onTimeout(1)

        assertTrue(shadow.isForegroundStopped)
        assertTrue(shadow.isStoppedBySelf)
        awaitUntil { activeWorkInfos(app).isNotEmpty() }

        controller.destroy()
        awaitUntil { activeWorkInfos(app).isNotEmpty() }
    }

    @Test
    fun startForegroundFailureStopsTheServiceWithoutCrashingAndFallsBackToWork() {
        val controller = Robolectric.buildService(
            TokenMonitorForegroundService::class.java,
            Intent(app, TokenMonitorForegroundService::class.java)
                .apply { action = TokenMonitorForegroundService.ACTION_START },
        )
        val shadow = Shadows.shadowOf(controller.get())
        shadow.setThrowInStartForeground(
            IllegalStateException("Time limit already exhausted for foreground service type dataSync"),
        )

        controller.create()

        assertTrue(shadow.isStoppedBySelf)
        awaitUntil { activeWorkInfos(app).isNotEmpty() }

        controller.destroy()
        awaitUntil { activeWorkInfos(app).isNotEmpty() }
    }

    @Test
    fun refusedBackgroundStartFallsBackToWorkInsteadOfCrashing() {
        val refusingContext = object : ContextWrapper(app) {
            override fun startForegroundService(service: Intent): ComponentName? =
                throw IllegalStateException("startForegroundService() not allowed")
        }

        TokenMonitorForegroundService.startService(refusingContext)

        awaitUntil { activeWorkInfos(app).isNotEmpty() }
    }

    @Test
    fun applicationCreationDoesNotStartTheForegroundService() {
        assertTrue(app.preferences.isServiceEnabled.value)
        assertNull(Shadows.shadowOf(app).nextStartedService)
    }
}
