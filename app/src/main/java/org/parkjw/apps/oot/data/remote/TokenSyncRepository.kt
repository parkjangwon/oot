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
import org.parkjw.apps.oot.data.model.AuthType
import org.parkjw.apps.oot.data.model.ProviderAccount
import org.parkjw.apps.oot.data.model.ProviderType
import org.parkjw.apps.oot.data.model.TokenStatus
import org.parkjw.apps.oot.data.oauth.OAuthManager
import java.util.concurrent.TimeUnit

class TokenSyncRepository(
    private val preferences: AccountPreferences,
    val oauthManager: OAuthManager,
    private val client: OkHttpClient,
    private val json: Json
) {
    private val openRouterApi = OpenRouterApi(client, json)
    private val xaiApi = XaiApi(client, json)
    private val zaiApi = ZaiApi(client, json)
    private val claudeApi = ClaudeApi(client, json)
    private val openAiApi = OpenAiApi(client, json)
    private val cursorApi = CursorApi(client, json)
    private val antigravityApi = AntigravityApi(client, json)

    private val _statuses = MutableStateFlow<List<TokenStatus>>(emptyList())
    val statuses: StateFlow<List<TokenStatus>> = _statuses.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    suspend fun refreshAll(): List<TokenStatus> = coroutineScope {
        _isRefreshing.value = true
        val activeAccounts = preferences.accounts.value.filter { it.isEnabled && it.provider.isSelectable }

        val deferreds = activeAccounts.map { account ->
            async {
                val updatedAccount = if (account.authType == AuthType.OAUTH && account.isTokenExpired) {
                    val refreshed = oauthManager.refreshTokenIfNeeded(account)
                    if (refreshed != account) {
                        preferences.saveAccount(refreshed)
                    }
                    refreshed
                } else {
                    account
                }
                fetchAccountStatus(updatedAccount)
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
            ProviderType.OPENAI -> openAiApi.fetchStatus(account)
            ProviderType.CURSOR -> cursorApi.fetchStatus(account)
            ProviderType.ANTIGRAVITY -> antigravityApi.fetchStatus(account)
        }
    }
}
