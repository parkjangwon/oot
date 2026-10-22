package org.parkjw.apps.oot.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
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

// Add-account dialog copy (#85, #86): 해요체 helper texts and sign-in boxes without emojis.
// 320dp wide: wider width qualifiers freeze a dialog with a text field under Robolectric (robolectric#8460).
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(NATIVE)
@Config(sdk = [35], qualifiers = "ko-w320dp-h760dp")
class AddAccountCopyScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun zaiHelperLight() {
        capture(ProviderType.ZAI, darkTheme = false, fileName = "add-account-zai-light.png")
        composeRule.onNodeWithText("확인해요.", substring = true).assertExists()
    }

    @Test
    fun claudeSignInLight() {
        capture(ProviderType.CLAUDE, darkTheme = false, fileName = "add-account-claude-light.png")
        composeRule.onNodeWithText("브라우저로 Claude 로그인").assertExists()
    }

    @Test
    fun claudeSignInDark() = capture(ProviderType.CLAUDE, darkTheme = true, fileName = "add-account-claude-dark.png")

    @Test
    fun xaiSignInLight() {
        capture(ProviderType.XAI, darkTheme = false, fileName = "add-account-xai-light.png")
        composeRule.onNodeWithText("xAI 기기 로그인 (RFC 8628)").assertExists()
    }

    @Test
    @Config(qualifiers = "en-w320dp-h760dp")
    fun claudeSignInLightEnglish() {
        capture(ProviderType.CLAUDE, darkTheme = false, fileName = "add-account-claude-light-en.png")
        composeRule.onNodeWithText("Claude browser sign-in (5-hour and weekly limits)").assertExists()
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(provider: ProviderType, darkTheme: Boolean, fileName: String) {
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
        if (provider != ProviderType.ZAI) {
            // Open the provider field (the display-name field below shows the same text), then pick.
            composeRule.onAllNodesWithText(ProviderType.ZAI.displayName)[0].performClick()
            composeRule.waitForIdle()
            composeRule.onAllNodesWithText(provider.displayName)[0].performClick()
        }
        composeRule.waitForIdle()
        captureScreenRoboImage(File(roborazziSystemPropertyOutputDirectory(), fileName))
    }
}
