package org.parkjw.apps.oot.data.local

import org.junit.Assert.assertEquals
import org.junit.Test
import org.parkjw.apps.oot.data.model.AuthType
import org.parkjw.apps.oot.data.model.ProviderAccount
import org.parkjw.apps.oot.data.model.ProviderType

class AccountOrderTest {

    private fun account(id: String, name: String = id) = ProviderAccount(
        id = id,
        provider = ProviderType.CLAUDE,
        name = name,
        authType = AuthType.API_KEY,
        apiKey = "sk-test-000",
    )

    @Test
    fun `merge keeps saved order and appends new ids in registration order`() {
        val merged = AccountPreferences.mergeAccountOrder(
            savedOrder = listOf("b", "a", "gone"),
            registrationIds = listOf("a", "b", "c", "d"),
        )
        assertEquals(listOf("b", "a", "c", "d"), merged)
    }

    @Test
    fun `merge with empty saved order returns registration order`() {
        assertEquals(
            listOf("a", "b"),
            AccountPreferences.mergeAccountOrder(emptyList(), listOf("a", "b")),
        )
    }

    @Test
    fun `merge ignores deleted ids only in saved order`() {
        assertEquals(
            listOf("a"),
            AccountPreferences.mergeAccountOrder(listOf("gone", "a", "also-gone"), listOf("a")),
        )
    }

    @Test
    fun `applyAccountOrder reorders account objects`() {
        val accounts = listOf(account("a"), account("b"), account("c"))
        val ordered = AccountPreferences.applyAccountOrder(accounts, listOf("c", "a"))
        assertEquals(listOf("c", "a", "b"), ordered.map { it.id })
    }
}
