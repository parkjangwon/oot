package org.parkjw.apps.oot.ui.screens

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.parkjw.apps.oot.ui.theme.OotTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ko-w412dp-h1300dp-xxhdpi")
class StorageResetTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setDashboard(
        available: Boolean,
        isResettingSecureStorage: Boolean = false,
        onReset: () -> Unit = {},
    ) {
        composeRule.setContent {
            OotTheme {
                DashboardScreen(
                    vendorUsages = emptyList(),
                    isSecureStorageAvailable = available,
                    isResettingSecureStorage = isResettingSecureStorage,
                    snackbarHostState = remember { SnackbarHostState() },
                    onResetSecureStorage = onReset,
                )
            }
        }
    }

    @Test
    fun `notice card shows while storage is unavailable`() {
        setDashboard(available = false)

        composeRule.onNodeWithText("저장소 초기화하고 계정 다시 추가").assertExists()
    }

    @Test
    fun `notice card is hidden when storage is available`() {
        setDashboard(available = true)

        composeRule.onNodeWithText("저장소 초기화하고 계정 다시 추가").assertDoesNotExist()
    }

    @Test
    fun `confirming the dialog triggers the reset`() {
        var reset = false
        setDashboard(available = false, onReset = { reset = true })

        composeRule.onNodeWithText("저장소 초기화하고 계정 다시 추가").performClick()
        composeRule.onNodeWithText("저장소를 초기화할까요?").assertExists()
        composeRule.onNodeWithText("초기화").performClick()

        assertTrue(reset)
        composeRule.onNodeWithText("저장소를 초기화할까요?").assertDoesNotExist()
    }

    @Test
    fun `cancel dismisses the dialog without resetting`() {
        var reset = false
        setDashboard(available = false, onReset = { reset = true })

        composeRule.onNodeWithText("저장소 초기화하고 계정 다시 추가").performClick()
        composeRule.onNodeWithText("취소").performClick()

        assertFalse(reset)
        composeRule.onNodeWithText("저장소를 초기화할까요?").assertDoesNotExist()
    }

    @Test
    fun `reset button is a polite live region while idle`() {
        setDashboard(available = false)

        composeRule.onNodeWithText("저장소 초기화하고 계정 다시 추가")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }

    @Test
    fun `reset button shows the resetting label and is disabled while reset is in progress`() {
        setDashboard(available = false, isResettingSecureStorage = true)

        composeRule.onNodeWithText("초기화하는 중…")
            .assertExists()
            .assertIsNotEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        composeRule.onNodeWithText("저장소 초기화하고 계정 다시 추가").assertDoesNotExist()
    }
}
