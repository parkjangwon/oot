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
import org.parkjw.apps.oot.data.model.VendorUsage
import org.parkjw.apps.oot.data.model.toVendorUsage
import org.parkjw.apps.oot.data.oauth.OAuthManager

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
    private val claudeApi = ClaudeApi(client, json, oauthManager) { preferences.saveAccount(it) }
    private val openAiApi = OpenAiApi(client, json, oauthManager) { preferences.saveAccount(it) }
    private val antigravityApi = AntigravityApi(client, json, oauthManager) { preferences.saveAccount(it) }
    private val deepSeekApi = DeepSeekApi(client, json)
    private val ollamaCloudApi = OllamaCloudApi(client, json)
    private val openCodeGoApi = OpenCodeGoApi(client, json)

    private val _statuses = MutableStateFlow<List<TokenStatus>>(emptyList())
    val statuses: StateFlow<List<TokenStatus>> = _statuses.asStateFlow()

    private val _vendorUsages = MutableStateFlow<List<VendorUsage>>(emptyList())
    val vendorUsages: StateFlow<List<VendorUsage>> = _vendorUsages.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

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
        _isRefreshing.value = true
        val activeAccounts = preferences.accounts.value.filter { it.isEnabled && it.provider.isSelectable }
        val intervalMs = preferences.refreshIntervalMinutes.value.coerceAtLeast(1) * 60_000L
        val previous = _statuses.value.associateBy { it.accountId }

        val deferreds = activeAccounts.map { account ->
            async {
                if (!refreshScheduler.shouldFetch(account.id)) {
                    return@async previous[account.id] ?: skippedBackoffStatus(account)
                }
                val updatedAccount = if (account.authType == AuthType.OAUTH && account.isTokenExpired) {
                    val refreshed = oauthManager.refreshTokenIfNeeded(account)
                    if (refreshed != account) {
                        preferences.saveAccount(refreshed)
                    }
                    refreshed
                } else {
                    account
                }
                val status = fetchAccountStatus(updatedAccount)
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

        val results = deferreds.awaitAll()
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
        remainingFormatted = "요청 제한 (대기)",
        statusSummary = "확인 필요",
        isError = true,
        errorMessage = "조회 제한 대기 중",
        isRateLimited = true,
    )

    private suspend fun fetchAccountStatus(account: ProviderAccount): TokenStatus {
        return when (account.provider) {
            ProviderType.OPENROUTER -> openRouterApi.fetchStatus(account)
            ProviderType.XAI -> xaiApi.fetchStatus(account)
            ProviderType.ZAI -> zaiApi.fetchStatus(account)
            ProviderType.CLAUDE -> claudeApi.fetchStatus(account)
            ProviderType.OPENAI -> openAiApi.fetchStatus(account)
            ProviderType.ANTIGRAVITY -> antigravityApi.fetchStatus(account)
            ProviderType.DEEPSEEK -> deepSeekApi.fetchStatus(account)
            ProviderType.OLLAMA_CLOUD -> ollamaCloudApi.fetchStatus(account)
            ProviderType.OPENCODE_GO -> openCodeGoApi.fetchStatus(account)
        }
    }
}
