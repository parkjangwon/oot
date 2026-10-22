package org.parkjw.apps.oot.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.parkjw.apps.oot.data.model.ProviderType
import org.parkjw.apps.oot.ui.screens.VendorMark
import org.parkjw.apps.oot.ui.theme.OotTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Locale

// Renders each vendor tile as it appears in the app, for the README's supported-vendor table
// (docs/screenshots/vendors/<provider>.png).
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h200dp-notnight-xxhdpi")
class VendorMarkDocsScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun vendorMarks() {
        composeRule.setContent {
            OotTheme(darkTheme = false) {
                Row {
                    ProviderType.entries.filter { it.isSelectable }.forEach { provider ->
                        VendorMark(provider, Modifier.testTag("mark_${provider.name}"))
                    }
                }
            }
        }
        ProviderType.entries.filter { it.isSelectable }.forEach { provider ->
            composeRule.onNodeWithTag("mark_${provider.name}").captureRoboImage(
                File(
                    roborazziSystemPropertyOutputDirectory(),
                    "vendor-${provider.name.lowercase(Locale.ROOT).replace('_', '-')}.png",
                ),
            )
        }
    }
}
