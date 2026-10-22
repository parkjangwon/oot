package org.parkjw.apps.oot.data.local

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.parkjw.apps.oot.data.model.AuthType
import org.parkjw.apps.oot.data.model.ProviderAccount
import org.parkjw.apps.oot.data.model.ProviderType
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

// A restart is a new AccountPreferences over the same app storage (#61).
// Robolectric has no Keystore, so SecureStorage uses its plain fallback file. Fake keys only.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AccountPreferencesPersistenceTest {

    private val context: Application = ApplicationProvider.getApplicationContext()

    private fun account(id: String) = ProviderAccount(
        id = id,
        provider = ProviderType.OPENROUTER,
        name = id,
        authType = AuthType.API_KEY,
        apiKey = "sk-test-000",
    )

    private fun prefsWith(vararg ids: String) =
        AccountPreferences(context).also { prefs -> ids.forEach { prefs.saveAccount(account(it)) } }

    private fun restart() = AccountPreferences(context)

    private fun AccountPreferences.ids() = accounts.value.map { it.id }

    @Test
    fun `registration order is the default`() {
        prefsWith("a", "b", "c")
        assertEquals(listOf("a", "b", "c"), restart().ids())
    }

    @Test
    fun `a reorder survives a restart`() {
        val prefs = prefsWith("a", "b", "c")
        prefs.setAccountOrder(listOf("c", "a", "b"))
        assertEquals(listOf("c", "a", "b"), prefs.ids())
        assertEquals(listOf("c", "a", "b"), restart().ids())
    }

    @Test
    fun `a provider added after a reorder goes last and the rest keep their order`() {
        val prefs = prefsWith("a", "b", "c")
        prefs.setAccountOrder(listOf("c", "a", "b"))
        prefs.saveAccount(account("d"))
        assertEquals(listOf("c", "a", "b", "d"), prefs.ids())
        assertEquals(listOf("c", "a", "b", "d"), restart().ids())
    }

    @Test
    fun `a deleted provider is dropped and the rest keep their order`() {
        val prefs = prefsWith("a", "b", "c", "d")
        prefs.setAccountOrder(listOf("c", "a", "d", "b"))
        prefs.deleteAccount("a")
        assertEquals(listOf("c", "d", "b"), prefs.ids())
        assertEquals(listOf("c", "d", "b"), restart().ids())
    }

    @Test
    fun `saving an existing provider keeps the order and the new token`() {
        val prefs = prefsWith("a", "b", "c")
        prefs.setAccountOrder(listOf("c", "a", "b"))
        // What an OAuth token refresh does.
        prefs.saveAccount(account("a").copy(accessToken = "at-new", refreshToken = "rt-new", tokenExpiresAt = 1L))
        assertEquals(listOf("c", "a", "b"), prefs.ids())
        val restarted = restart()
        assertEquals(listOf("c", "a", "b"), restarted.ids())
        assertEquals("at-new", restarted.accounts.value.first { it.id == "a" }.accessToken)
    }

    @Test
    fun `a refresh update keeps the order, the name and the enable toggle`() {
        val prefs = prefsWith("a", "b", "c")
        prefs.setAccountOrder(listOf("c", "a", "b"))
        prefs.saveAccount(account("a").copy(name = "Renamed"))
        prefs.toggleAccount("a", false)

        // The copy a refresh started from still says name "a" and enabled.
        val refreshed = account("a").copy(accessToken = "at-new", refreshToken = "rt-new", tokenExpiresAt = 123L)
        assertTrue(prefs.updateAccountIfPresent(refreshed))

        assertEquals(listOf("c", "a", "b"), prefs.ids())
        val updated = prefs.accounts.value.first { it.id == "a" }
        assertEquals("Renamed", updated.name)
        assertFalse(updated.isEnabled)
        assertEquals("at-new", updated.accessToken)
        assertEquals(123L, updated.tokenExpiresAt)

        val restarted = restart()
        assertEquals(listOf("c", "a", "b"), restarted.ids())
        val stored = restarted.accounts.value.first { it.id == "a" }
        assertEquals("Renamed", stored.name)
        assertFalse(stored.isEnabled)
        assertEquals("at-new", stored.accessToken)
    }

    @Test
    fun `a refresh for a deleted provider writes nothing and does not re-add it`() {
        val prefs = prefsWith("a", "b")
        prefs.deleteAccount("a")

        assertFalse(prefs.updateAccountIfPresent(account("a").copy(accessToken = "at-new")))

        assertEquals(listOf("b"), prefs.ids())
        assertEquals(listOf("b"), restart().ids())
    }

    @Test
    fun `disabling and enabling a provider keeps the order`() {
        val prefs = prefsWith("a", "b", "c")
        prefs.setAccountOrder(listOf("c", "a", "b"))
        prefs.toggleAccount("a", false)
        assertEquals(listOf("c", "a", "b"), restart().ids())
        assertFalse(restart().accounts.value.first { it.id == "a" }.isEnabled)
        prefs.toggleAccount("a", true)
        assertEquals(listOf("c", "a", "b"), restart().ids())
        assertTrue(restart().accounts.value.first { it.id == "a" }.isEnabled)
    }

    @Test
    fun `the saved order wins over the stored list order`() {
        val prefs = prefsWith("a", "b", "c")
        prefs.setAccountOrder(listOf("b", "c", "a"))
        // Two restarts in a row: the launch itself must not reshuffle anything.
        assertEquals(listOf("b", "c", "a"), restart().ids())
        assertEquals(listOf("b", "c", "a"), restart().ids())
    }

    @Test
    fun `a saved Antigravity account is dropped on load and the rest keep order and toggles`() {
        val prefs = context.getSharedPreferences("oot_fallback_prefs", Application.MODE_PRIVATE)
        val rawAccounts = """
            [
              {"id":"c","provider":"ZAI","name":"c","authType":"API_KEY","apiKey":"k","isEnabled":false},
              {"id":"x","provider":"ANTIGRAVITY","name":"x","authType":"OAUTH","accessToken":"at","refreshToken":"rt"},
              {"id":"a","provider":"OPENROUTER","name":"a","authType":"API_KEY","apiKey":"k"},
              {"id":"b","provider":"CLAUDE","name":"b","authType":"API_KEY","apiKey":"k"}
            ]
        """.trimIndent()
        prefs.edit()
            .putString("oot_accounts", rawAccounts)
            .putString("oot_account_order", """["b","x","c","a"]""")
            .putString("oot_notification_hidden", """["x","a"]""")
            .commit()

        val loaded = AccountPreferences(context)
        assertEquals(listOf("b", "c", "a"), loaded.ids())
        assertFalse(loaded.accounts.value.first { it.id == "c" }.isEnabled)
        assertTrue(loaded.accounts.value.first { it.id == "b" }.isEnabled)
        assertTrue(loaded.accounts.value.first { it.id == "a" }.isEnabled)
        assertEquals(setOf("a"), loaded.notificationHiddenAccountIds.value)

        assertEquals(listOf("b", "c", "a"), restart().ids())
        assertFalse(prefs.getString("oot_accounts", null)!!.contains("ANTIGRAVITY"))
    }

    // Token refresh saves on the worker's thread while the UI reorders. The collector pauses a save right after it
    // published its list; without the lock the reorder finishes meanwhile and the paused save then writes its stale order.
    @Test
    fun `a reorder cannot slip into the middle of a token refresh save`() {
        val prefs = prefsWith("a", "b", "c")
        val paused = CountDownLatch(1)
        val resume = CountDownLatch(1)
        val armed = AtomicBoolean(false)
        val scope = CoroutineScope(Dispatchers.Unconfined)
        scope.launch {
            prefs.accounts.collect {
                if (armed.compareAndSet(true, false)) {
                    paused.countDown()
                    resume.await(10, TimeUnit.SECONDS)
                }
            }
        }
        armed.set(true)
        val refresh = thread { prefs.updateAccountIfPresent(account("b").copy(accessToken = "at-new")) }
        assertTrue(paused.await(10, TimeUnit.SECONDS))
        val reorder = thread { prefs.setAccountOrder(listOf("c", "b", "a")) }
        Thread.sleep(200) // long enough for an unlocked reorder to finish
        resume.countDown()
        refresh.join(10_000)
        reorder.join(10_000)
        scope.cancel()

        assertEquals(listOf("c", "b", "a"), prefs.ids())
        assertEquals(listOf("c", "b", "a"), restart().ids())
        assertEquals("at-new", restart().accounts.value.first { it.id == "b" }.accessToken)
    }
}
