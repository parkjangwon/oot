package org.parkjw.apps.oot.ui.screens

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.parkjw.apps.oot.R
import org.parkjw.apps.oot.data.model.ProviderAccount
import org.parkjw.apps.oot.data.model.ProviderType
import org.parkjw.apps.oot.i18n.AppLanguage
import org.parkjw.apps.oot.ui.theme.OotTheme
import org.parkjw.apps.oot.update.UpdateUiState
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "ko-w412dp-h915dp")
class SettingsScreenSemanticsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val demoAccounts = listOf(
        ProviderAccount(id = "demo-claude", provider = ProviderType.CLAUDE, name = "Claude"),
        ProviderAccount(id = "demo-xai", provider = ProviderType.XAI, name = "xAI"),
        ProviderAccount(id = "demo-zai", provider = ProviderType.ZAI, name = "Z.ai"),
    )

    private fun getString(resId: Int): String =
        RuntimeEnvironment.getApplication().getString(resId)

    private fun setSettingsContent(
        isServiceEnabled: Boolean = true,
        autoUpdateCheckEnabled: Boolean = false,
        keepScreenOnEnabled: Boolean = false,
        accounts: List<ProviderAccount> = demoAccounts,
        notificationHiddenIds: Set<String> = setOf("demo-xai"),
        language: AppLanguage = AppLanguage.KOREAN,
        onToggleService: (Boolean) -> Unit = {},
        onAutoUpdateCheckChange: (Boolean) -> Unit = {},
        onKeepScreenOnChange: (Boolean) -> Unit = {},
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
                    keepScreenOnEnabled = keepScreenOnEnabled,
                    language = language,
                    versionName = "1.0.0",
                    updateState = UpdateUiState.Idle,
                    onBack = {},
                    onToggleService = onToggleService,
                    onSetRefreshInterval = {},
                    onNotificationShownChange = onNotificationShownChange,
                    onAutoUpdateCheckChange = onAutoUpdateCheckChange,
                    onKeepScreenOnChange = onKeepScreenOnChange,
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
            getString(R.string.settings_section_language),
            getString(R.string.settings_section_notifications),
            getString(R.string.settings_section_notification_visibility),
            getString(R.string.settings_section_display),
            getString(R.string.settings_section_update),
            getString(R.string.settings_section_info),
        )
        for (headingText in expectedHeadings) {
            composeRule.onNode(hasText(headingText) and headingMatcher)
                .assertExists()
        }
    }

    @Test
    fun switchRowsAreToggleableWithSwitchRoleAndLabelText() {
        var serviceToggled: Boolean? = null
        setSettingsContent(
            onToggleService = { serviceToggled = it },
        )

        // 1. Persistent notification toggle
        val persistentTitle = getString(R.string.settings_persistent_notification)
        composeRule.onAllNodes(hasRole(Role.Switch) and hasText(persistentTitle))
            .assertCountEquals(1)
        composeRule.onNode(hasRole(Role.Switch) and hasText(persistentTitle))
            .assertIsOn()
            .performClick()
        assertEquals(false, serviceToggled)

        // 2. Account notification visibility toggles
        composeRule.onAllNodes(hasRole(Role.Switch) and hasText("Claude"))
            .assertCountEquals(1)
        composeRule.onNode(hasRole(Role.Switch) and hasText("Claude"))
            .assertIsOn()

        composeRule.onAllNodes(hasRole(Role.Switch) and hasText("xAI"))
            .assertCountEquals(1)
        composeRule.onNode(hasRole(Role.Switch) and hasText("xAI"))
            .assertIsOff()

        composeRule.onAllNodes(hasRole(Role.Switch) and hasText("Z.ai"))
            .assertCountEquals(1)
        composeRule.onNode(hasRole(Role.Switch) and hasText("Z.ai"))
            .assertIsOn()

        // VendorMark fallback letter tile must not be exposed to accessibility
        composeRule.onAllNodes(hasText("Z")).assertCountEquals(0)

        // 3. Auto update check toggle
        val autoUpdateTitle = getString(R.string.update_auto)
        composeRule.onAllNodes(hasRole(Role.Switch) and hasText(autoUpdateTitle))
            .assertCountEquals(1)
        composeRule.onNode(hasRole(Role.Switch) and hasText(autoUpdateTitle))
            .assertIsOff()
    }

    @Test
    fun keepScreenOnRowIsAToggleWithSwitchRole() {
        var toggled: Boolean? = null
        setSettingsContent(keepScreenOnEnabled = false, onKeepScreenOnChange = { toggled = it })

        val title = getString(R.string.settings_keep_screen_on)
        composeRule.onAllNodes(hasRole(Role.Switch) and hasText(title))
            .assertCountEquals(1)
        // Supporting text is merged into the same node, so TalkBack reads the row once.
        composeRule.onAllNodes(
            hasRole(Role.Switch) and
                hasText(title) and
                hasText(getString(R.string.settings_keep_screen_on_desc)),
        ).assertCountEquals(1)
        composeRule.onNode(hasRole(Role.Switch) and hasText(title))
            .assertIsOff()
            .performClick()
        assertEquals(true, toggled)
    }

    @Test
    fun backButtonHasAccessibleContentDescription() {
        setSettingsContent()

        composeRule.onNode(hasContentDescription(getString(R.string.settings_back)) and hasRole(Role.Button))
            .assertExists()
    }

    @Test
    @Config(qualifiers = "en-w412dp-h915dp")
    fun englishConfigurationResolvesEnglishHeadingsAndBackDescription() {
        setSettingsContent(language = AppLanguage.ENGLISH)
        val headingMatcher = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)

        val expectedHeadings = listOf(
            getString(R.string.settings_section_language),
            getString(R.string.settings_section_notifications),
            getString(R.string.settings_section_notification_visibility),
            getString(R.string.settings_section_display),
            getString(R.string.settings_section_update),
            getString(R.string.settings_section_info),
        )
        for (headingText in expectedHeadings) {
            composeRule.onNode(hasText(headingText) and headingMatcher)
                .assertExists()
        }

        composeRule.onNode(hasContentDescription(getString(R.string.settings_back)) and hasRole(Role.Button))
            .assertExists()
    }

    @Test
    fun gitHubRowIsClickableWithoutSelectionSemantics() {
        setSettingsContent()

        val sourceCodeLabel = getString(R.string.settings_source_code)
        composeRule.onNode(hasText(sourceCodeLabel) and hasClickAction())
            .assertExists()

        composeRule.onNode(
            hasText(sourceCodeLabel) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected),
        ).assertDoesNotExist()
    }

    private companion object {
        fun hasRole(role: Role): SemanticsMatcher = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)
    }
}
