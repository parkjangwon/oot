package org.parkjw.apps.oot.ui.screens

import android.view.WindowManager
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.parkjw.apps.oot.ui.theme.OotTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

// Credential input (#95): the add-account dialog must be excluded from screenshots and the recents preview.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ko-w320dp-h760dp")
class AddAccountSecureWindowTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun dialogWindowHasSecureFlag() {
        composeRule.setContent {
            OotTheme {
                AddAccountDialog(
                    onDismiss = {},
                    onStartOAuth = {},
                    onOpenExternalBrowser = {},
                    onStartXaiDeviceFlow = { _, _, _ -> },
                    onExchangeClaudeCode = { _, _, _ -> },
                    onStartOpenAiDeviceFlow = { _, _, _ -> },
                    onSaveAccount = {},
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNode(isDialog()).assertExists()

        val dialog = ShadowDialog.getLatestDialog()
        assertNotNull("add-account dialog should be shown", dialog)
        val window = dialog.window
        assertNotNull("add-account dialog window should exist", window)
        val flags = window!!.attributes.flags
        assertTrue(
            "dialog window must set FLAG_SECURE",
            flags and WindowManager.LayoutParams.FLAG_SECURE != 0,
        )
    }
}
