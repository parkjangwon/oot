package org.parkjw.apps.oot.ui.screens

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import kotlinx.serialization.json.Json
import org.junit.Rule
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith
import org.parkjw.apps.oot.data.model.AuthType
import org.parkjw.apps.oot.data.model.ProviderAccount
import org.parkjw.apps.oot.data.model.ProviderType
import org.parkjw.apps.oot.data.model.Remaining
import org.parkjw.apps.oot.data.model.TokenStatus
import org.parkjw.apps.oot.data.model.UsageWindow
import org.parkjw.apps.oot.data.model.VendorError
import org.parkjw.apps.oot.data.model.VendorUsage
import org.parkjw.apps.oot.data.model.toVendorUsage
import org.parkjw.apps.oot.data.remote.parseXaiBilling
import org.parkjw.apps.oot.data.remote.rateLimitedTokenStatus
import org.parkjw.apps.oot.data.remote.toUsageWindows
import org.parkjw.apps.oot.ui.theme.OotTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration
import java.time.Instant

@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ko-w412dp-h1300dp-xxhdpi")
class DashboardScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val fixedNow: Instant = Instant.parse("2026-10-21T12:00:00Z")

    private fun fakeVendorUsages(openRouterKeyLimit: Boolean): List<VendorUsage> = listOf(
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
            accountId = "openai-oauth-1",
            providerType = ProviderType.OPENAI,
            displayName = "OpenAI",
            windows = listOf(
                UsageWindow(
                    id = "five_hour",
                    label = "5h",
                    remaining = Remaining.Percent(62.0),
                    resetsAt = fixedNow.plus(Duration.ofHours(2).plusMinutes(15)),
                ),
                UsageWindow(
                    id = "seven_day",
                    label = "7d",
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
                    label = "5h",
                    remaining = Remaining.Percent(98.0),
                    resetsAt = fixedNow.plus(Duration.ofHours(3).plusMinutes(27)),
                ),
                // <=20% warns by badge only; the number keeps the default color.
                UsageWindow(
                    id = "seven_day",
                    label = "7d",
                    remaining = Remaining.Percent(14.0),
                    resetsAt = fixedNow.plus(Duration.ofDays(4)),
                ),
            ),
        ),
        // xAI reports only its weekly credit period, with the period end as the reset.
        VendorUsage(
            accountId = "xai-1",
            providerType = ProviderType.XAI,
            displayName = "xAI",
            windows = listOf(
                UsageWindow(
                    id = "weekly",
                    label = "weekly",
                    remaining = Remaining.Percent(4.0),
                    resetsAt = fixedNow.plus(Duration.ofHours(7).plusMinutes(12)),
                ),
            ),
        ),
        VendorUsage(
            accountId = "openrouter-1",
            providerType = ProviderType.OPENROUTER,
            displayName = "OpenRouter",
            windows = buildList {
                add(
                    UsageWindow(
                        id = "credit",
                        label = "credit",
                        remaining = Remaining.Balance(12.40, "USD"),
                    )
                )
                if (openRouterKeyLimit) {
                    add(
                        UsageWindow(
                            id = "key",
                            label = "$20.00",
                            remaining = Remaining.Balance(7.60, "USD"),
                        )
                    )
                }
            },
        ),
        VendorUsage(
            accountId = "deepseek-1",
            providerType = ProviderType.DEEPSEEK,
            displayName = "DeepSeek",
            windows = listOf(
                UsageWindow(
                    id = "credit",
                    label = "credit",
                    remaining = Remaining.Balance(110.00, "CNY"),
                ),
            ),
        ),
    )

    // #105: right after a weekly reset xAI reports creditUsagePercent = 1, i.e. 1% used (99% left).
    private fun xaiRightAfterWeeklyReset(): VendorUsage {
        val billing = parseXaiBilling(
            """{"config":{"currentPeriod":{"type":"USAGE_PERIOD_TYPE_WEEKLY","start":"2026-10-21T00:00:00Z","end":"2026-10-28T00:00:00Z"},"creditUsagePercent":1}}""",
            Json,
        )!!
        return VendorUsage(
            accountId = "xai-1",
            providerType = ProviderType.XAI,
            displayName = "xAI",
            windows = billing.toUsageWindows(),
        )
    }

    // The last good Claude snapshot after a 429: its windows are still in the future, but the values are stale.
    private fun claudeAfterFailedRefresh(): VendorUsage {
        val account = ProviderAccount(
            id = "claude-1",
            provider = ProviderType.CLAUDE,
            name = "Claude",
            authType = AuthType.OAUTH,
            accessToken = "at-test-000",
        )
        val cached = TokenStatus(
            accountId = "claude-1",
            provider = ProviderType.CLAUDE,
            accountName = "Claude",
            remainingFormatted = "62% 남음",
            statusSummary = "62% 남음",
            lastUpdated = fixedNow.minus(Duration.ofHours(2)).toEpochMilli(),
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
        )
        return rateLimitedTokenStatus(
            account,
            retryAfterHeader = null,
            cached = cached,
            nowMs = fixedNow.toEpochMilli(),
        ).toVendorUsage(fixedNow)
    }

    @Test
    @Config(qualifiers = "ko-w412dp-h1300dp-notnight-xxhdpi")
    fun xaiAfterWeeklyReset() = captureXaiAfterReset(darkTheme = false, fileName = "xai-after-weekly-reset-light.png")

    @Test
    @Config(qualifiers = "ko-w412dp-h1300dp-night-xxhdpi")
    fun xaiAfterWeeklyResetDark() = captureXaiAfterReset(darkTheme = true, fileName = "xai-after-weekly-reset-dark.png")

    private fun captureXaiAfterReset(darkTheme: Boolean, fileName: String) {
        composeRule.setContent {
            OotTheme(darkTheme = darkTheme) {
                DashboardScreen(
                    vendorUsages = listOf(xaiRightAfterWeeklyReset(), claudeAfterFailedRefresh()),
                    now = fixedNow,
                    modifier = Modifier.testTag("dashboard_root"),
                )
            }
        }
        composeRule.onNodeWithText("xAI").assertExists()
        // 1% used, so 99% left and no danger badge.
        composeRule.onNodeWithText("99% 남음").assertExists()
        composeRule.onNodeWithText("위험").assertDoesNotExist()
        composeRule.onNodeWithText("2시간 전 값이에요", substring = true).assertExists()
        composeRule.onNodeWithTag("dashboard_root").captureRoboImage(
            File(roborazziSystemPropertyOutputDirectory(), fileName),
        )
    }

    // A failed refresh after a success keeps the last values but marks them stale.
    @Test
    @Config(qualifiers = "ko-w412dp-h1300dp-notnight-xxhdpi")
    fun staleAfterRefreshFailure() =
        captureStale(darkTheme = false, fileName = "stale-after-refresh-failure-light.png")

    @Test
    @Config(qualifiers = "ko-w412dp-h1300dp-night-xxhdpi")
    fun staleAfterRefreshFailureDark() =
        captureStale(darkTheme = true, fileName = "stale-after-refresh-failure-dark.png")

    @Test
    @Config(qualifiers = "en-w412dp-h1300dp-notnight-xxhdpi")
    fun staleAfterRefreshFailureEnglish() =
        captureStale(darkTheme = false, fileName = "stale-after-refresh-failure-light-en.png", english = true)

    private fun captureStale(darkTheme: Boolean, fileName: String, english: Boolean = false) {
        composeRule.setContent {
            OotTheme(darkTheme = darkTheme) {
                DashboardScreen(
                    vendorUsages = listOf(claudeAfterFailedRefresh(), xaiRightAfterWeeklyReset()),
                    now = fixedNow,
                    modifier = Modifier.testTag("dashboard_root"),
                )
            }
        }
        // The cached snapshot is still shown, with the time of the last successful fetch.
        if (english) {
            composeRule.onNodeWithText("62% left").assertExists()
            composeRule.onNodeWithText("99% left").assertExists()
            composeRule.onNodeWithText("Values from 2h ago", substring = true).assertExists()
        } else {
            composeRule.onNodeWithText("62% 남음").assertExists()
            composeRule.onNodeWithText("99% 남음").assertExists()
            composeRule.onNodeWithText("2시간 전 값이에요", substring = true).assertExists()
        }
        composeRule.onNodeWithTag("dashboard_root").captureRoboImage(
            File(roborazziSystemPropertyOutputDirectory(), fileName),
        )
    }

    // Nothing registered: 해요체 title, same as the notification's empty state.
    @Test
    @Config(qualifiers = "ko-w412dp-h1300dp-notnight-xxhdpi")
    fun dashboardEmptyLight() = captureEmpty(darkTheme = false, fileName = "dashboard-empty-light.png")

    @Test
    @Config(qualifiers = "ko-w412dp-h1300dp-night-xxhdpi")
    fun dashboardEmptyDark() = captureEmpty(darkTheme = true, fileName = "dashboard-empty-dark.png")

    private fun captureEmpty(darkTheme: Boolean, fileName: String) {
        composeRule.setContent {
            OotTheme(darkTheme = darkTheme) {
                DashboardScreen(
                    vendorUsages = emptyList(),
                    now = fixedNow,
                    modifier = Modifier.testTag("dashboard_root"),
                )
            }
        }
        composeRule.onNodeWithText("등록된 프로바이더가 없어요").assertExists()
        composeRule.onNodeWithTag("dashboard_root").captureRoboImage(
            File(roborazziSystemPropertyOutputDirectory(), fileName),
        )
    }

    @Test
    @Config(qualifiers = "ko-w412dp-h1300dp-notnight-xxhdpi")
    fun dashboardLight() {
        composeRule.setContent {
            OotTheme(darkTheme = false) {
                DashboardScreen(
                    vendorUsages = fakeVendorUsages(openRouterKeyLimit = true),
                    now = fixedNow,
                    modifier = Modifier.testTag("dashboard_root"),
                )
            }
        }
        composeRule.onNodeWithText("Claude").assertExists()
        composeRule.onNodeWithText("$12.40").assertExists()
        composeRule.onNodeWithText("키 한도 $20.00 중 $7.60 남음").assertExists()
        composeRule.onNodeWithText("프로바이더 추가").assertExists()
        composeRule.onNodeWithTag("dashboard_root").captureRoboImage(
            File(roborazziSystemPropertyOutputDirectory(), "dashboard-light.png"),
        )
    }

    @Test
    @Config(qualifiers = "ko-w412dp-h1300dp-night-xxhdpi")
    fun dashboardDark() {
        composeRule.setContent {
            OotTheme(darkTheme = true) {
                DashboardScreen(
                    vendorUsages = fakeVendorUsages(openRouterKeyLimit = true),
                    now = fixedNow,
                    modifier = Modifier.testTag("dashboard_root"),
                )
            }
        }
        composeRule.onNodeWithText("Claude").assertExists()
        composeRule.onNodeWithText("$12.40").assertExists()
        composeRule.onNodeWithText("키 한도 $20.00 중 $7.60 남음").assertExists()
        composeRule.onNodeWithText("프로바이더 추가").assertExists()
        composeRule.onNodeWithTag("dashboard_root").captureRoboImage(
            File(roborazziSystemPropertyOutputDirectory(), "dashboard-dark.png"),
        )
    }

    @Test
    fun dashboardOpenRouterNoKeyLimitLight() {
        composeRule.setContent {
            OotTheme(darkTheme = false) {
                DashboardScreen(
                    vendorUsages = fakeVendorUsages(openRouterKeyLimit = false),
                    now = fixedNow,
                    modifier = Modifier.testTag("dashboard_root"),
                )
            }
        }
        composeRule.onNodeWithText("$12.40").assertExists()
        composeRule.onNodeWithText("키 한도 없음").assertExists()
        composeRule.onNodeWithTag("dashboard_root").captureRoboImage(
            File(roborazziSystemPropertyOutputDirectory(), "dashboard-openrouter-nolimit-light.png"),
        )
    }

    @Test
    fun dashboardOpenRouterNoKeyLimitDark() {
        composeRule.setContent {
            OotTheme(darkTheme = true) {
                DashboardScreen(
                    vendorUsages = fakeVendorUsages(openRouterKeyLimit = false),
                    now = fixedNow,
                    modifier = Modifier.testTag("dashboard_root"),
                )
            }
        }
        composeRule.onNodeWithText("$12.40").assertExists()
        composeRule.onNodeWithText("키 한도 없음").assertExists()
        composeRule.onNodeWithTag("dashboard_root").captureRoboImage(
            File(roborazziSystemPropertyOutputDirectory(), "dashboard-openrouter-nolimit-dark.png"),
        )
    }

    @Test
    fun dashboardDraggingLight() {
        composeRule.setContent {
            OotTheme(darkTheme = false) {
                DashboardScreen(
                    vendorUsages = fakeVendorUsages(openRouterKeyLimit = true),
                    now = fixedNow,
                    previewDraggingAccountId = "glm-1",
                    modifier = Modifier.testTag("dashboard_root"),
                )
            }
        }
        composeRule.onNodeWithText("GLM").assertExists()
        composeRule.onNodeWithTag("dashboard_root").captureRoboImage(
            File(roborazziSystemPropertyOutputDirectory(), "dashboard-dragging-light.png"),
        )
    }

    @Test
    fun dashboardDraggingDark() {
        composeRule.setContent {
            OotTheme(darkTheme = true) {
                DashboardScreen(
                    vendorUsages = fakeVendorUsages(openRouterKeyLimit = true),
                    now = fixedNow,
                    previewDraggingAccountId = "glm-1",
                    modifier = Modifier.testTag("dashboard_root"),
                )
            }
        }
        composeRule.onNodeWithText("GLM").assertExists()
        composeRule.onNodeWithTag("dashboard_root").captureRoboImage(
            File(roborazziSystemPropertyOutputDirectory(), "dashboard-dragging-dark.png"),
        )
    }

    // English (#74): the same dashboards with English resources.
    @Test
    @Config(qualifiers = "en-w412dp-h1300dp-notnight-xxhdpi")
    fun dashboardLightEnglish() = captureEnglish(darkTheme = false, fileName = "dashboard-light-en.png")

    @Test
    @Config(qualifiers = "en-w412dp-h1300dp-night-xxhdpi")
    fun dashboardDarkEnglish() = captureEnglish(darkTheme = true, fileName = "dashboard-dark-en.png")

    private fun captureEnglish(darkTheme: Boolean, fileName: String) {
        composeRule.setContent {
            OotTheme(darkTheme = darkTheme) {
                DashboardScreen(
                    vendorUsages = fakeVendorUsages(openRouterKeyLimit = true),
                    now = fixedNow,
                    modifier = Modifier.testTag("dashboard_root"),
                )
            }
        }
        composeRule.onNodeWithText("$7.60 left of $20.00 key limit").assertExists()
        // Badges describe the quota left (#80).
        composeRule.onNodeWithText("Low").assertExists()
        composeRule.onNodeWithText("Critical").assertExists()
        composeRule.onNodeWithText("Add provider").assertExists()
        composeRule.onAllNodesWithText("5-hour")[0].assertExists()
        composeRule.onNodeWithTag("dashboard_root").captureRoboImage(
            File(roborazziSystemPropertyOutputDirectory(), fileName),
        )
    }

    @Test
    @Config(qualifiers = "en-w412dp-h1300dp-notnight-xxhdpi")
    fun dashboardEmptyLightEnglish() {
        composeRule.setContent {
            OotTheme(darkTheme = false) {
                DashboardScreen(
                    vendorUsages = emptyList(),
                    now = fixedNow,
                    modifier = Modifier.testTag("dashboard_root"),
                )
            }
        }
        composeRule.onNodeWithText("No providers yet").assertExists()
        composeRule.onNodeWithTag("dashboard_root").captureRoboImage(
            File(roborazziSystemPropertyOutputDirectory(), "dashboard-empty-light-en.png"),
        )
    }

    @Test
    @Config(qualifiers = "en-w412dp-h1300dp-notnight-xxhdpi")
    fun dashboardErrorsEnglish() {
        composeRule.setContent {
            OotTheme(darkTheme = false) {
                DashboardScreen(
                    vendorUsages = listOf(
                        VendorUsage("e1", ProviderType.CLAUDE, "Claude", emptyList(), VendorError.Unauthorized),
                        VendorUsage("e2", ProviderType.ZAI, "GLM", emptyList(), VendorError.Network),
                    ),
                    now = fixedNow,
                    modifier = Modifier.testTag("dashboard_root"),
                )
            }
        }
        composeRule.onNodeWithText("Sign in again", substring = true).assertExists()
        composeRule.onNodeWithTag("dashboard_root").captureRoboImage(
            File(roborazziSystemPropertyOutputDirectory(), "dashboard-errors-light-en.png"),
        )
    }
}
