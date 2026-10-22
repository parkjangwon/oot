package org.parkjw.apps.oot.ui.screens

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import java.io.File
import java.time.Duration
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.parkjw.apps.oot.data.model.ProviderType
import org.parkjw.apps.oot.data.model.Remaining
import org.parkjw.apps.oot.data.model.UsageWindow
import org.parkjw.apps.oot.data.model.VendorUsage
import org.parkjw.apps.oot.ui.theme.OotTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// Storage reset (#109): the notice card and its confirmation dialog.
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ko-w412dp-h1300dp-xxhdpi")
class StorageResetScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val fixedNow: Instant = Instant.parse("2026-10-21T12:00:00Z")

    private fun demoUsages(): List<VendorUsage> = listOf(
        VendorUsage(
            accountId = "claude-1",
            providerType = ProviderType.CLAUDE,
            displayName = "Claude",
            windows = listOf(
                UsageWindow(
                    id = "five_hour",
                    label = "5h",
                    remaining = Remaining.Percent(62.0),
                    resetsAt = fixedNow.plus(Duration.ofHours(1).plusMinutes(40)),
                ),
                UsageWindow(
                    id = "seven_day",
                    label = "7d",
                    remaining = Remaining.Percent(81.0),
                    resetsAt = fixedNow.plus(Duration.ofDays(3)),
                ),
            ),
        ),
        VendorUsage(
            accountId = "glm-1",
            providerType = ProviderType.ZAI,
            displayName = "GLM",
            windows = listOf(
                UsageWindow(
                    id = "five_hour",
                    label = "5h",
                    remaining = Remaining.Percent(44.0),
                    resetsAt = fixedNow.plus(Duration.ofHours(2)),
                ),
            ),
        ),
    )

    @Test
    @Config(qualifiers = "ko-w412dp-h1300dp-notnight-xxhdpi")
    fun dashboardNoticeLight() = capture(darkTheme = false, fileName = "storage-reset-dashboard-light.png")

    @Test
    @Config(qualifiers = "ko-w412dp-h1300dp-night-xxhdpi")
    fun dashboardNoticeDark() = capture(darkTheme = true, fileName = "storage-reset-dashboard-dark.png")

    @Test
    @Config(qualifiers = "en-w412dp-h1300dp-notnight-xxhdpi")
    fun dashboardNoticeEnglish() = capture(
        darkTheme = false,
        fileName = "storage-reset-dashboard-en.png",
        english = true,
    )

    @Test
    @Config(qualifiers = "ko-w412dp-h1300dp-notnight-xxhdpi")
    fun resetDialogLight() = captureDialog(
        darkTheme = false,
        fileName = "storage-reset-confirm-dialog-light.png",
    )

    @Test
    @Config(qualifiers = "en-w412dp-h1300dp-notnight-xxhdpi")
    fun resetDialogEnglish() = captureDialog(
        darkTheme = false,
        fileName = "storage-reset-confirm-dialog-light-en.png",
        english = true,
    )

    private fun setDashboard(darkTheme: Boolean) {
        composeRule.setContent {
            OotTheme(darkTheme = darkTheme) {
                DashboardScreen(
                    vendorUsages = demoUsages(),
                    now = fixedNow,
                    isSecureStorageAvailable = false,
                    snackbarHostState = remember { SnackbarHostState() },
                    modifier = Modifier.testTag("dashboard_root"),
                )
            }
        }
    }

    private fun capture(darkTheme: Boolean, fileName: String, english: Boolean = false) {
        setDashboard(darkTheme)
        composeRule.onNodeWithText("Claude").assertExists()
        if (english) {
            composeRule.onNodeWithText("Reset storage and sign in again").assertExists()
        } else {
            composeRule.onNodeWithText("저장소 초기화하고 다시 로그인").assertExists()
        }
        composeRule.onNodeWithTag("dashboard_root").captureRoboImage(
            File(roborazziSystemPropertyOutputDirectory(), fileName),
        )
    }

    private fun captureDialog(darkTheme: Boolean, fileName: String, english: Boolean = false) {
        setDashboard(darkTheme)
        composeRule.onNodeWithText(
            if (english) "Reset storage and sign in again" else "저장소 초기화하고 다시 로그인",
        ).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(
            if (english) "Reset secure storage?" else "저장소를 초기화할까요?",
        ).assertExists()
        captureScreenRoboImage(File(roborazziSystemPropertyOutputDirectory(), fileName))
    }
}
