package org.parkjw.apps.oot.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.parkjw.apps.oot.data.model.ProviderAccount

class AccountPreferences(context: Context) {
    private val secureStorage = SecureStorage(context)
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    private val _accounts = MutableStateFlow<List<ProviderAccount>>(emptyList())
    val accounts: StateFlow<List<ProviderAccount>> = _accounts.asStateFlow()

    private val _isServiceEnabled = MutableStateFlow(true)
    val isServiceEnabled: StateFlow<Boolean> = _isServiceEnabled.asStateFlow()

    private val _refreshIntervalMinutes = MutableStateFlow(15)
    val refreshIntervalMinutes: StateFlow<Int> = _refreshIntervalMinutes.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        val accountsJson = secureStorage.getString(KEY_ACCOUNTS, "[]") ?: "[]"
        try {
            val list = json.decodeFromString<List<ProviderAccount>>(accountsJson)
            _accounts.value = list
        } catch (e: Exception) {
            _accounts.value = emptyList()
        }

        _isServiceEnabled.value = secureStorage.getString(KEY_SERVICE_ENABLED, "true")?.toBooleanStrictOrNull() ?: true
        _refreshIntervalMinutes.value = secureStorage.getString(KEY_REFRESH_INTERVAL, "15")?.toIntOrNull() ?: 15
    }

    fun saveAccount(account: ProviderAccount) {
        val current = _accounts.value.toMutableList()
        val index = current.indexOfFirst { it.id == account.id }
        if (index >= 0) {
            current[index] = account
        } else {
            current.add(account)
        }
        _accounts.value = current
        persistAccounts(current)
    }

    fun deleteAccount(accountId: String) {
        val current = _accounts.value.filter { it.id != accountId }
        _accounts.value = current
        persistAccounts(current)
    }

    fun toggleAccount(accountId: String, isEnabled: Boolean) {
        val current = _accounts.value.map {
            if (it.id == accountId) it.copy(isEnabled = isEnabled) else it
        }
        _accounts.value = current
        persistAccounts(current)
    }

    fun setServiceEnabled(enabled: Boolean) {
        _isServiceEnabled.value = enabled
        secureStorage.putString(KEY_SERVICE_ENABLED, enabled.toString())
    }

    fun setRefreshInterval(minutes: Int) {
        _refreshIntervalMinutes.value = minutes
        secureStorage.putString(KEY_REFRESH_INTERVAL, minutes.toString())
    }

    private fun persistAccounts(list: List<ProviderAccount>) {
        try {
            val serialized = json.encodeToString(list)
            secureStorage.putString(KEY_ACCOUNTS, serialized)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        private const val KEY_ACCOUNTS = "oot_accounts"
        private const val KEY_SERVICE_ENABLED = "oot_service_enabled"
        private const val KEY_REFRESH_INTERVAL = "oot_refresh_interval"
    }
}
