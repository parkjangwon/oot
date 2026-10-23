package org.parkjw.apps.oot.data.remote

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.parkjw.apps.oot.R
import org.parkjw.apps.oot.data.local.AccountPreferences
import org.parkjw.apps.oot.data.model.AuthType
import org.parkjw.apps.oot.data.model.ProviderAccount
import org.parkjw.apps.oot.data.model.ProviderType
import org.parkjw.apps.oot.data.model.TokenStatus
import org.parkjw.apps.oot.data.model.VendorUsage
import org.parkjw.apps.oot.data.model.asStale
import org.parkjw.apps.oot.data.model.toVendorUsage
import org.parkjw.apps.oot.data.oauth.OAuthManager
import org.parkjw.apps.oot.i18n.AppText

class TokenSyncRepository(
    private val preferences: AccountPreferences,
    val oauthManager: OAuthManager,
    private val client: OkHttpClient,
    private val json: Json,
    private val refreshScheduler: RefreshScheduler = RefreshScheduler(),
) {
    private val openRouterApi = OpenRouterApi(client, json)
    private val xaiApi = XaiApi(client, json)
    private val zaiApi = ZaiApi(client, json)
    private val claudeApi = ClaudeApi(client, json, oauthManager) { preferences.updateAccountIfPresent(it) }
    private val openAiApi = OpenAiApi(client, json, oauthManager) { preferences.updateAccountIfPresent(it) }
    private val deepSeekApi = DeepSeekApi(client, json)
    private val ollamaCloudApi = OllamaCloudApi(client, json)
    private val openCodeGoApi = OpenCodeGoApi(client, json)

    private val _statuses = MutableStateFlow<List<TokenStatus>>(emptyList())
    val statuses: StateFlow<List<TokenStatus>> = _statuses.asStateFlow()

    private val _vendorUsages = MutableStateFlow<List<VendorUsage>>(emptyList())
    val vendorUsages: StateFlow<List<VendorUsage>> = _vendorUsages.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /**
     * Drop cached usage/backoff. Refreshes that started before a secure-storage reset are ignored by comparing
     * [AccountPreferences.resetGeneration], which the storage reset bumps.
     */
    @Synchronized
    fun onAccountsReset() {
        refreshScheduler.clear()
        _statuses.value = emptyList()
        _vendorUsages.value = emptyList()
        _isRefreshing.value = false
    }

    /** Save the user's drag order and reorder cached usages to match (no network). */
    fun applyAccountOrder(accountIds: List<String>) {
        preferences.setAccountOrder(accountIds)
        val usageById = _vendorUsages.value.associateBy { it.accountId }
        val statusById = _statuses.value.associateBy { it.accountId }
        val orderedIds = AccountPreferences.mergeAccountOrder(
            accountIds,
            _vendorUsages.value.map { it.accountId },
        )
        _vendorUsages.value = orderedIds.mapNotNull { usageById[it] }
        _statuses.value = orderedIds.mapNotNull { statusById[it] }
    }

    suspend fun refreshAll(): List<TokenStatus> = coroutineScope {
        // Read before the accounts: a reset after this point makes every check below fail.
        val startGeneration = preferences.resetGeneration()
        fun stale() = startGeneration != preferences.resetGeneration()
        _isRefreshing.value = true
        val activeAccounts = preferences.accounts.value.filter { it.isEnabled && it.provider.isSelectable }
        val intervalMs = preferences.refreshIntervalMinutes.value.coerceAtLeast(1) * 60_000L
        val previous = _statuses.value.associateBy { it.accountId }

        val deferreds = activeAccounts.map { account ->
            async {
                // A reset after this refresh started removed every account: send no request and write nothing.
                if (stale()) return@async null
                if (!refreshScheduler.shouldFetch(account.id)) {
                    // Backoff: the previous values are shown again, so they must be marked stale.
                    return@async previous[account.id]?.asStale() ?: skippedBackoffStatus(account)
                }
                val updatedAccount = if (account.authType == AuthType.OAUTH && account.isTokenExpired) {
                    val refreshed = oauthManager.refreshTokenIfNeeded(account)
                    if (stale()) return@async null
                    // Removed while the token refresh was in flight: no usage call with the rotated token.
                    if (refreshed != account && !preferences.updateAccountIfPresent(refreshed)) {
                        return@async null
                    }
                    refreshed
                } else {
                    account
                }
                if (stale()) return@async null
                val status = fetchAccountStatus(updatedAccount)
                if (stale()) return@async null
                if (status.isRateLimited) {
                    refreshScheduler.onRateLimited(
                        account.id,
                        intervalMs,
                        status.rateLimitedRetryAfterMs,
                    )
                } else if (!status.isError) {
                    refreshScheduler.onSuccess(account.id)
                }
                status
            }
        }

        val results = deferreds.awaitAll().filterNotNull()
        // The reset already cleared the state: do not overwrite it (and leave _isRefreshing as onAccountsReset set it).
        if (stale()) return@coroutineScope emptyList()
        // Order by what is saved now, not at the start: the user may have dragged while requests were in flight.
        val byId = results.associateBy { it.accountId }
        val ordered = preferences.accounts.value.mapNotNull { byId[it.id] }
        _statuses.value = ordered
        _vendorUsages.value = ordered.map { it.toVendorUsage() }
        _isRefreshing.value = false
        ordered
    }

    private fun skippedBackoffStatus(account: ProviderAccount): TokenStatus = TokenStatus(
        accountId = account.id,
        provider = account.provider,
        accountName = account.name,
        remainingFormatted = AppText.get(R.string.data_rate_limit_waiting),
        statusSummary = AppText.get(R.string.status_needs_check),
        isError = true,
        errorMessage = AppText.get(R.string.data_rate_limit_backoff),
        isRateLimited = true,
    )

    private suspend fun fetchAccountStatus(account: ProviderAccount): TokenStatus {
        return when (account.provider) {
            ProviderType.OPENROUTER -> openRouterApi.fetchStatus(account)
            ProviderType.XAI -> xaiApi.fetchStatus(account)
            ProviderType.ZAI -> zaiApi.fetchStatus(account)
            ProviderType.CLAUDE -> claudeApi.fetchStatus(account)
            ProviderType.OPENAI -> openAiApi.fetchStatus(account)
            ProviderType.DEEPSEEK -> deepSeekApi.fetchStatus(account)
            ProviderType.OLLAMA_CLOUD -> ollamaCloudApi.fetchStatus(account)
            ProviderType.OPENCODE_GO -> openCodeGoApi.fetchStatus(account)
        }
    }
}
