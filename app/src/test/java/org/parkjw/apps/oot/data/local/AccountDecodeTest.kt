package org.parkjw.apps.oot.data.local

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import org.parkjw.apps.oot.data.model.ProviderType

class AccountDecodeTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `accounts of a removed provider are dropped and the rest kept`() {
        val raw = """
            [
              {"id":"a","provider":"CLAUDE","name":"Claude"},
              {"id":"b","provider":"CURSOR","name":"Cursor"},
              {"id":"c","provider":"OPENROUTER","name":"OpenRouter"}
            ]
        """.trimIndent()
        val decoded = AccountPreferences.decodeAccounts(json, raw)
        assertEquals(listOf("a", "c"), decoded.accounts.map { it.id })
        assertEquals(listOf(ProviderType.CLAUDE, ProviderType.OPENROUTER), decoded.accounts.map { it.provider })
        assertEquals(1, decoded.droppedCount)
    }

    @Test
    fun `invalid json yields empty list`() {
        val decoded = AccountPreferences.decodeAccounts(json, "not json")
        assertEquals(0, decoded.accounts.size)
        assertEquals(0, decoded.droppedCount)
    }
}
