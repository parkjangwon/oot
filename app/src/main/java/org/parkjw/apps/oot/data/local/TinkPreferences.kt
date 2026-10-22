package org.parkjw.apps.oot.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
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
 */
internal class AeadPreferences(
    private val delegate: SharedPreferences,
    private val aead: Aead,
) : SharedPreferences by delegate {

    private fun seal(key: String, value: String): String =
        Base64.encodeToString(aead.encrypt(value.toByteArray(), key.toByteArray()), Base64.NO_WRAP)

    private fun open(key: String, sealed: String): String =
        String(aead.decrypt(Base64.decode(sealed, Base64.NO_WRAP), key.toByteArray()))

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
    }
}

/**
 * Opens the Tink-backed store. Anything old [openLegacy] returns is copied in once, verified, then removed with
 * [deleteLegacy]; a failure before that point throws and leaves the old store untouched.
 */
internal fun openTinkPreferences(
    context: Context,
    openAead: (Context) -> Aead = ::keystoreAead,
    openLegacy: (Context) -> SharedPreferences? = ::openLegacyEncryptedPreferences,
    deleteLegacy: (Context) -> Unit = ::deleteLegacyEncryptedPreferences,
): SharedPreferences {
    val store = AeadPreferences(context.getSharedPreferences(TINK_PREFS_NAME, Context.MODE_PRIVATE), openAead(context))
    if (store.getString(MIGRATED_KEY, null) == MIGRATED_VALUE) {
        deleteLegacy(context)
        return store
    }
    val legacy = openLegacy(context) ?: return store
    val entries = legacy.all.filterValues { it is String }.mapValues { it.value as String }
    val editor = store.edit()
    entries.forEach { (key, value) -> editor.putString(key, value) }
    editor.putString(MIGRATED_KEY, MIGRATED_VALUE)
    check(editor.commit()) { "could not write the migrated credentials" }
    if (!entries.all { (key, value) -> store.getString(key, null) == value }) {
        // No marker may survive unverified data: the next launch must migrate again.
        store.edit().remove(MIGRATED_KEY).commit()
        error("migrated credentials did not verify")
    }
    deleteLegacy(context)
    return store
}

private const val TINK_PREFS_NAME = "oot_tink_prefs"
private const val KEYSET_PREFS_NAME = "oot_tink_keyset"
private const val KEYSET_ALIAS = "oot_tink_keyset_entry"
private const val MASTER_KEY_URI = "android-keystore://oot_tink_master_key"
private const val MIGRATED_KEY = "oot_tink_migrated"
private const val MIGRATED_VALUE = "1"
private const val LEGACY_ENCRYPTED_PREFS_NAME = "oot_secure_prefs"

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
