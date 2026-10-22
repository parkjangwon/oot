package org.parkjw.apps.oot.data.local

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.atomic.AtomicInteger
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SecureStorageMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val v131Accounts = """
        [
          {"id":"api-1","provider":"OPENROUTER","name":"API","authType":"API_KEY","apiKey":"sk-test-000","isEnabled":true},
          {"id":"oauth-1","provider":"CLAUDE","name":"OAuth","authType":"OAUTH","accessToken":"at-test","refreshToken":"rt-test","tokenExpiresAt":1234567890000,"isEnabled":true}
        ]
    """.trimIndent()

    private fun legacyPrefs(): SharedPreferences =
        context.getSharedPreferences("oot_fallback_prefs", Context.MODE_PRIVATE)

    private fun seedV131(prefs: SharedPreferences) {
        prefs.edit()
            .putString("oot_accounts", v131Accounts)
            .putString("oot_account_order", """["oauth-1","api-1"]""")
            .putString("oot_notification_hidden", """["api-1"]""")
            .putString("oot_service_enabled", "false")
            .putString("oot_refresh_interval", "30")
            .commit()
    }

    private fun account(id: String) = ProviderAccount(
        id = id,
        provider = ProviderType.DEEPSEEK,
        name = id,
        authType = AuthType.API_KEY,
        apiKey = "sk-test-000",
    )

    @Test
    fun `v1_3_1 data in the encrypted store loads unchanged and nothing is written to plaintext`() {
        seedV131(testEncryptedPrefs(context))

        val prefs = testAccountPreferences(context)

        assertEquals(listOf("oauth-1", "api-1"), prefs.accounts.value.map { it.id })
        val oauth = prefs.accounts.value.first { it.id == "oauth-1" }
        assertEquals("at-test", oauth.accessToken)
        assertEquals("rt-test", oauth.refreshToken)
        assertEquals(1234567890000L, oauth.tokenExpiresAt)
        assertEquals("sk-test-000", prefs.accounts.value.first { it.id == "api-1" }.apiKey)
        assertFalse(prefs.isServiceEnabled.value)
        assertEquals(30, prefs.refreshIntervalMinutes.value)
        assertEquals(setOf("api-1"), prefs.notificationHiddenAccountIds.value)
        assertTrue(prefs.isSecureStorageAvailable)
        assertTrue(legacyPrefs().all.isEmpty())
    }

    @Test
    fun `legacy plaintext only is migrated into the encrypted store`() {
        seedV131(legacyPrefs())

        val prefs = testAccountPreferences(context)

        assertEquals(listOf("oauth-1", "api-1"), prefs.accounts.value.map { it.id })
        assertEquals("at-test", prefs.accounts.value.first { it.id == "oauth-1" }.accessToken)
        val encrypted = testEncryptedPrefs(context)
        assertEquals("false", encrypted.getString("oot_service_enabled", null))
        assertEquals("30", encrypted.getString("oot_refresh_interval", null))
        assertEquals("""["oauth-1","api-1"]""", encrypted.getString("oot_account_order", null))
        assertEquals("""["api-1"]""", encrypted.getString("oot_notification_hidden", null))
        assertTrue(encrypted.getString("oot_accounts", null)!!.contains("oauth-1"))
        assertTrue(legacyPrefs().all.isEmpty())
    }

    @Test
    fun `migration is idempotent across restarts`() {
        seedV131(legacyPrefs())

        val first = testAccountPreferences(context)
        val second = testAccountPreferences(context)

        assertEquals(listOf("oauth-1", "api-1"), first.accounts.value.map { it.id })
        assertEquals(first.accounts.value.map { it.id }, second.accounts.value.map { it.id })
        assertEquals(2, second.accounts.value.size)
        assertTrue(legacyPrefs().all.isEmpty())
    }

    @Test
    fun `both stores present merge accounts by id with encrypted winning and hidden ids unioned`() {
        testEncryptedPrefs(context).edit()
            .putString(
                "oot_accounts",
                """[{"id":"a","provider":"OPENROUTER","name":"enc-a","authType":"API_KEY","apiKey":"enc-a"},""" +
                    """{"id":"b","provider":"ZAI","name":"enc-b","authType":"API_KEY","apiKey":"enc-b"}]"""
            )
            .putString("oot_account_order", """["a","b"]""")
            .putString("oot_notification_hidden", """["a"]""")
            .commit()
        legacyPrefs().edit()
            .putString(
                "oot_accounts",
                """[{"id":"a","provider":"OPENROUTER","name":"legacy-a","authType":"API_KEY","apiKey":"legacy-a"},""" +
                    """{"id":"c","provider":"DEEPSEEK","name":"legacy-c","authType":"API_KEY","apiKey":"legacy-c"}]"""
            )
            .putString("oot_account_order", """["c","a"]""")
            .putString("oot_notification_hidden", """["b","c"]""")
            .commit()

        val prefs = testAccountPreferences(context)

        assertEquals(listOf("a", "b", "c"), prefs.accounts.value.map { it.id })
        assertEquals("enc-a", prefs.accounts.value.first { it.id == "a" }.name)
        assertEquals(setOf("a", "b", "c"), prefs.notificationHiddenAccountIds.value)
        assertTrue(legacyPrefs().all.isEmpty())
    }

    @Test
    fun `an orphan legacy account with an older token does not overwrite the newer encrypted token`() {
        testEncryptedPrefs(context).edit()
            .putString(
                "oot_accounts",
                """[{"id":"oauth-1","provider":"CLAUDE","name":"OAuth","authType":"OAUTH",""" +
                    """"accessToken":"at-new","refreshToken":"rt-new","tokenExpiresAt":1234567890000}]"""
            )
            .putString("oot_account_order", """["oauth-1"]""")
            .commit()
        seedV131(legacyPrefs()) // same oauth-1 id, older at-test/rt-test

        val first = testAccountPreferences(context)

        val oauth = first.accounts.value.first { it.id == "oauth-1" }
        assertEquals("at-new", oauth.accessToken)
        assertEquals("rt-new", oauth.refreshToken)
        assertTrue(legacyPrefs().all.isEmpty())

        val restarted = testAccountPreferences(context)

        assertEquals("at-new", restarted.accounts.value.first { it.id == "oauth-1" }.accessToken)
        assertTrue(legacyPrefs().all.isEmpty())
    }

    @Test
    fun `a legacy file left after migration is deleted without re-adding accounts deleted since then`() {
        seedV131(legacyPrefs())

        val first = testAccountPreferences(context)
        assertEquals(listOf("oauth-1", "api-1"), first.accounts.value.map { it.id })
        first.deleteAccount("api-1")
        assertTrue(legacyPrefs().all.isEmpty())

        // The migration committed, but clear/delete of the plaintext failed: the stale file is back on disk.
        seedV131(legacyPrefs())

        val second = testAccountPreferences(context)

        assertEquals(listOf("oauth-1"), second.accounts.value.map { it.id })
        assertFalse(second.accounts.value.any { it.id == "api-1" })
        assertTrue(legacyPrefs().all.isEmpty())
    }

    @Test
    fun `the migration marker is not exposed as an account or a setting`() {
        seedV131(legacyPrefs())

        val prefs = testAccountPreferences(context)

        assertEquals(listOf("oauth-1", "api-1"), prefs.accounts.value.map { it.id })
        assertFalse(prefs.isServiceEnabled.value)
        assertEquals(30, prefs.refreshIntervalMinutes.value)
        assertEquals(setOf("api-1"), prefs.notificationHiddenAccountIds.value)
        assertEquals("1", testEncryptedPrefs(context).getString("oot_legacy_migrated", null))

        val restarted = testAccountPreferences(context)
        assertEquals(listOf("oauth-1", "api-1"), restarted.accounts.value.map { it.id })
        assertFalse(restarted.isServiceEnabled.value)
    }

    @Test
    fun `a failing legacy clear still removes the plaintext file`() {
        seedV131(legacyPrefs())
        val wrapper = ClearFailingContext(context)

        val storage = SecureStorage(wrapper, openEncrypted = { c ->
            c.getSharedPreferences(TEST_ENCRYPTED_STORE, Context.MODE_PRIVATE)
        })

        assertEquals(StorageStatus.ENCRYPTED, storage.status)
        assertEquals("1", testEncryptedPrefs(context).getString("oot_legacy_migrated", null))
        assertTrue(wrapper.deleteAttempted)
        assertTrue(legacyPrefs().all.isEmpty())
    }

    @Test
    fun `unavailable storage reads legacy into memory and never writes plaintext`() {
        seedV131(legacyPrefs())
        val before = HashMap(legacyPrefs().all)

        val storage = SecureStorage(context, openEncrypted = { throw IllegalStateException("keystore unavailable") })
        assertEquals(StorageStatus.UNAVAILABLE, storage.status)
        val prefs = AccountPreferences(context, storage)

        assertEquals(listOf("oauth-1", "api-1"), prefs.accounts.value.map { it.id })
        assertFalse(prefs.isSecureStorageAvailable)
        assertEquals(before, HashMap(legacyPrefs().all))
        assertTrue(testEncryptedPrefs(context).all.isEmpty())

        prefs.saveAccount(account("new-1"))

        assertEquals(listOf("oauth-1", "api-1", "new-1"), prefs.accounts.value.map { it.id })
        assertEquals(before, HashMap(legacyPrefs().all))
        assertTrue(testEncryptedPrefs(context).all.isEmpty())
    }

    @Test
    fun `a later successful launch migrates the data kept from an unavailable first launch`() {
        seedV131(legacyPrefs())

        val first = SecureStorage(context, openEncrypted = { throw IllegalStateException("keystore unavailable") })
        val firstPrefs = AccountPreferences(context, first)
        assertEquals(StorageStatus.UNAVAILABLE, first.status)
        assertEquals(listOf("oauth-1", "api-1"), firstPrefs.accounts.value.map { it.id })
        assertTrue(testEncryptedPrefs(context).all.isEmpty())

        val second = testSecureStorage(context)
        assertEquals(StorageStatus.ENCRYPTED, second.status)
        val secondPrefs = AccountPreferences(context, second)
        assertEquals(listOf("oauth-1", "api-1"), secondPrefs.accounts.value.map { it.id })
        assertEquals("at-test", secondPrefs.accounts.value.first { it.id == "oauth-1" }.accessToken)
        assertEquals("30", testEncryptedPrefs(context).getString("oot_refresh_interval", null))
        assertTrue(legacyPrefs().all.isEmpty())
    }

    @Test
    fun `a failed first open attempt is retried once`() {
        seedV131(testEncryptedPrefs(context))
        val attempts = AtomicInteger(0)

        val storage = SecureStorage(context, openEncrypted = { c ->
            if (attempts.incrementAndGet() == 1) throw IllegalStateException("transient keystore failure")
            c.getSharedPreferences(TEST_ENCRYPTED_STORE, Context.MODE_PRIVATE)
        })

        assertEquals(StorageStatus.ENCRYPTED, storage.status)
        assertEquals(2, attempts.get())
        assertEquals("30", storage.getString("oot_refresh_interval", null))
    }

    @Test
    fun `a failed migration commit keeps the legacy data`() {
        seedV131(legacyPrefs())
        val before = HashMap(legacyPrefs().all)

        val storage = SecureStorage(context, openEncrypted = { c ->
            CommitFailingPreferences(c.getSharedPreferences(TEST_ENCRYPTED_STORE, Context.MODE_PRIVATE))
        })

        assertEquals(StorageStatus.ENCRYPTED, storage.status)
        assertEquals(before, HashMap(legacyPrefs().all))
        assertTrue(testEncryptedPrefs(context).all.isEmpty())
    }

    private class ClearFailingContext(base: Context) : ContextWrapper(base) {
        var deleteAttempted = false

        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            super.getSharedPreferences(name, mode).let {
                if (name == "oot_fallback_prefs") ClearFailingPreferences(it) else it
            }

        override fun deleteSharedPreferences(name: String): Boolean {
            if (name == "oot_fallback_prefs") deleteAttempted = true
            return super.deleteSharedPreferences(name)
        }
    }

    private class ClearFailingPreferences(private val delegate: SharedPreferences) : SharedPreferences by delegate {
        override fun edit(): SharedPreferences.Editor = ClearFailingEditor(delegate.edit())
    }

    private class ClearFailingEditor(private val delegate: SharedPreferences.Editor) : SharedPreferences.Editor by delegate {
        override fun commit(): Boolean = false
        override fun apply() = Unit
    }

    private class CommitFailingPreferences(private val delegate: SharedPreferences) : SharedPreferences by delegate {
        override fun edit(): SharedPreferences.Editor = CommitFailingEditor(delegate.edit())
    }

    private class CommitFailingEditor(private val delegate: SharedPreferences.Editor) : SharedPreferences.Editor by delegate {
        override fun commit(): Boolean = false
        override fun apply() = Unit
    }
}
