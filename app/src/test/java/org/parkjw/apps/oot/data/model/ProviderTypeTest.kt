package org.parkjw.apps.oot.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderTypeTest {
    @Test
    fun antigravityIsNotSelectable() {
        assertFalse(ProviderType.ANTIGRAVITY.isSelectable)
    }

    @Test
    fun otherProvidersAreSelectable() {
        val selectable = ProviderType.entries.filter { it.isSelectable }
        assertTrue(selectable.containsAll(ProviderType.entries - ProviderType.ANTIGRAVITY))
        assertFalse(selectable.contains(ProviderType.ANTIGRAVITY))
    }
}
