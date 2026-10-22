package org.parkjw.apps.oot.data.local

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

enum class StorageStatus { ENCRYPTED, UNAVAILABLE }

class SecureStorage internal constructor(
    context: Context,
    openEncrypted: (Context) -> SharedPreferences = ::openEncryptedPreferences,
    private val legacyPrefsName: String = LEGACY_PREFS_NAME,
    private val resolveConflict: (key: String, encrypted: String, legacy: String) -> String = { _, _, legacy -> legacy },
) {
    val status: StorageStatus
    private val prefs: SharedPreferences?
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

    fun putString(key: String, value: String) {
        val store = prefs
        if (store != null) store.edit().putString(key, value).apply() else memory[key] = value
    }

    /** Like [putString] but returns only once the value (and any earlier queued write) is on disk. */
    @SuppressLint("ApplySharedPref") // blocking is the point: data that must survive a force-stop
    fun putStringNow(key: String, value: String) {
        val store = prefs
        if (store != null) store.edit().putString(key, value).commit() else memory[key] = value
    }

    fun getString(key: String, defaultValue: String? = null): String? {
        val store = prefs
        return if (store != null) store.getString(key, defaultValue) else memory[key] ?: defaultValue
    }

    fun remove(key: String) {
        val store = prefs
        if (store != null) store.edit().remove(key).apply() else memory.remove(key)
    }

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
        if (!readBackOk) return

        try {
            if (legacy.edit().clear().commit()) deleteLegacy(context)
        } catch (e: Exception) {
            // Keep the legacy file: the next launch retries.
        }
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

    private fun deleteLegacy(context: Context) {
        try {
            context.deleteSharedPreferences(legacyPrefsName)
        } catch (e: Exception) {
            // Nothing left to migrate; the next launch retries the delete.
        }
    }
}

private const val ENCRYPTED_PREFS_NAME = "oot_secure_prefs"
private const val LEGACY_PREFS_NAME = "oot_fallback_prefs"

private fun openEncryptedPreferences(context: Context): SharedPreferences {
    val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    return EncryptedSharedPreferences.create(
        context,
        ENCRYPTED_PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
}
