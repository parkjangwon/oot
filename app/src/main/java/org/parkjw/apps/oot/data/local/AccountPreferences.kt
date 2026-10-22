package org.parkjw.apps.oot.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import org.parkjw.apps.oot.data.model.ProviderAccount

class AccountPreferences(
    context: Context,
    private val secureStorage: SecureStorage = SecureStorage(
        context,
        resolveConflict = { key, encrypted, legacy -> mergeStoredValue(key, encrypted, legacy) },
    ),
) {
    val isSecureStorageAvailable: Boolean get() = secureStorage.status == StorageStatus.ENCRYPTED

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    private val _accounts = MutableStateFlow<List<ProviderAccount>>(emptyList())
    val accounts: StateFlow<List<ProviderAccount>> = _accounts.asStateFlow()

    private val _isServiceEnabled = MutableStateFlow(true)
    val isServiceEnabled: StateFlow<Boolean> = _isServiceEnabled.asStateFlow()

    private val _refreshIntervalMinutes = MutableStateFlow(15)
    val refreshIntervalMinutes: StateFlow<Int> = _refreshIntervalMinutes.asStateFlow()

    private val _isAutoUpdateCheckEnabled = MutableStateFlow(true)
    val isAutoUpdateCheckEnabled: StateFlow<Boolean> = _isAutoUpdateCheckEnabled.asStateFlow()

    // Accounts the user switched off in Settings; absent = shown, so upgrades keep everything in the notification.
    private val _notificationHiddenAccountIds = MutableStateFlow<Set<String>>(emptySet())
    val notificationHiddenAccountIds: StateFlow<Set<String>> = _notificationHiddenAccountIds.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        val accountsJson = secureStorage.getString(KEY_ACCOUNTS, "[]") ?: "[]"
        val decoded = decodeAccounts(json, accountsJson)
        val savedOrder = decodeIdList(secureStorage.getString(KEY_ACCOUNT_ORDER, "[]") ?: "[]")
        val ordered = applyAccountOrder(decoded.accounts, savedOrder)
        _accounts.value = ordered
        // Skip the (synchronous) write when nothing changed: that is every launch after the first.
        if (ordered.map { it.id } != savedOrder) persistOrder(ordered.map { it.id })
        if (decoded.droppedCount > 0) persistAccounts(ordered)

        val savedHidden = decodeIdList(secureStorage.getString(KEY_NOTIFICATION_HIDDEN, "[]") ?: "[]").toSet()
        val hidden = savedHidden.intersect(ordered.map { it.id }.toSet())
        _notificationHiddenAccountIds.value = hidden
        if (hidden != savedHidden) persistNotificationHidden(hidden)

        _isServiceEnabled.value = secureStorage.getString(KEY_SERVICE_ENABLED, "true")?.toBooleanStrictOrNull() ?: true
        _refreshIntervalMinutes.value = secureStorage.getString(KEY_REFRESH_INTERVAL, "15")?.toIntOrNull() ?: 15
        _isAutoUpdateCheckEnabled.value = secureStorage.getString(KEY_AUTO_UPDATE_CHECK, "true")?.toBooleanStrictOrNull() ?: true
    }

    @Synchronized
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
        persistOrder(current.map { it.id })
    }

    @Synchronized
    fun deleteAccount(accountId: String) {
        val current = _accounts.value.filter { it.id != accountId }
        _accounts.value = current
        persistAccounts(current)
        persistOrder(current.map { it.id })
        setShownInNotification(accountId, true) // drops the hidden id with its account
    }

    @Synchronized
    fun setAccountOrder(accountIds: List<String>) {
        val ordered = applyAccountOrder(_accounts.value, accountIds)
        _accounts.value = ordered
        persistAccounts(ordered)
        persistOrder(ordered.map { it.id })
    }

    @Synchronized
    fun toggleAccount(accountId: String, isEnabled: Boolean) {
        val current = _accounts.value.map {
            if (it.id == accountId) it.copy(isEnabled = isEnabled) else it
        }
        _accounts.value = current
        persistAccounts(current)
    }

    @Synchronized
    fun setShownInNotification(accountId: String, shown: Boolean) {
        val hidden = _notificationHiddenAccountIds.value.let { if (shown) it - accountId else it + accountId }
        _notificationHiddenAccountIds.value = hidden
        persistNotificationHidden(hidden)
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

    // commit, not apply: a drag must survive a force-stop right after it. It also flushes the accounts write queued before it.
    private fun persistOrder(accountIds: List<String>) {
        try {
            secureStorage.putStringNow(KEY_ACCOUNT_ORDER, json.encodeToString(accountIds))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun persistNotificationHidden(accountIds: Set<String>) {
        try {
            // Same path as the drag order: committed, so a toggle survives a force-stop right after it.
            secureStorage.putStringNow(KEY_NOTIFICATION_HIDDEN, json.encodeToString(accountIds))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun decodeIdList(raw: String): List<String> = try {
        json.decodeFromString<List<String>>(raw)
    } catch (e: Exception) {
        emptyList()
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

        /** Saved ids first (skipping deleted); accounts missing from saved order append in registration order. */
        fun mergeAccountOrder(savedOrder: List<String>, registrationIds: List<String>): List<String> {
            val known = registrationIds.toSet()
            val ordered = savedOrder.filter { it in known }
            val orderedSet = ordered.toSet()
            return ordered + registrationIds.filter { it !in orderedSet }
        }

        fun applyAccountOrder(
            accounts: List<ProviderAccount>,
            savedOrder: List<String>,
        ): List<ProviderAccount> {
            val byId = accounts.associateBy { it.id }
            return mergeAccountOrder(savedOrder, accounts.map { it.id }).mapNotNull { byId[it] }
        }

        private val mergeJson = Json { ignoreUnknownKeys = true; prettyPrint = false }

        /** Resolves a key found in both the encrypted and the legacy plaintext store. */
        internal fun mergeStoredValue(key: String, encrypted: String, legacy: String): String = when (key) {
            KEY_ACCOUNTS -> mergeAccounts(encrypted, legacy)
            KEY_NOTIFICATION_HIDDEN, KEY_ACCOUNT_ORDER -> mergeIdLists(encrypted, legacy)
            else -> legacy
        }

        private fun mergeAccounts(encrypted: String, legacy: String): String {
            val byId = LinkedHashMap<String, ProviderAccount>()
            decodeAccounts(mergeJson, legacy).accounts.forEach { byId[it.id] = it }
            decodeAccounts(mergeJson, encrypted).accounts.forEach { byId.putIfAbsent(it.id, it) }
            return mergeJson.encodeToString(byId.values.toList())
        }

        private fun mergeIdLists(encrypted: String, legacy: String): String {
            val ids = LinkedHashSet<String>()
            ids += decodeIdList(mergeJson, legacy)
            ids += decodeIdList(mergeJson, encrypted)
            return mergeJson.encodeToString(ids.toList())
        }

        private fun decodeIdList(json: Json, raw: String): List<String> = try {
            json.decodeFromString<List<String>>(raw)
        } catch (e: Exception) {
            emptyList()
        }

        private const val KEY_ACCOUNTS = "oot_accounts"
        private const val KEY_ACCOUNT_ORDER = "oot_account_order"
        private const val KEY_NOTIFICATION_HIDDEN = "oot_notification_hidden"
        private const val KEY_SERVICE_ENABLED = "oot_service_enabled"
        private const val KEY_REFRESH_INTERVAL = "oot_refresh_interval"
        private const val KEY_AUTO_UPDATE_CHECK = "oot_auto_update_check"
    }
}
