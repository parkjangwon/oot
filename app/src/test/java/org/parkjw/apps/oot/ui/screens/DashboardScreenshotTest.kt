package org.parkjw.apps.oot.ui.screens

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import org.junit.Rule
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith
import org.parkjw.apps.oot.data.model.ProviderType
import org.parkjw.apps.oot.data.model.Remaining
import org.parkjw.apps.oot.data.model.UsageWindow
import org.parkjw.apps.oot.data.model.VendorError
import org.parkjw.apps.oot.data.model.VendorUsage
import org.parkjw.apps.oot.ui.theme.OotTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration
import java.time.Instant

@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h1100dp-xxhdpi")
class DashboardScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val fixedNow: Instant = Instant.parse("2026-10-21T12:00:00Z")

    private val fakeVendorUsages: List<VendorUsage> = listOf(
        VendorUsage(
            accountId = "claude-1",
            providerType = ProviderType.CLAUDE,
            displayName = "Claude",
            windows = listOf(
                UsageWindow(
                    id = "five_hour",
                    label = "5시간",
                    remaining = Remaining.Percent(62.0),
                    resetsAt = fixedNow.plus(Duration.ofHours(1).plusMinutes(40)),
                ),
                UsageWindow(
                    id = "seven_day",
                    label = "주간",
                    remaining = Remaining.Percent(81.0),
                    resetsAt = fixedNow.plus(Duration.ofDays(3)),
                ),
            ),
        ),
        VendorUsage(
            accountId = "openai-oauth-1",
            providerType = ProviderType.OPENAI,
            displayName = "OpenAI",
            windows = listOf(
                UsageWindow(
                    id = "five_hour",
                    label = "5시간",
                    remaining = Remaining.Percent(62.0),
                    resetsAt = fixedNow.plus(Duration.ofHours(2).plusMinutes(15)),
                ),
                UsageWindow(
                    id = "seven_day",
                    label = "주간",
                    remaining = Remaining.Percent(88.0),
                    resetsAt = fixedNow.plus(Duration.ofDays(4)),
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
                    label = "5시간",
                    remaining = Remaining.Percent(98.0),
                    resetsAt = fixedNow.plus(Duration.ofHours(3).plusMinutes(27)),
                ),
            ),
        ),
        VendorUsage(
            accountId = "xai-1",
            providerType = ProviderType.XAI,
            displayName = "xAI",
            windows = listOf(
                UsageWindow(
                    id = "weekly",
                    label = "주간",
                    remaining = Remaining.Percent(14.0),
                    resetsAt = fixedNow.plus(Duration.ofDays(2)),
                ),
                UsageWindow(
                    id = "five_hour",
                    label = "5시간",
                    remaining = Remaining.Percent(4.0),
                    resetsAt = fixedNow.plus(Duration.ofMinutes(45)),
                ),
            ),
        ),
        VendorUsage(
            accountId = "openrouter-1",
            providerType = ProviderType.OPENROUTER,
            displayName = "OpenRouter",
            windows = listOf(
                UsageWindow(
                    id = "credit",
                    label = "키 한도 $20.00",
                    remaining = Remaining.Balance(12.40, "USD"),
                ),
            ),
        ),
    )

    @Test
    fun dashboardLight() {
        composeRule.setContent {
            OotTheme(darkTheme = false) {
                DashboardScreen(
                    vendorUsages = fakeVendorUsages,
                    now = fixedNow,
                    modifier = Modifier.testTag("dashboard_root"),
                )
            }
        }
        composeRule.onNodeWithText("Claude").assertExists()
        composeRule.onNodeWithText("$12.40").assertExists()
        composeRule.onNodeWithTag("dashboard_root").captureRoboImage(
            File(roborazziSystemPropertyOutputDirectory(), "dashboard-light.png"),
        )
    }

    @Test
    fun dashboardDark() {
        composeRule.setContent {
            OotTheme(darkTheme = true) {
                DashboardScreen(
                    vendorUsages = fakeVendorUsages,
                    now = fixedNow,
                    modifier = Modifier.testTag("dashboard_root"),
                )
            }
        }
        composeRule.onNodeWithText("Claude").assertExists()
        composeRule.onNodeWithText("$12.40").assertExists()
        composeRule.onNodeWithTag("dashboard_root").captureRoboImage(
            File(roborazziSystemPropertyOutputDirectory(), "dashboard-dark.png"),
        )
    }
}
