package org.parkjw.apps.oot.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
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

    private val _isAutoUpdateCheckEnabled = MutableStateFlow(true)
    val isAutoUpdateCheckEnabled: StateFlow<Boolean> = _isAutoUpdateCheckEnabled.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        val accountsJson = secureStorage.getString(KEY_ACCOUNTS, "[]") ?: "[]"
        val decoded = decodeAccounts(json, accountsJson)
        _accounts.value = decoded.accounts
        if (decoded.droppedCount > 0) persistAccounts(decoded.accounts)

        _isServiceEnabled.value = secureStorage.getString(KEY_SERVICE_ENABLED, "true")?.toBooleanStrictOrNull() ?: true
        _refreshIntervalMinutes.value = secureStorage.getString(KEY_REFRESH_INTERVAL, "15")?.toIntOrNull() ?: 15
        _isAutoUpdateCheckEnabled.value = secureStorage.getString(KEY_AUTO_UPDATE_CHECK, "true")?.toBooleanStrictOrNull() ?: true
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

    fun setAutoUpdateCheckEnabled(enabled: Boolean) {
        _isAutoUpdateCheckEnabled.value = enabled
        secureStorage.putString(KEY_AUTO_UPDATE_CHECK, enabled.toString())
    }

    private fun persistAccounts(list: List<ProviderAccount>) {
        try {
            val serialized = json.encodeToString(list)
            secureStorage.putString(KEY_ACCOUNTS, serialized)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    data class DecodedAccounts(val accounts: List<ProviderAccount>, val droppedCount: Int)

    companion object {
        // Decodes stored accounts one by one so an account of a removed provider (e.g. Cursor, #23)
        // or a corrupt entry is dropped without losing the others.
        fun decodeAccounts(json: Json, raw: String): DecodedAccounts {
            val array = try {
                json.parseToJsonElement(raw).jsonArray
            } catch (e: Exception) {
                return DecodedAccounts(emptyList(), 0)
            }
            val accounts = array.mapNotNull { element ->
                try {
                    json.decodeFromJsonElement(ProviderAccount.serializer(), element)
                } catch (e: Exception) {
                    null
                }
            }
            return DecodedAccounts(accounts, array.size - accounts.size)
        }

        private const val KEY_ACCOUNTS = "oot_accounts"
        private const val KEY_SERVICE_ENABLED = "oot_service_enabled"
        private const val KEY_REFRESH_INTERVAL = "oot_refresh_interval"
        private const val KEY_AUTO_UPDATE_CHECK = "oot_auto_update_check"
    }
}
