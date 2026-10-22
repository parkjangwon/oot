package org.parkjw.apps.oot.data.model

import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderTypeTest {
    @Test
    fun allProvidersAreSelectable() {
        assertTrue(ProviderType.entries.all { it.isSelectable })
    }
}
