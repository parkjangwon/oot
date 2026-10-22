package org.parkjw.apps.oot.data.local

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountPreferencesMergeTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun ids(raw: String): List<String> =
        json.parseToJsonElement(raw).jsonArray.map { it.jsonObject.getValue("id").jsonPrimitive.content }

    private fun strings(raw: String): List<String> =
        json.parseToJsonElement(raw).jsonArray.map { it.jsonPrimitive.content }

    @Test
    fun `accounts merge by id with the encrypted entry winning and its order first`() {
        val legacy = """[{"id":"a","provider":"OPENROUTER","name":"legacy-a","authType":"API_KEY","apiKey":"legacy-a"},""" +
            """{"id":"c","provider":"DEEPSEEK","name":"legacy-c","authType":"API_KEY","apiKey":"legacy-c"}]"""
        val encrypted = """[{"id":"a","provider":"OPENROUTER","name":"enc-a","authType":"API_KEY","apiKey":"enc-a"},""" +
            """{"id":"b","provider":"ZAI","name":"enc-b","authType":"API_KEY","apiKey":"enc-b"}]"""

        val merged = AccountPreferences.mergeStoredValue("oot_accounts", encrypted, legacy)

        assertEquals(listOf("a", "b", "c"), ids(merged))
        val a = json.parseToJsonElement(merged).jsonArray
            .first { it.jsonObject.getValue("id").jsonPrimitive.content == "a" }
        assertEquals("enc-a", a.jsonObject.getValue("name").jsonPrimitive.content)
    }

    @Test
    fun `entries that fail to decode are left out of the merge`() {
        val legacy = """[{"id":"a","provider":"OPENROUTER","name":"a","authType":"API_KEY","apiKey":"k"}]"""
        val encrypted = """[{"id":"x","provider":"ANTIGRAVITY","name":"x","authType":"OAUTH","accessToken":"at"}]"""

        val merged = AccountPreferences.mergeStoredValue("oot_accounts", encrypted, legacy)

        assertEquals(listOf("a"), ids(merged))
    }

    @Test
    fun `hidden ids are the union of both stores`() {
        val merged = AccountPreferences.mergeStoredValue(
            "oot_notification_hidden",
            encrypted = """["a","c"]""",
            legacy = """["b","a"]""",
        )

        assertEquals(listOf("a", "c", "b"), strings(merged))
    }

    @Test
    fun `account order is the encrypted order followed by legacy-only ids`() {
        val merged = AccountPreferences.mergeStoredValue(
            "oot_account_order",
            encrypted = """["a","b"]""",
            legacy = """["c","a"]""",
        )

        assertEquals(listOf("a", "b", "c"), strings(merged))
    }

    @Test
    fun `other keys take the encrypted value`() {
        assertEquals(
            "true",
            AccountPreferences.mergeStoredValue("oot_service_enabled", encrypted = "true", legacy = "false"),
        )
        assertEquals(
            "15",
            AccountPreferences.mergeStoredValue("oot_refresh_interval", encrypted = "15", legacy = "30"),
        )
    }
}
