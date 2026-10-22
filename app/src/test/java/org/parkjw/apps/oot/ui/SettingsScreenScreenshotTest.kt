package org.parkjw.apps.oot.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.GraphicsMode.Mode.NATIVE
import org.parkjw.apps.oot.ui.screens.SettingsScreen
import org.parkjw.apps.oot.ui.theme.OotTheme
import org.parkjw.apps.oot.update.ReleaseFixtures
import org.parkjw.apps.oot.update.ReleaseJson
import org.parkjw.apps.oot.update.UpdateProblem
import org.parkjw.apps.oot.update.UpdateUiState

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp")
class SettingsScreenScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val release by lazy {
        ReleaseJson.parseList(ReleaseFixtures.list(ReleaseFixtures.release("v1.1.0"))).single()
    }

    private fun capture(name: String, darkTheme: Boolean = false, state: UpdateUiState) {
        composeRule.setContent {
            OotTheme(darkTheme = darkTheme) {
                SettingsScreen(
                    isServiceEnabled = true,
                    refreshIntervalMinutes = 1,
                    autoUpdateCheckEnabled = true,
                    versionName = "1.0.3",
                    updateState = state,
                    onBack = {},
                    onToggleService = {},
                    onSetRefreshInterval = {},
                    onAutoUpdateCheckChange = {},
                    onUpdateAction = {},
                    onOpenSourceCode = {},
                )
            }
        }
        composeRule.onNodeWithText("GitHub").assertExists()
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun settingsIdle() {
        capture("settings-idle", state = UpdateUiState.Idle)
    }

    @Test
    fun settingsNewVersion() {
        capture(
            "settings-new-version",
            state = UpdateUiState.Available(release, release.apkAsset(), prompt = false),
        )
    }

    @Test
    fun settingsDownloading() {
        capture("settings-downloading", state = UpdateUiState.Downloading(release, 42))
    }

    @Test
    fun settingsFailed() {
        capture(
            "settings-failed",
            state = UpdateUiState.Failed(UpdateProblem.Download, release.htmlUrl, release),
        )
    }

    @Test
    fun settingsDark() {
        capture("settings-dark", darkTheme = true, state = UpdateUiState.Idle)
    }
}
