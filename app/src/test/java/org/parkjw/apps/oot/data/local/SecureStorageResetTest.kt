package org.parkjw.apps.oot.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
class SecureStorageResetTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private var aliasDeleted = false

    private fun resetStorage(
        openEncrypted: (Context) -> SharedPreferences = {
            it.getSharedPreferences(TEST_ENCRYPTED_STORE, Context.MODE_PRIVATE)
        },
    ): SecureStorage = SecureStorage(
        context,
        openEncrypted = openEncrypted,
        encryptedPrefsName = TEST_ENCRYPTED_STORE,
        deleteKeyAlias = { aliasDeleted = true },
    )

    private fun account(id: String) = ProviderAccount(
        id = id,
        provider = ProviderType.DEEPSEEK,
        name = id,
        authType = AuthType.API_KEY,
        apiKey = "sk-test-000",
    )

    private fun legacyPrefs(): SharedPreferences =
        context.getSharedPreferences(LEGACY_STORE, Context.MODE_PRIVATE)

    @Test
    fun `reset removes only the encrypted and legacy stores and the key alias`() {
        testEncryptedPrefs(context).edit().putString("oot_accounts", "[]").commit()
        legacyPrefs().edit().putString("oot_accounts", "[]").commit()
        context.getSharedPreferences(UNRELATED_STORE, Context.MODE_PRIVATE)
            .edit().putString("keep", "1").commit()
        val dataStoreFile = File(context.filesDir, "datastore/language.preferences_pb").apply {
            parentFile?.mkdirs()
            writeText("keep")
        }

        val storage = resetStorage()

        assertTrue(storage.reset())

        assertTrue("the key alias must be deleted", aliasDeleted)
        assertTrue(testEncryptedPrefs(context).all.isEmpty())
        assertTrue(legacyPrefs().all.isEmpty())
        assertEquals("1", context.getSharedPreferences(UNRELATED_STORE, Context.MODE_PRIVATE).getString("keep", null))
        assertTrue("DataStore files must stay", dataStoreFile.isFile)
    }

    @Test
    fun `reset keeps non-credential settings and clears accounts`() {
        val before = AccountPreferences(context, resetStorage())
        before.saveAccount(account("a"))
        before.setServiceEnabled(false)
        before.setRefreshInterval(30)
        before.setAutoUpdateCheckEnabled(false)
        before.setShownInNotification("a", false)

        assertTrue(before.resetSecureStorage())

        assertTrue(before.isSecureStorageAvailable)
        assertTrue(before.accounts.value.isEmpty())
        assertFalse(before.isServiceEnabled.value)
        assertEquals(30, before.refreshIntervalMinutes.value)
        assertFalse(before.isAutoUpdateCheckEnabled.value)
        assertEquals(setOf("a"), before.notificationHiddenAccountIds.value)

        val after = AccountPreferences(context, resetStorage())
        assertTrue(after.isSecureStorageAvailable)
        assertTrue(after.accounts.value.isEmpty())
        assertFalse(after.isServiceEnabled.value)
        assertEquals(30, after.refreshIntervalMinutes.value)
        assertFalse(after.isAutoUpdateCheckEnabled.value)
        assertEquals(emptySet<String>(), after.notificationHiddenAccountIds.value)
    }

    @Test
    fun `status is encrypted when the store reopens after reset`() {
        val storage = resetStorage()

        assertEquals(StorageStatus.ENCRYPTED, storage.status)
        assertTrue(storage.reset())
        assertEquals(StorageStatus.ENCRYPTED, storage.status)
    }

    @Test
    fun `status stays unavailable when the store cannot reopen after reset`() {
        val attempts = AtomicInteger(0)
        val storage = resetStorage(openEncrypted = { c ->
            if (attempts.incrementAndGet() >= 2) throw IllegalStateException("keystore unavailable")
            c.getSharedPreferences(TEST_ENCRYPTED_STORE, Context.MODE_PRIVATE)
        })
        assertEquals(StorageStatus.ENCRYPTED, storage.status)

        assertFalse(storage.reset())

        assertEquals(StorageStatus.UNAVAILABLE, storage.status)
        assertTrue(aliasDeleted)
    }

    @Test
    fun `reset clears in-memory credentials even when the store cannot reopen`() {
        legacyPrefs().edit().putString("keep", "legacy").commit()
        val storage = SecureStorage(
            context,
            openEncrypted = { throw IllegalStateException("keystore unavailable") },
            encryptedPrefsName = TEST_ENCRYPTED_STORE,
            deleteKeyAlias = { aliasDeleted = true },
        )
        assertEquals(StorageStatus.UNAVAILABLE, storage.status)
        assertEquals("legacy", storage.getString("keep"))

        assertFalse(storage.reset())

        assertNull(storage.getString("keep"))
        assertTrue(legacyPrefs().all.isEmpty())
    }

    private companion object {
        const val LEGACY_STORE = "oot_fallback_prefs"
        const val UNRELATED_STORE = "oot_unrelated_prefs"
    }
}
