package org.parkjw.apps.oot.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressLevelTest {
    @Test
    fun aboveTwentyIsNormal() {
        assertEquals(ProgressLevel.Normal, progressLevel(21))
        assertEquals(ProgressLevel.Normal, progressLevel(100))
    }

    @Test
    fun twentyAndBelowIsWarning() {
        assertEquals(ProgressLevel.Warning, progressLevel(20))
        assertEquals(ProgressLevel.Warning, progressLevel(6))
    }

    @Test
    fun fiveAndBelowIsError() {
        assertEquals(ProgressLevel.Error, progressLevel(5))
        assertEquals(ProgressLevel.Error, progressLevel(0))
    }
}
