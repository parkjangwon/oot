package org.parkjw.apps.oot.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.parkjw.apps.oot.data.model.ProviderType

class VendorLogoTest {

    @Test
    fun `official logos cover every provider or explicitly fall back`() {
        val withLogo = ProviderType.entries.mapNotNull { p ->
            VendorLogo.of(p)?.let { p }
        }.toSet()
        assertEquals(
            setOf(
                ProviderType.CLAUDE,
                ProviderType.OPENROUTER,
                ProviderType.XAI,
                ProviderType.ANTIGRAVITY,
                ProviderType.DEEPSEEK,
                ProviderType.OLLAMA_CLOUD,
                ProviderType.OPENCODE_GO,
            ),
            withLogo,
        )
        assertNull(VendorLogo.of(ProviderType.OPENAI))
        assertNull(VendorLogo.of(ProviderType.ZAI))
        assertNotNull(VendorLogo.of(ProviderType.CLAUDE)?.mono)
    }
}
