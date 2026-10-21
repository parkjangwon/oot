package org.parkjw.apps.oot.update

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first

interface UpdateStore {
    suspend fun lastAutoCheckDay(): Long?
    suspend fun setLastAutoCheckDay(epochDay: Long)
    suspend fun snooze(): UpdateSnooze?
    suspend fun setSnooze(snooze: UpdateSnooze)
}

private val Context.updateDataStore by preferencesDataStore(name = "update")

class DataStoreUpdateStore(private val dataStore: DataStore<Preferences>) : UpdateStore {
    constructor(context: Context) : this(context.updateDataStore)

    private suspend fun prefs(): Preferences =
        dataStore.data.catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }.first()

    override suspend fun lastAutoCheckDay(): Long? = prefs()[LastAutoCheckDayKey]

    override suspend fun setLastAutoCheckDay(epochDay: Long) {
        dataStore.edit { it[LastAutoCheckDayKey] = epochDay }
    }

    override suspend fun snooze(): UpdateSnooze? {
        val prefs = prefs()
        val version = prefs[SnoozeVersionKey] ?: return null
        val day = prefs[SnoozeDayKey] ?: return null
        return UpdateSnooze(version, day)
    }

    override suspend fun setSnooze(snooze: UpdateSnooze) {
        dataStore.edit {
            it[SnoozeVersionKey] = snooze.version
            it[SnoozeDayKey] = snooze.epochDay
        }
    }

    companion object {
        val LastAutoCheckDayKey = longPreferencesKey("last_auto_check_day")
        val SnoozeVersionKey = stringPreferencesKey("snooze_version")
        val SnoozeDayKey = longPreferencesKey("snooze_day")
    }
}
