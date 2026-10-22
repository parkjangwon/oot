package org.parkjw.apps.oot.data.local

import android.content.Context
import android.content.SharedPreferences

// Robolectric has no AndroidKeyStore, so tests use a plain file as the encrypted store.
internal const val TEST_ENCRYPTED_STORE = "test_encrypted_store"

internal fun testSecureStorage(
    context: Context,
    resolveConflict: (key: String, encrypted: String, legacy: String) -> String = { _, _, legacy -> legacy },
): SecureStorage = SecureStorage(
    context,
    openEncrypted = { it.getSharedPreferences(TEST_ENCRYPTED_STORE, Context.MODE_PRIVATE) },
    resolveConflict = resolveConflict,
)

internal fun testAccountPreferences(context: Context): AccountPreferences =
    AccountPreferences(
        context,
        testSecureStorage(context) { key, encrypted, legacy ->
            AccountPreferences.mergeStoredValue(key, encrypted, legacy)
        },
    )

internal fun testEncryptedPrefs(context: Context): SharedPreferences =
    context.getSharedPreferences(TEST_ENCRYPTED_STORE, Context.MODE_PRIVATE)
