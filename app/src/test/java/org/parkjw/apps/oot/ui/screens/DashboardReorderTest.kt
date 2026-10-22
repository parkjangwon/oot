package org.parkjw.apps.oot.ui.screens

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.center
import androidx.compose.ui.test.down
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
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

// Real long-press drags (#61). The gesture handler used to restart on the first swap, so no reorder was ever reported.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ko-w412dp-h1300dp-xxhdpi")
class DashboardReorderTest {

    @get:Rule
    val composeRule = createComposeRule()

    // Balance-only windows: no "least remaining" summary row, so the cards are the only list items.
    private fun usage(id: String, name: String, balance: Double = 10.0) = VendorUsage(
        accountId = id,
        providerType = ProviderType.OPENROUTER,
        displayName = name,
        windows = listOf(UsageWindow("credit", "credit", Remaining.Balance(balance, "USD"))),
    )

    private val abc = listOf(usage("a", "Alpha"), usage("b", "Bravo"), usage("c", "Charlie"))
    private val shown = mutableStateOf(abc)
    private val reported = mutableListOf<List<String>>()

    private fun show() {
        composeRule.setContent {
            OotTheme { DashboardScreen(vendorUsages = shown.value, onReorderVendors = { reported += it }) }
        }
    }

    private fun card(name: String) = composeRule.onNodeWithText(name)

    private fun pressAndHold(name: String) {
        card(name).performTouchInput { down(center) }
        composeRule.mainClock.advanceTimeBy(LONG_PRESS_MS)
    }

    // One move per frame, like a finger: the recomposition between moves is what used to cancel the drag.
    private fun move(name: String, positions: Float) {
        val dy = positions * card(name).fetchSemanticsNode().size.height * CARD_AND_GAP / STEPS
        repeat(STEPS) {
            card(name).performTouchInput { moveBy(Offset(0f, dy)) }
            composeRule.waitForIdle()
        }
    }

    private fun release(name: String) {
        card(name).performTouchInput { up() }
        composeRule.waitForIdle()
    }

    private fun longPressDrag(name: String, positions: Float) {
        pressAndHold(name)
        move(name, positions)
        release(name)
    }

    @Test
    fun `long press drag down one position reports the new order`() {
        show()
        longPressDrag("Alpha", 1f)
        assertEquals(listOf(listOf("b", "a", "c")), reported)
    }

    @Test
    fun `long press drag survives several swaps and reports the final order once`() {
        show()
        longPressDrag("Alpha", 2f)
        assertEquals(listOf(listOf("b", "c", "a")), reported)
    }

    @Test
    fun `long press drag up reports the new order`() {
        show()
        longPressDrag("Charlie", -1f)
        assertEquals(listOf(listOf("a", "c", "b")), reported)
    }

    @Test
    fun `long press drag to the top reports the new order`() {
        show()
        longPressDrag("Charlie", -2f)
        assertEquals(listOf(listOf("c", "a", "b")), reported)
    }

    @Test
    fun `two drags in a row each report their own order`() {
        show()
        longPressDrag("Alpha", 1f)
        longPressDrag("Charlie", -2f)
        assertEquals(listOf(listOf("b", "a", "c"), listOf("c", "b", "a")), reported)
    }

    @Test
    fun `a refresh landing mid-drag keeps the dragged order and still shows the new numbers`() {
        show()
        pressAndHold("Alpha")
        move("Alpha", 1f)
        // The data refresh still carries the persisted (old) order: the drag has not been reported yet.
        composeRule.runOnIdle { shown.value = listOf(usage("a", "Alpha", balance = 99.0), abc[1], abc[2]) }
        composeRule.waitForIdle()
        move("Alpha", 0.1f)
        release("Alpha")
        assertEquals(listOf(listOf("b", "a", "c")), reported)
        card("Alpha").assertTextContains("$99.00")
    }

    private companion object {
        const val LONG_PRESS_MS = 600L
        const val STEPS = 20
        // A card plus the list gap; the gap is small next to the card.
        const val CARD_AND_GAP = 1.1f
    }
}
