package org.parkjw.apps.oot.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

class ExitBackPressPolicyTest {

    @Test
    fun firstBackPressReturnsFalseAndSecondWithin2sReturnsTrue() {
        var now = 1_000L
        val policy = ExitBackPressPolicy(clockMillis = { now })

        assertFalse(policy.onBackPressed())

        now = 2_500L
        assertTrue(policy.onBackPressed())
    }

    @Test
    fun secondBackPressAtExact2sBoundaryReturnsTrue() {
        var now = 1_000L
        val policy = ExitBackPressPolicy(clockMillis = { now })

        assertFalse(policy.onBackPressed())

        now = 3_000L
        assertTrue(policy.onBackPressed())
    }

    @Test
    fun secondBackPressAfter2sReturnsFalseAndRestartsWindow() {
        var now = 1_000L
        val policy = ExitBackPressPolicy(clockMillis = { now })

        assertFalse(policy.onBackPressed())

        now = 3_001L
        assertFalse(policy.onBackPressed())

        now = 4_500L
        assertTrue(policy.onBackPressed())
    }

    @Test
    fun stateResetsAfterExitDecision() {
        var now = 1_000L
        val policy = ExitBackPressPolicy(clockMillis = { now })

        assertFalse(policy.onBackPressed())
        now = 1_500L
        assertTrue(policy.onBackPressed())

        now = 2_000L
        assertFalse(policy.onBackPressed())
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityBackNavigationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun backOnSettingsReturnsToMainScreenAndDoubleBackOnMainExits() {
        var now = 10_000L
        val activity = composeRule.activity
        activity.exitBackPressPolicy = ExitBackPressPolicy(clockMillis = { now })
        ShadowToast.reset()

        // Navigate to Settings
        composeRule.onNodeWithContentDescription("설정").performClick()
        composeRule.onNodeWithText("GitHub").assertExists()

        // Back on Settings returns to main screen without toast or finish
        composeRule.runOnUiThread {
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
        assertFalse(activity.isFinishing)
        assertNull(ShadowToast.getTextOfLatestToast())
        composeRule.onNodeWithContentDescription("프로바이더 추가").assertExists()

        // First back on main screen shows toast and does not finish
        composeRule.runOnUiThread {
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
        assertFalse(activity.isFinishing)
        assertEquals(activity.getString(ExitBackPressPolicy.EXIT_PROMPT_MESSAGE), ShadowToast.getTextOfLatestToast())

        // Back after >2s shows toast again and does not finish
        ShadowToast.reset()
        now += 2_500L
        composeRule.runOnUiThread {
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
        assertFalse(activity.isFinishing)
        assertEquals(activity.getString(ExitBackPressPolicy.EXIT_PROMPT_MESSAGE), ShadowToast.getTextOfLatestToast())

        // Second back within 2s calls finish()
        now += 1_000L
        composeRule.runOnUiThread {
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
        assertTrue(activity.isFinishing)
    }
}
