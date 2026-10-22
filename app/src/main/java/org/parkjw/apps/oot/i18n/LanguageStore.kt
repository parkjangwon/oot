package org.parkjw.apps.oot.i18n

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.languageDataStore by preferencesDataStore(name = "language")

/** Persists the selected [AppLanguage] in DataStore; an unset or unknown value reads as [AppLanguage.DEFAULT]. */
class LanguageStore(private val dataStore: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.languageDataStore)

    val language: Flow<AppLanguage> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { AppLanguage.fromTag(it[LanguageKey]) ?: AppLanguage.DEFAULT }

    suspend fun current(): AppLanguage = language.first()

    suspend fun set(language: AppLanguage) {
        dataStore.edit { it[LanguageKey] = language.tag }
    }

    private companion object {
        val LanguageKey = stringPreferencesKey("app_language")
    }
}
