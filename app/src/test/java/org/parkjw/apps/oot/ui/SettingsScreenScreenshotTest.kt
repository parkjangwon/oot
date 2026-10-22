package org.parkjw.apps.oot.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.GraphicsMode.Mode.NATIVE
import org.parkjw.apps.oot.data.model.ProviderAccount
import org.parkjw.apps.oot.data.model.ProviderType
import org.parkjw.apps.oot.i18n.AppLanguage
import org.parkjw.apps.oot.ui.screens.SettingsScreen
import org.parkjw.apps.oot.ui.theme.OotTheme
import org.parkjw.apps.oot.update.ReleaseFixtures
import org.parkjw.apps.oot.update.ReleaseJson
import org.parkjw.apps.oot.update.UpdateProblem
import org.parkjw.apps.oot.update.UpdateUiState

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(NATIVE)
@Config(sdk = [35], qualifiers = "ko-w412dp-h915dp")
class SettingsScreenScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val release by lazy {
        ReleaseJson.parseList(ReleaseFixtures.list(ReleaseFixtures.release("v1.1.0"))).single()
    }

    private val demoAccounts = listOf(
        ProviderAccount(id = "demo-claude", provider = ProviderType.CLAUDE, name = "Claude (OAuth)"),
        ProviderAccount(id = "demo-glm", provider = ProviderType.ZAI, name = "Z.ai (GLM)"),
        ProviderAccount(id = "demo-xai", provider = ProviderType.XAI, name = "xAI (Grok)"),
        ProviderAccount(id = "demo-openrouter", provider = ProviderType.OPENROUTER, name = "OpenRouter"),
    )

    private fun setSettingsContent(
        darkTheme: Boolean = false,
        state: UpdateUiState = UpdateUiState.Idle,
        accounts: List<ProviderAccount> = emptyList(),
        notificationHiddenIds: Set<String> = emptySet(),
        onNotificationShownChange: (String, Boolean) -> Unit = { _, _ -> },
        language: AppLanguage = AppLanguage.KOREAN,
        onLanguageChange: (AppLanguage) -> Unit = {},
    ) {
        composeRule.setContent {
            OotTheme(darkTheme = darkTheme) {
                SettingsScreen(
                    isServiceEnabled = true,
                    refreshIntervalMinutes = 1,
                    accounts = accounts,
                    notificationHiddenIds = notificationHiddenIds,
                    autoUpdateCheckEnabled = true,
                    language = language,
                    versionName = "1.0.3",
                    updateState = state,
                    onBack = {},
                    onToggleService = {},
                    onSetRefreshInterval = {},
                    onNotificationShownChange = onNotificationShownChange,
                    onAutoUpdateCheckChange = {},
                    onLanguageChange = onLanguageChange,
                    onUpdateAction = {},
                    onOpenSourceCode = {},
                )
            }
        }
    }

    private fun capture(
        name: String,
        darkTheme: Boolean = false,
        state: UpdateUiState,
        accounts: List<ProviderAccount> = emptyList(),
        notificationHiddenIds: Set<String> = emptySet(),
        language: AppLanguage = AppLanguage.KOREAN,
    ) {
        setSettingsContent(darkTheme, state, accounts, notificationHiddenIds, language = language)
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

    // 3x (xxhdpi) like the notification captures; night must come before the density in the qualifiers.
    @Test
    @Config(qualifiers = "ko-w412dp-h1040dp-notnight-xxhdpi")
    fun settingsNotificationVisibilityLight() {
        capture(
            "settings-notification-visibility-light-3x",
            state = UpdateUiState.Idle,
            accounts = demoAccounts,
            notificationHiddenIds = setOf("demo-glm"),
        )
    }

    @Test
    @Config(qualifiers = "ko-w412dp-h1040dp-night-xxhdpi")
    fun settingsNotificationVisibilityDark() {
        capture(
            "settings-notification-visibility-dark-3x",
            darkTheme = true,
            state = UpdateUiState.Idle,
            accounts = demoAccounts,
            notificationHiddenIds = setOf("demo-glm"),
        )
    }

    @Test
    fun notificationVisibilityRowsReportTheNewState() {
        val changes = mutableListOf<Pair<String, Boolean>>()
        setSettingsContent(
            accounts = demoAccounts,
            notificationHiddenIds = setOf("demo-glm"),
            onNotificationShownChange = { id, shown -> changes += id to shown },
        )
        composeRule.onNodeWithText("xAI (Grok)").performClick()
        composeRule.onNodeWithText("Z.ai (GLM)").performClick()
        assertEquals(listOf("demo-xai" to false, "demo-glm" to true), changes)
    }

    @Test
    fun notificationVisibilitySectionIsHiddenWithoutProviders() {
        setSettingsContent()
        composeRule.onNodeWithText("알림").assertExists()
        composeRule.onNodeWithText("알림에 표시").assertDoesNotExist()
    }

    // Language selector (#74): Korean (default) and English, light 3x like the other settings captures.
    @Test
    @Config(qualifiers = "ko-w412dp-h1040dp-notnight-xxhdpi")
    fun settingsLanguageKorean() {
        capture("settings-language-ko-light-3x", state = UpdateUiState.Idle, accounts = demoAccounts)
        composeRule.onNodeWithText("언어 · Language").assertExists()
        composeRule.onNodeWithText("한국어").assertExists()
        composeRule.onNodeWithText("English").assertExists()
    }

    @Test
    @Config(qualifiers = "en-w412dp-h1040dp-notnight-xxhdpi")
    fun settingsLanguageEnglish() {
        capture(
            "settings-language-en-light-3x",
            state = UpdateUiState.Idle,
            accounts = demoAccounts,
            language = AppLanguage.ENGLISH,
        )
        composeRule.onNodeWithText("Language").assertExists()
        composeRule.onNodeWithText("Notifications").assertExists()
        composeRule.onNodeWithText("Show in notification").assertExists()
    }

    @Test
    @Config(qualifiers = "en-w412dp-h1040dp-night-xxhdpi")
    fun settingsLanguageEnglishDark() {
        capture(
            "settings-language-en-dark-3x",
            darkTheme = true,
            state = UpdateUiState.Idle,
            accounts = demoAccounts,
            language = AppLanguage.ENGLISH,
        )
    }

    @Test
    fun languageSelectorReportsTheChoice() {
        val picked = mutableListOf<AppLanguage>()
        setSettingsContent(onLanguageChange = { picked += it })
        composeRule.onNodeWithText("English").performClick()
        composeRule.onNodeWithText("한국어").performClick()
        assertEquals(listOf(AppLanguage.ENGLISH, AppLanguage.KOREAN), picked)
    }
}
