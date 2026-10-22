package org.parkjw.apps.oot.service

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
 * The service's start/stop behavior: the foreground notification, the periodic vs in-service sync
 * switch driven by the refresh interval, and the STOP/REFRESH actions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TokenMonitorForegroundServiceTest {

    private val app: OotApp = ApplicationProvider.getApplicationContext()

    @Before
    fun ensureWorkManager() {
        initWorkManager(app)
    }

    private fun buildService(action: String) = Robolectric.buildService(
        TokenMonitorForegroundService::class.java,
        Intent(app, TokenMonitorForegroundService::class.java).apply { this.action = action },
    ).create()

    @Test
    fun createShowsTheForegroundNotificationAndSchedulesPeriodicRefresh() {
        val controller = buildService(TokenMonitorForegroundService.ACTION_START)
        val shadow = Shadows.shadowOf(controller.get())

        assertEquals(TokenNotificationManager.NOTIFICATION_ID, shadow.lastForegroundNotificationId)
        val notification = shadow.lastForegroundNotification
        assertEquals(TokenNotificationManager.CHANNEL_ID, notification!!.channelId)

        // The default 15-minute interval goes to WorkManager, not to an in-service loop.
        awaitUntil { uniqueWorkInfos(app).size == 1 }
        assertEquals(WorkInfo.State.ENQUEUED, uniqueWorkInfos(app).single().state)

        controller.destroy()
    }

    @Test
    fun shortIntervalCancelsPeriodicWorkForInServiceSync() {
        TokenRefreshWork.enqueue(app, intervalMinutes = 20)
        awaitUntil { uniqueWorkInfos(app).any { it.state == WorkInfo.State.ENQUEUED } }

        app.preferences.setRefreshInterval(5)
        val controller = buildService(TokenMonitorForegroundService.ACTION_START)

        // Under 15 minutes the service runs the loop itself instead of WorkManager.
        awaitUntil { activeWorkInfos(app).isEmpty() }

        controller.destroy()
    }

    @Test
    fun intervalChangesSwitchBetweenWorkManagerAndInServiceSync() {
        val controller = buildService(TokenMonitorForegroundService.ACTION_START)
        awaitUntil { uniqueWorkInfos(app).isNotEmpty() }

        app.preferences.setRefreshInterval(5)
        awaitUntil { activeWorkInfos(app).isEmpty() }

        app.preferences.setRefreshInterval(20)
        awaitUntil(describe = { "work=${uniqueWorkInfos(app)}" }) {
            uniqueWorkInfos(app).any { it.state == WorkInfo.State.ENQUEUED }
        }

        controller.destroy()
    }

    @Test
    fun stopActionRemovesTheNotificationCancelsWorkAndStopsSelf() {
        val controller = buildService(TokenMonitorForegroundService.ACTION_START)
        awaitUntil { uniqueWorkInfos(app).isNotEmpty() }
        val shadow = Shadows.shadowOf(controller.get())

        controller.get().onStartCommand(
            Intent(app, TokenMonitorForegroundService::class.java)
                .apply { action = TokenMonitorForegroundService.ACTION_STOP },
            0,
            2,
        )

        assertTrue(shadow.isForegroundStopped)
        assertTrue(shadow.getNotificationShouldRemoved())
        assertTrue(shadow.isStoppedBySelf)
        awaitUntil { activeWorkInfos(app).isEmpty() }

        controller.destroy()
    }

    @Test
    fun refreshActionRefetchesAndRepostsTheNotification() {
        val controller = buildService(TokenMonitorForegroundService.ACTION_START)
        val notifications = notificationManager(app)
        notifications.cancel(TokenNotificationManager.NOTIFICATION_ID)
        assertNull(Shadows.shadowOf(notifications).getNotification(TokenNotificationManager.NOTIFICATION_ID))

        controller.get().onStartCommand(
            Intent(app, TokenMonitorForegroundService::class.java)
                .apply { action = TokenMonitorForegroundService.ACTION_REFRESH },
            0,
            2,
        )

        awaitUntil {
            Shadows.shadowOf(notifications).getNotification(TokenNotificationManager.NOTIFICATION_ID) != null
        }

        controller.destroy()
    }

    @Test
    fun startHelperTargetsThisService() {
        // OotApp.onCreate already queues a start; drop it so this asserts the helper's own intent.
        Shadows.shadowOf(app).clearStartedServices()
        TokenMonitorForegroundService.startService(app)
        val started = Shadows.shadowOf(app).nextStartedService
        assertNotNull(started)
        assertEquals(TokenMonitorForegroundService::class.java.name, started.component?.className)
        assertEquals(TokenMonitorForegroundService.ACTION_START, started.action)
        assertNull(Shadows.shadowOf(app).nextStartedService)
    }
}
