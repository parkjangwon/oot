package org.parkjw.apps.oot.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import java.io.File

/**
 * String values are sealed with Tink AEAD (AES256-GCM) before they reach [delegate]; the key name is bound as
 * associated data so a value cannot be swapped between keys. Key names stay readable on disk.
 * Only strings are sealed: the typed puts other than putString throw [UnsupportedOperationException].
 */
internal class AeadPreferences(
    private val delegate: SharedPreferences,
    private val aead: Aead,
) : SharedPreferences by delegate {

    private fun seal(key: String, value: String): String = Base64.encodeToString(
        aead.encrypt(value.toByteArray(Charsets.UTF_8), key.toByteArray(Charsets.UTF_8)),
        Base64.NO_WRAP,
    )

    private fun open(key: String, sealed: String): String = String(
        aead.decrypt(Base64.decode(sealed, Base64.NO_WRAP), key.toByteArray(Charsets.UTF_8)),
        Charsets.UTF_8,
    )

    override fun getString(key: String, defValue: String?): String? =
        delegate.getString(key, null)?.let { open(key, it) } ?: defValue

    override fun getAll(): Map<String, *> =
        delegate.all.mapValues { (key, value) -> if (value is String) open(key, value) else value }

    override fun edit(): SharedPreferences.Editor = AeadEditor(delegate.edit())

    private inner class AeadEditor(private val editor: SharedPreferences.Editor) :
        SharedPreferences.Editor by editor {
        override fun putString(key: String, value: String?): SharedPreferences.Editor = apply {
            editor.putString(key, value?.let { seal(key, it) })
        }

        override fun putInt(key: String, value: Int): SharedPreferences.Editor = unsupported()
        override fun putLong(key: String, value: Long): SharedPreferences.Editor = unsupported()
        override fun putFloat(key: String, value: Float): SharedPreferences.Editor = unsupported()
        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = unsupported()
        override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor = unsupported()

        private fun unsupported(): Nothing = throw UnsupportedOperationException("only strings are sealed")
    }
}

/**
 * Opens the Tink-backed store. Anything old [openLegacy] returns is copied in once, verified, then removed with
 * [deleteLegacy]; a failure before that point throws and leaves the old store untouched.
 * After a crash the Tink store may hold part of the data without the marker; a key already there keeps its value.
 * One unreadable old entry blocks the marker and the delete (readable ones are still copied) and throws.
 */
internal fun openTinkPreferences(
    context: Context,
    openAead: (Context) -> Aead = ::keystoreAead,
    openLegacy: (Context) -> SharedPreferences? = ::openLegacyEncryptedPreferences,
    deleteLegacy: (Context) -> Unit = ::deleteLegacyEncryptedPreferences,
): SharedPreferences {
    val store = AeadPreferences(context.getSharedPreferences(TINK_PREFS_NAME, Context.MODE_PRIVATE), openAead(context))
    // A corrupt marker must not block legacy import: treat decrypt failure as "not migrated".
    val migrated = runCatching { store.getString(MIGRATED_KEY, null) }
        .onFailure { Log.w(TAG, "could not read migration marker: $MIGRATED_KEY") }
        .getOrNull()
    if (migrated == MIGRATED_VALUE) {
        deleteLegacy(context)
        return store
    }
    val legacy = openLegacy(context) ?: return store
    val entries = mutableMapOf<String, String>()
    // The real store decrypts every entry in getAll, so one bad entry can fail here too.
    val legacyKeys = runCatching { legacy.all.filterValues { it is String }.keys }.getOrNull()
    var unreadable = legacyKeys == null
    for (key in legacyKeys.orEmpty()) {
        // A value already in the Tink store (earlier partial run) is newer than the old copy: keep it.
        // A throwing read counts as not present, so the old value is copied over it.
        if (runCatching { store.getString(key, null) }.getOrNull() != null) continue
        try {
            legacy.getString(key, null)?.let { entries[key] = it }
        } catch (e: Exception) {
            unreadable = true
            // Key name only, never the value. The credential reset flow (#109) is the recovery path.
            Log.w(TAG, "could not read old credential entry: $key")
        }
    }
    val editor = store.edit()
    entries.forEach { (key, value) -> editor.putString(key, value) }
    check(editor.commit()) { "could not write the migrated credentials" }
    check(entries.all { (key, value) -> store.getString(key, null) == value }) {
        "migrated credentials did not verify"
    }
    // Without the marker the old store stays, so nothing is lost; SecureStorage falls back to UNAVAILABLE.
    check(!unreadable) { "an old credential entry could not be read" }
    // The marker is its own commit after the verified data: a crash before it leaves the old store intact.
    check(store.edit().putString(MIGRATED_KEY, MIGRATED_VALUE).commit()) { "could not write the migration marker" }
    deleteLegacy(context)
    return store
}

private const val TAG = "TinkPreferences"
internal const val TINK_PREFS_NAME = "oot_tink_prefs"
internal const val KEYSET_PREFS_NAME = "oot_tink_keyset"
private const val KEYSET_ALIAS = "oot_tink_keyset_entry"
internal const val TINK_MASTER_KEY_ALIAS = "oot_tink_master_key"
private const val MASTER_KEY_URI = "android-keystore://$TINK_MASTER_KEY_ALIAS"
private const val MIGRATED_KEY = "oot_tink_migrated"
private const val MIGRATED_VALUE = "1"
internal const val LEGACY_ENCRYPTED_PREFS_NAME = "oot_secure_prefs"

private fun keystoreAead(context: Context): Aead {
    AeadConfig.register()
    return AndroidKeysetManager.Builder()
        .withSharedPref(context, KEYSET_ALIAS, KEYSET_PREFS_NAME)
        .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
        .withMasterKeyUri(MASTER_KEY_URI)
        .build()
        .keysetHandle
        .getPrimitive(Aead::class.java)
}

// Only open the old store when its file exists; opening it would otherwise create a fresh master key.
private fun openLegacyEncryptedPreferences(context: Context): SharedPreferences? {
    if (!File(context.dataDir, "shared_prefs/$LEGACY_ENCRYPTED_PREFS_NAME.xml").exists()) return null
    val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
    return EncryptedSharedPreferences.create(
        context,
        LEGACY_ENCRYPTED_PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
}

private fun deleteLegacyEncryptedPreferences(context: Context) {
    // A failed delete is retried on the next launch because the migrated marker is already committed.
    runCatching { context.deleteSharedPreferences(LEGACY_ENCRYPTED_PREFS_NAME) }
}
