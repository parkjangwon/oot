package org.parkjw.apps.oot.data.remote

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.parkjw.apps.oot.data.local.AccountPreferences
import org.parkjw.apps.oot.data.model.ProviderAccount
import org.parkjw.apps.oot.data.model.ProviderType
import org.parkjw.apps.oot.data.model.TokenStatus
import java.util.concurrent.TimeUnit

class TokenSyncRepository(
    private val preferences: AccountPreferences
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val openRouterApi = OpenRouterApi(client, json)
    private val xaiApi = XaiApi(client, json)
    private val zaiApi = ZaiApi(client, json)
    private val claudeApi = ClaudeApi(client)

    private val _statuses = MutableStateFlow<List<TokenStatus>>(emptyList())
    val statuses: StateFlow<List<TokenStatus>> = _statuses.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    suspend fun refreshAll(): List<TokenStatus> = coroutineScope {
        _isRefreshing.value = true
        val activeAccounts = preferences.accounts.value.filter { it.isEnabled }

        val deferreds = activeAccounts.map { account ->
            async {
                fetchAccountStatus(account)
            }
        }

        val results = deferreds.awaitAll()
        _statuses.value = results
        _isRefreshing.value = false
        results
    }

    private suspend fun fetchAccountStatus(account: ProviderAccount): TokenStatus {
        return when (account.provider) {
            ProviderType.OPENROUTER -> openRouterApi.fetchStatus(account)
            ProviderType.XAI -> xaiApi.fetchStatus(account)
            ProviderType.ZAI -> zaiApi.fetchStatus(account)
            ProviderType.CLAUDE -> claudeApi.fetchStatus(account)
        }
    }
}
