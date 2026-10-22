package org.parkjw.apps.oot.ui.screens

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.parkjw.apps.oot.data.model.ProviderAccount
import org.parkjw.apps.oot.data.model.ProviderType
import org.parkjw.apps.oot.i18n.AppLanguage
import org.parkjw.apps.oot.ui.theme.OotTheme
import org.parkjw.apps.oot.update.UpdateUiState
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ko-w412dp-h915dp")
class SettingsScreenSemanticsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val demoAccounts = listOf(
        ProviderAccount(id = "demo-claude", provider = ProviderType.CLAUDE, name = "Claude"),
        ProviderAccount(id = "demo-xai", provider = ProviderType.XAI, name = "xAI"),
    )

    private fun setSettingsContent(
        isServiceEnabled: Boolean = true,
        autoUpdateCheckEnabled: Boolean = false,
        accounts: List<ProviderAccount> = demoAccounts,
        notificationHiddenIds: Set<String> = setOf("demo-xai"),
        onToggleService: (Boolean) -> Unit = {},
        onAutoUpdateCheckChange: (Boolean) -> Unit = {},
        onNotificationShownChange: (String, Boolean) -> Unit = { _, _ -> },
    ) {
        composeRule.setContent {
            OotTheme {
                SettingsScreen(
                    isServiceEnabled = isServiceEnabled,
                    refreshIntervalMinutes = 15,
                    accounts = accounts,
                    notificationHiddenIds = notificationHiddenIds,
                    autoUpdateCheckEnabled = autoUpdateCheckEnabled,
                    language = AppLanguage.KOREAN,
                    versionName = "1.0.0",
                    updateState = UpdateUiState.Idle,
                    onBack = {},
                    onToggleService = onToggleService,
                    onSetRefreshInterval = {},
                    onNotificationShownChange = onNotificationShownChange,
                    onAutoUpdateCheckChange = onAutoUpdateCheckChange,
                    onLanguageChange = {},
                    onUpdateAction = {},
                    onOpenSourceCode = {},
                )
            }
        }
    }

    @Test
    fun sectionHeadersAreMarkedAsHeadings() {
        setSettingsContent()
        val headingMatcher = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)

        val expectedHeadings = listOf(
            "언어 · Language",
            "알림",
            "알림에 표시",
            "업데이트",
            "정보",
        )
        for (headingText in expectedHeadings) {
            composeRule.onNode(hasText(headingText) and headingMatcher)
                .assertExists()
        }
    }

    @Test
    fun switchRowsAreToggleableWithSwitchRoleAndLabelText() {
        setSettingsContent()

        // 1. Persistent notification toggle
        composeRule.onNode(hasRole(Role.Switch) and hasText("상시 알림"))
            .assertExists()
            .assertIsOn()

        // 2. Account notification visibility toggles
        composeRule.onNode(hasRole(Role.Switch) and hasText("Claude"))
            .assertExists()
            .assertIsOn()

        composeRule.onNode(hasRole(Role.Switch) and hasText("xAI"))
            .assertExists()
            .assertIsOff()

        // 3. Auto update check toggle
        composeRule.onNode(hasRole(Role.Switch) and hasText("자동으로 업데이트 확인"))
            .assertExists()
            .assertIsOff()
    }

    @Test
    fun backButtonHasAccessibleContentDescription() {
        setSettingsContent()

        composeRule.onNode(hasContentDescription("뒤로 가기") and hasRole(Role.Button))
            .assertExists()
    }

    private companion object {
        fun hasRole(role: Role): SemanticsMatcher = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)
    }
}
