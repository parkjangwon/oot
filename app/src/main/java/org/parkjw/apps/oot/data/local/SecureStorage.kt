package org.parkjw.apps.oot.data.local

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore

enum class StorageStatus { ENCRYPTED, UNAVAILABLE }

class SecureStorage internal constructor(
    private val context: Context,
    private val openEncrypted: (Context) -> SharedPreferences = { openTinkPreferences(it) },
    private val legacyPrefsName: String = LEGACY_PREFS_NAME,
    private val encryptedPrefsName: String = TINK_PREFS_NAME,
    // The keyset file must go together with the Tink master key alias: a keyset without its key cannot reopen.
    private val extraStoreNames: List<String> = listOf(KEYSET_PREFS_NAME, LEGACY_ENCRYPTED_PREFS_NAME),
    private val deleteStoreFile: (Context, String) -> Unit = { ctx, name -> ctx.deleteSharedPreferences(name) },
    private val deleteKeyAlias: () -> Unit = ::deleteMasterKeyAlias,
    // A legacy plaintext file can be an orphan left by v1.3.1 after a transient failure, older than the
    // encrypted store, and stale OAuth refresh tokens must not overwrite newer ones: the encrypted value wins.
    private val resolveConflict: (key: String, encrypted: String, legacy: String) -> String = { _, encrypted, _ -> encrypted },
) {
    @Volatile
    var status: StorageStatus
        private set

    private var prefs: SharedPreferences?
    private val memory = mutableMapOf<String, String>()

    init {
        val opened = openWithRetry(context, openEncrypted)
        prefs = opened
        if (opened != null) {
            status = StorageStatus.ENCRYPTED
            migrate(context, opened)
        } else {
            status = StorageStatus.UNAVAILABLE
            seedFromLegacy(context)
        }
    }

    /**
     * Removes the encrypted and legacy stores and the key that may have made the encrypted store unreadable,
     * then opens a fresh store. Returns true when the new store is usable.
     */
    @Synchronized
    fun reset(): Boolean {
        memory.clear()
        prefs = null
        for (name in listOf(encryptedPrefsName, legacyPrefsName) + extraStoreNames) {
            try {
                deleteStoreFile(context, name)
            } catch (e: Exception) {
                // The reopen below decides whether the store is usable.
            }
        }
        try {
            deleteKeyAlias()
        } catch (e: Exception) {
            // A missing key is already the desired state.
        }
        val reopened = openWithRetry(context, openEncrypted)
        prefs = reopened
        status = if (reopened != null) StorageStatus.ENCRYPTED else StorageStatus.UNAVAILABLE
        return reopened != null
    }

    @Synchronized
    fun putString(key: String, value: String) {
        val store = prefs
        if (store != null) store.edit().putString(key, value).apply() else memory[key] = value
    }

    /** Like [putString] but returns only once the value (and any earlier queued write) is on disk. */
    @Synchronized
    @SuppressLint("ApplySharedPref") // blocking is the point: data that must survive a force-stop
    fun putStringNow(key: String, value: String) {
        val store = prefs
        if (store != null) store.edit().putString(key, value).commit() else memory[key] = value
    }

    @Synchronized
    fun getString(key: String, defaultValue: String? = null): String? {
        val store = prefs
        return if (store != null) store.getString(key, defaultValue) else memory[key] ?: defaultValue
    }

    @Synchronized
    fun remove(key: String) {
        val store = prefs
        if (store != null) store.edit().remove(key).apply() else memory.remove(key)
    }

    @Synchronized
    fun clear() {
        val store = prefs
        if (store != null) store.edit().clear().apply() else memory.clear()
    }

    private fun openWithRetry(context: Context, open: (Context) -> SharedPreferences): SharedPreferences? {
        repeat(2) {
            try {
                return open(context)
            } catch (e: Exception) {
                // Retry once: a transient Keystore failure should not drop to memory-only mode.
            }
        }
        return null
    }

    @SuppressLint("ApplySharedPref")
    private fun migrate(context: Context, encrypted: SharedPreferences) {
        val legacy = try {
            context.getSharedPreferences(legacyPrefsName, Context.MODE_PRIVATE)
        } catch (e: Exception) {
            return
        }
        // Marker present: the migration already committed. Never merge the legacy file again, or accounts the user
        // deleted after migration would come back. Only retry removing the leftover plaintext file.
        val alreadyMigrated = try {
            encrypted.getString(MARKER_KEY, null) == MARKER_VALUE
        } catch (e: Exception) {
            return
        }
        if (alreadyMigrated) {
            deleteLegacy(context)
            return
        }

        val entries = try {
            legacy.all
        } catch (e: Exception) {
            return
        }
        if (entries.isEmpty()) {
            deleteLegacy(context)
            return
        }

        val migrated = LinkedHashMap<String, String>()
        for ((key, value) in entries) {
            if (value !is String) continue
            val current = try {
                encrypted.getString(key, null)
            } catch (e: Exception) {
                return
            }
            migrated[key] = if (current == null) value else resolveConflict(key, current, value)
        }
        if (migrated.isEmpty()) {
            deleteLegacy(context)
            return
        }

        val editor = encrypted.edit()
        migrated.forEach { (key, value) -> editor.putString(key, value) }
        // The marker rides in the same commit as the migrated values: after this commit the merge must never run again.
        editor.putString(MARKER_KEY, MARKER_VALUE)
        val committed = try {
            editor.commit()
        } catch (e: Exception) {
            false
        }
        if (!committed) return

        val readBackOk = migrated.all { (key, value) ->
            try {
                encrypted.getString(key, null) == value
            } catch (e: Exception) {
                false
            }
        }
        if (!readBackOk) {
            // Do not leave a marker behind when the values did not land: the next launch must merge again.
            try {
                encrypted.edit().remove(MARKER_KEY).commit()
            } catch (e: Exception) {
                // Nothing more to do.
            }
            return
        }

        deleteLegacy(context)
    }

    private fun seedFromLegacy(context: Context) {
        val legacy = try {
            context.getSharedPreferences(legacyPrefsName, Context.MODE_PRIVATE)
        } catch (e: Exception) {
            return
        }
        val entries = try {
            legacy.all
        } catch (e: Exception) {
            return
        }
        for ((key, value) in entries) {
            if (value is String) memory[key] = value
        }
    }

    @SuppressLint("ApplySharedPref")
    private fun deleteLegacy(context: Context) {
        val legacy = try {
            context.getSharedPreferences(legacyPrefsName, Context.MODE_PRIVATE)
        } catch (e: Exception) {
            null
        }
        try {
            legacy?.edit()?.clear()?.commit()
        } catch (e: Exception) {
            // Fall through: deleteSharedPreferences below can still remove the file.
        }
        try {
            context.deleteSharedPreferences(legacyPrefsName)
        } catch (e: Exception) {
            // The marker keeps the next launch from re-merging; it retries the delete.
        }
    }
}

private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val LEGACY_PREFS_NAME = "oot_fallback_prefs"
private const val MARKER_KEY = "oot_legacy_migrated"
private const val MARKER_VALUE = "1"

// Removes the Tink master key and the old androidx default key; a missing alias is already the desired state.
private fun deleteMasterKeyAlias() {
    val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    for (alias in listOf(TINK_MASTER_KEY_ALIAS, MasterKey.DEFAULT_MASTER_KEY_ALIAS)) {
        if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
    }
}
