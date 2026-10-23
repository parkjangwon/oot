package org.parkjw.apps.oot.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import java.io.File
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
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

// #112: the account detail dialog notes when deleting only removes the key from this device.
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ko-w412dp-h1300dp-xxhdpi")
class DeleteRevokeNoteScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val fixedNow: Instant = Instant.parse("2026-10-21T12:00:00Z")

    private val usages = listOf(
        VendorUsage(
            accountId = "openrouter-1",
            providerType = ProviderType.OPENROUTER,
            displayName = "OpenRouter",
            windows = listOf(
                UsageWindow(id = "credits", label = "credits", remaining = Remaining.Percent(58.0), resetsAt = null),
            ),
        ),
        VendorUsage(
            accountId = "xai-1",
            providerType = ProviderType.XAI,
            displayName = "Grok",
            windows = listOf(
                UsageWindow(
                    id = "weekly", label = "7d", remaining = Remaining.Percent(71.0),
                    resetsAt = fixedNow.plus(Duration.ofDays(2)),
                ),
            ),
        ),
    )

    private val koNote = "삭제하면 이 기기에 저장된 키만 지워져요. 서버 쪽 철회는 해당 서비스에서 직접 해 주세요."
    private val enNote = "Deleting removes the key from this device only. Revoke it on the server in the service itself."

    private fun captureDialog(
        fileName: String,
        darkTheme: Boolean = false,
        english: Boolean = false,
        deviceOnly: Set<String> = setOf("openrouter-1"),
        open: String = "OpenRouter",
    ) {
        composeRule.setContent {
            OotTheme(darkTheme = darkTheme) {
                DashboardScreen(vendorUsages = usages, now = fixedNow, deviceOnlyRevocationIds = deviceOnly)
            }
        }
        composeRule.onNodeWithText(open).performClick()
        composeRule.waitForIdle()
        val noteShown = composeRule.onAllNodesWithText(if (english) enNote else koNote).fetchSemanticsNodes().size
        assertEquals(if ("openrouter-1" in deviceOnly && open == "OpenRouter") 1 else 0, noteShown)
        captureScreenRoboImage(File(roborazziSystemPropertyOutputDirectory(), fileName))
    }

    // "before" renders the dialog exactly as main does today (no device-only account ids → no note).
    @Test
    @Config(qualifiers = "ko-w412dp-h1300dp-notnight-xxhdpi")
    fun beforeLight() = captureDialog("revoke-note-before-light.png", deviceOnly = emptySet())

    @Test
    @Config(qualifiers = "en-w412dp-h1300dp-notnight-xxhdpi")
    fun beforeEnglish() = captureDialog("revoke-note-before-en.png", english = true, deviceOnly = emptySet())

    @Test
    @Config(qualifiers = "ko-w412dp-h1300dp-notnight-xxhdpi")
    fun afterLight() = captureDialog("revoke-note-after-light.png")

    @Test
    @Config(qualifiers = "ko-w412dp-h1300dp-night-xxhdpi")
    fun afterDark() = captureDialog("revoke-note-after-dark.png", darkTheme = true)

    @Test
    @Config(qualifiers = "en-w412dp-h1300dp-notnight-xxhdpi")
    fun afterEnglish() = captureDialog("revoke-note-after-en.png", english = true)

    @Test
    @Config(qualifiers = "ko-w320dp-h1300dp-notnight-xxhdpi")
    fun afterNarrowKorean() = captureDialog("revoke-note-after-320-ko.png")

    @Test
    @Config(qualifiers = "en-w320dp-h1300dp-notnight-xxhdpi")
    fun afterNarrowEnglish() = captureDialog("revoke-note-after-320-en.png", english = true)

    // Server-revoke account (xAI sign-in): no note.
    @Test
    @Config(qualifiers = "ko-w412dp-h1300dp-notnight-xxhdpi")
    fun afterServerRevokeNoNote() = captureDialog("revoke-note-after-server-no-note.png", open = "Grok")
}
