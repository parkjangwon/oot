package org.parkjw.apps.oot.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.parkjw.apps.oot.data.model.ProviderType
import org.parkjw.apps.oot.ui.theme.OotTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.GraphicsMode.Mode.NATIVE
import java.io.File

// The provider list of the add-account dialog, opened. No accounts or keys involved.
// 320dp wide: wider width qualifiers freeze a dialog with a text field under Robolectric (robolectric#8460).
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(NATIVE)
@Config(sdk = [35], qualifiers = "w320dp-h760dp")
class AddAccountProviderListScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun providerListLight() = capture(darkTheme = false, fileName = "add-provider-list-light.png")

    @Test
    fun providerListDark() = capture(darkTheme = true, fileName = "add-provider-list-dark.png")

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(darkTheme: Boolean, fileName: String) {
        composeRule.setContent {
            OotTheme(darkTheme = darkTheme) {
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
        // The provider field; the display-name field below shows the same text.
        composeRule.onAllNodesWithText(ProviderType.ZAI.displayName)[0].performClick()
        composeRule.waitForIdle()
        captureScreenRoboImage(File(roborazziSystemPropertyOutputDirectory(), fileName))
    }
}
