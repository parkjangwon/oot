package org.parkjw.apps.oot.service

import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.parkjw.apps.oot.OotApp
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The interval split and the periodic work registration. The worker body needs WorkManager's
 * restricted test APIs, which work-testing would provide; it is not a dependency, so [TokenRefreshWork]
 * is covered through its public entry points instead.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TokenRefreshWorkTest {

    private val app: OotApp = ApplicationProvider.getApplicationContext()

    @Test
    fun shortIntervalsRunInTheServiceLongerOnesUseWorkManager() {
        assertEquals(15, TokenRefreshWork.MIN_PERIODIC_MINUTES)
        assertTrue(TokenRefreshWork.usesShortInterval(1))
        assertTrue(TokenRefreshWork.usesShortInterval(14))
        assertFalse(TokenRefreshWork.usesShortInterval(15))
        assertFalse(TokenRefreshWork.usesShortInterval(60))
    }

    @Test
    fun enqueueRegistersCancellablePeriodicWork() {
        initWorkManager(app)

        TokenRefreshWork.enqueue(app, intervalMinutes = 30)

        awaitUntil { uniqueWorkInfos(app).size == 1 }
        assertEquals(WorkInfo.State.ENQUEUED, uniqueWorkInfos(app).single().state)

        TokenRefreshWork.cancel(app)
        awaitUntil { activeWorkInfos(app).isEmpty() }
    }

    @Test
    fun enqueueReplacesThePreviousPeriodicWork() {
        initWorkManager(app)

        TokenRefreshWork.enqueue(app, intervalMinutes = 15)
        awaitUntil { uniqueWorkInfos(app).singleOrNull()?.state == WorkInfo.State.ENQUEUED }
        val firstId = uniqueWorkInfos(app).single().id

        TokenRefreshWork.enqueue(app, intervalMinutes = 60)

        // CANCEL_AND_REENQUEUE drops the old work from the unique name, leaving one fresh request.
        awaitUntil {
            uniqueWorkInfos(app).singleOrNull()?.let {
                it.id != firstId && it.state == WorkInfo.State.ENQUEUED
            } == true
        }
    }
}
