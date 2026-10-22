package org.parkjw.apps.oot.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.aead.AeadConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TinkMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    // Robolectric has no AndroidKeyStore: a real Tink AEAD with an in-memory keyset stands in for the Keystore one.
    private val aead: Aead by lazy {
        AeadConfig.register()
        KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM")).getPrimitive(Aead::class.java)
    }

    private fun oldStore(): SharedPreferences = context.getSharedPreferences("fake_old_encrypted", Context.MODE_PRIVATE)
    private fun tinkFile(): SharedPreferences = context.getSharedPreferences("oot_tink_prefs", Context.MODE_PRIVATE)

    private fun open(
        legacy: SharedPreferences? = oldStore(),
        openAead: (Context) -> Aead = { aead },
    ): SharedPreferences = openTinkPreferences(
        context,
        openAead = openAead,
        openLegacy = { legacy },
        deleteLegacy = { it.deleteSharedPreferences("fake_old_encrypted") },
    )

    private fun seedOld() {
        oldStore().edit()
            .putString("oot_accounts", """[{"id":"api-1","apiKey":"sk-test-000"}]""")
            .putString("oot_refresh_interval", "30")
            .commit()
    }

    @Test
    fun `values are sealed on disk and read back through the store`() {
        val store = open(legacy = null)
        store.edit().putString("oot_accounts", "sk-test-000").commit()

        assertEquals("sk-test-000", store.getString("oot_accounts", null))
        assertNotEquals("sk-test-000", tinkFile().getString("oot_accounts", null))
        assertEquals("fallback", store.getString("missing", "fallback"))
    }

    @Test
    fun `old encrypted store is copied into the Tink store and removed`() {
        seedOld()

        val store = open()

        assertEquals("30", store.getString("oot_refresh_interval", null))
        assertTrue(store.getString("oot_accounts", null)!!.contains("sk-test-000"))
        assertTrue(oldStore().all.isEmpty())
    }

    @Test
    fun `migration through SecureStorage keeps the status encrypted and accounts readable`() {
        seedOld()

        val storage = SecureStorage(context, openEncrypted = { open() })

        assertEquals(StorageStatus.ENCRYPTED, storage.status)
        assertEquals("30", storage.getString("oot_refresh_interval", null))
    }

    @Test
    fun `a restart after migration does not re-import a stale old store`() {
        seedOld()
        open().edit().remove("oot_accounts").commit()
        seedOld() // the old file came back because its delete failed earlier

        val store = open()

        assertNull(store.getString("oot_accounts", null))
        assertTrue(oldStore().all.isEmpty())
    }

    @Test
    fun `an unreadable old store leaves it untouched and reports unavailable`() {
        seedOld()
        val before = HashMap(oldStore().all)
        val broken = object : SharedPreferences by oldStore() {
            override fun getAll(): Map<String, *> = throw IllegalStateException("keystore key lost")
        }

        val storage = SecureStorage(context, openEncrypted = { open(legacy = broken) })

        assertEquals(StorageStatus.UNAVAILABLE, storage.status)
        assertEquals(before, HashMap(oldStore().all))
        assertNull(tinkFile().getString("oot_tink_migrated", null))
    }

    @Test
    fun `a keyset failure reports unavailable and keeps the old store`() {
        seedOld()
        val before = HashMap(oldStore().all)

        val storage = SecureStorage(context, openEncrypted = {
            open(openAead = { throw IllegalStateException("keystore unavailable") })
        })

        assertEquals(StorageStatus.UNAVAILABLE, storage.status)
        assertEquals(before, HashMap(oldStore().all))
    }

    @Test
    fun `a value that fails verification keeps the old store and leaves no marker`() {
        seedOld()
        val before = HashMap(oldStore().all)
        val flaky = object : Aead {
            override fun encrypt(plaintext: ByteArray, associatedData: ByteArray) = aead.encrypt(plaintext, associatedData)
            override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray) = "wrong".toByteArray()
        }

        try {
            open(openAead = { flaky })
            fail("expected the migration to fail")
        } catch (e: IllegalStateException) {
            // expected: SecureStorage turns this into UNAVAILABLE
        }

        assertEquals(before, HashMap(oldStore().all))
        assertFalse(tinkFile().contains("oot_tink_migrated"))
    }
}
