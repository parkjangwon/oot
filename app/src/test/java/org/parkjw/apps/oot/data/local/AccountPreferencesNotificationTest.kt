package org.parkjw.apps.oot.data.local

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.parkjw.apps.oot.data.model.AuthType
import org.parkjw.apps.oot.data.model.ProviderAccount
import org.parkjw.apps.oot.data.model.ProviderType
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

// A fresh AccountPreferences on the same application stands in for an app restart.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AccountPreferencesNotificationTest {

    private fun preferences() = testAccountPreferences(RuntimeEnvironment.getApplication())

    private fun account(id: String) = ProviderAccount(
        id = id,
        provider = ProviderType.CLAUDE,
        name = id,
        authType = AuthType.API_KEY,
        apiKey = "sk-test-000",
    )

    @Test
    fun `every account is shown by default, also for accounts saved before the setting existed`() {
        preferences().apply { saveAccount(account("a")); saveAccount(account("b")) }
        val restarted = preferences()
        assertEquals(listOf("a", "b"), restarted.accounts.value.map { it.id })
        assertEquals(emptySet<String>(), restarted.notificationHiddenAccountIds.value)
    }

    @Test
    fun `hidden accounts survive a restart and showing again removes them`() {
        val prefs = preferences()
        prefs.saveAccount(account("a"))
        prefs.saveAccount(account("b"))

        prefs.setShownInNotification("a", false)
        assertEquals(setOf("a"), prefs.notificationHiddenAccountIds.value)
        assertEquals(setOf("a"), preferences().notificationHiddenAccountIds.value)

        prefs.setShownInNotification("a", true)
        assertEquals(emptySet<String>(), prefs.notificationHiddenAccountIds.value)
        assertEquals(emptySet<String>(), preferences().notificationHiddenAccountIds.value)
    }

    @Test
    fun `hiding does not touch the account list or its drag order`() {
        val prefs = preferences()
        listOf("a", "b", "c").forEach { prefs.saveAccount(account(it)) }
        prefs.setAccountOrder(listOf("c", "a", "b"))

        prefs.setShownInNotification("a", false)
        assertEquals(listOf("c", "a", "b"), preferences().accounts.value.map { it.id })
    }

    @Test
    fun `deleting an account drops its hidden id`() {
        val prefs = preferences()
        prefs.saveAccount(account("a"))
        prefs.saveAccount(account("b"))
        prefs.setShownInNotification("a", false)
        prefs.setShownInNotification("b", false)

        prefs.deleteAccount("a")
        assertEquals(setOf("b"), prefs.notificationHiddenAccountIds.value)
        assertEquals(setOf("b"), preferences().notificationHiddenAccountIds.value)
    }

    @Test
    fun `hidden ids of accounts that no longer exist are cleaned on load`() {
        val prefs = preferences()
        prefs.saveAccount(account("a"))
        prefs.setShownInNotification("a", false)
        prefs.setShownInNotification("gone", false)

        assertEquals(setOf("a"), preferences().notificationHiddenAccountIds.value)
    }
}
