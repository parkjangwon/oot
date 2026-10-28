package org.parkjw.apps.oot.ui

import android.view.WindowManager
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.parkjw.apps.oot.OotApp
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private fun MainActivity.hasKeepScreenOnFlag(): Boolean =
    window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0

/** The window flag follows the setting through every foreground change (#148). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "ko-w412dp-h915dp")
class MainActivityKeepScreenOnTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun theFlagFollowsTheSettingAcrossForegroundChanges() {
        assertFalse(composeRule.activity.hasKeepScreenOnFlag())

        composeRule.onNodeWithContentDescription("설정").performClick()
        composeRule.onNodeWithText("화면 켜둠").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertTrue(composeRule.activity.hasKeepScreenOnFlag())

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        assertFalse(composeRule.activity.hasKeepScreenOnFlag())

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.waitForIdle()
        assertTrue(composeRule.activity.hasKeepScreenOnFlag())

        composeRule.onNodeWithText("화면 켜둠").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertFalse(composeRule.activity.hasKeepScreenOnFlag())
    }
}

/** A stored "on" setting must apply from the first resume, not only after the switch is toggled. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "ko-w412dp-h915dp")
class MainActivityKeepScreenOnColdStartTest {

    private val app: OotApp = ApplicationProvider.getApplicationContext()

    @Test
    fun aStoredOnSettingKeepsTheScreenOnFromTheFirstResume() {
        app.preferences.setKeepScreenOnEnabled(true)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { assertTrue(it.hasKeepScreenOnFlag()) }

            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.onActivity { assertFalse(it.hasKeepScreenOnFlag()) }

            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { assertTrue(it.hasKeepScreenOnFlag()) }
        }
    }
}
