package org.parkjw.apps.oot.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.parkjw.apps.oot.data.model.AuthType
import org.parkjw.apps.oot.data.model.ProviderAccount
import org.parkjw.apps.oot.data.model.ProviderType
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AccountPreferencesReauthTest {

    private fun preferences() = testAccountPreferences(RuntimeEnvironment.getApplication())

    private fun account(
        id: String,
        provider: ProviderType = ProviderType.CLAUDE,
        authType: AuthType = AuthType.OAUTH,
        apiKey: String = "",
        accessToken: String? = "initial_access_token",
        refreshToken: String? = "initial_refresh_token",
        tokenExpiresAt: Long? = 1000L,
        name: String = "Name $id",
        isEnabled: Boolean = true,
        customEndpoint: String? = "https://custom.endpoint/$id",
        createdAt: Long = 123456789L,
        accountEmail: String? = "$id@example.com",
        chatgptAccountId: String? = "chatgpt-$id",
    ) = ProviderAccount(
        id = id,
        provider = provider,
        name = name,
        authType = authType,
        apiKey = apiKey,
        accessToken = accessToken,
        refreshToken = refreshToken,
        tokenExpiresAt = tokenExpiresAt,
        accountEmail = accountEmail,
        chatgptAccountId = chatgptAccountId,
        customEndpoint = customEndpoint,
        isEnabled = isEnabled,
        createdAt = createdAt,
    )

    @Test
    fun reauthenticatingOAuthAccountKeepsIdOrderNameEnabledEndpointAndNotificationsWhileUpdatingTokens() {
        val prefs = preferences()
        val accountA = account("a")
        val accountB = account("b")
        val accountC = account("c")

        prefs.saveAccount(accountA)
        prefs.saveAccount(accountB)
        prefs.saveAccount(accountC)

        // Custom order [c, a, b]
        prefs.setAccountOrder(listOf("c", "a", "b"))
        assertEquals(listOf("c", "a", "b"), prefs.accounts.value.map { it.id })

        // Hidden notification on 'a'
        prefs.setShownInNotification("a", false)
        assertEquals(setOf("a"), prefs.notificationHiddenAccountIds.value)

        // In-place reauth on account 'a' with new tokens
        val updatedTokens = accountA.copy(
            accessToken = "new_access_token",
            refreshToken = "new_refresh_token",
            tokenExpiresAt = 9999L,
            accountEmail = "new_email@example.com",
            chatgptAccountId = "new_chatgpt_id",
        )
        val success = prefs.updateAccountIfPresent(updatedTokens)
        assertTrue(success)

        // Verify in-memory state
        val accounts = prefs.accounts.value
        assertEquals(listOf("c", "a", "b"), accounts.map { it.id })
        assertEquals(setOf("a"), prefs.notificationHiddenAccountIds.value)

        val updatedA = accounts.first { it.id == "a" }
        assertEquals("Name a", updatedA.name)
        assertEquals(true, updatedA.isEnabled)
        assertEquals("https://custom.endpoint/a", updatedA.customEndpoint)
        assertEquals(123456789L, updatedA.createdAt)
        assertEquals("new_access_token", updatedA.accessToken)
        assertEquals("new_refresh_token", updatedA.refreshToken)
        assertEquals(9999L, updatedA.tokenExpiresAt)
        assertEquals("new_email@example.com", updatedA.accountEmail)
        assertEquals("new_chatgpt_id", updatedA.chatgptAccountId)

        // Verify persistence across restart
        val restarted = preferences()
        assertEquals(listOf("c", "a", "b"), restarted.accounts.value.map { it.id })
        assertEquals(setOf("a"), restarted.notificationHiddenAccountIds.value)

        val persistedA = restarted.accounts.value.first { it.id == "a" }
        assertEquals("Name a", persistedA.name)
        assertEquals(true, persistedA.isEnabled)
        assertEquals("https://custom.endpoint/a", persistedA.customEndpoint)
        assertEquals(123456789L, persistedA.createdAt)
        assertEquals("new_access_token", persistedA.accessToken)
        assertEquals("new_refresh_token", persistedA.refreshToken)
        assertEquals(9999L, persistedA.tokenExpiresAt)
        assertEquals("new_email@example.com", persistedA.accountEmail)
        assertEquals("new_chatgpt_id", persistedA.chatgptAccountId)
    }

    @Test
    fun reauthenticatingApiKeyAccountKeepsOrderNameEnabledAndReplacesOnlyApiKey() {
        val prefs = preferences()
        val accountA = account("a", provider = ProviderType.OPENROUTER, authType = AuthType.API_KEY, apiKey = "old-key")
        val accountB = account("b", provider = ProviderType.OPENROUTER, authType = AuthType.API_KEY, apiKey = "key-b")
        val accountC = account("c", provider = ProviderType.OPENROUTER, authType = AuthType.API_KEY, apiKey = "key-c")

        prefs.saveAccount(accountA)
        prefs.saveAccount(accountB)
        prefs.saveAccount(accountC)
        prefs.setAccountOrder(listOf("c", "a", "b"))
        prefs.setShownInNotification("a", false)

        val success = prefs.updateApiKeyIfPresent("a", "new-secret-api-key")
        assertTrue(success)

        val accounts = prefs.accounts.value
        assertEquals(listOf("c", "a", "b"), accounts.map { it.id })
        assertEquals(setOf("a"), prefs.notificationHiddenAccountIds.value)

        val updatedA = accounts.first { it.id == "a" }
        assertEquals("new-secret-api-key", updatedA.apiKey)
        assertEquals("Name a", updatedA.name)
        assertEquals(true, updatedA.isEnabled)
        assertEquals("https://custom.endpoint/a", updatedA.customEndpoint)
        assertEquals("initial_access_token", updatedA.accessToken)
        assertEquals("initial_refresh_token", updatedA.refreshToken)
        assertEquals(1000L, updatedA.tokenExpiresAt)

        // Survives restart
        val restarted = preferences()
        assertEquals(listOf("c", "a", "b"), restarted.accounts.value.map { it.id })
        assertEquals(setOf("a"), restarted.notificationHiddenAccountIds.value)
        assertEquals("new-secret-api-key", restarted.accounts.value.first { it.id == "a" }.apiKey)
    }

    @Test
    fun failedOrNonExistentAccountReauthDoesNotModifyAccountsOrWriteChanges() {
        val prefs = preferences()
        val accountA = account("a")
        prefs.saveAccount(accountA)

        val nonExistent = account("non-existent").copy(accessToken = "intruder")
        val tokenUpdateFailed = prefs.updateAccountIfPresent(nonExistent)
        assertFalse(tokenUpdateFailed)

        val apiKeyUpdateFailed = prefs.updateApiKeyIfPresent("non-existent", "intruder-key")
        assertFalse(apiKeyUpdateFailed)

        // Existing accounts are completely identical
        assertEquals(listOf(accountA), prefs.accounts.value)
        assertEquals(listOf(accountA), preferences().accounts.value)
    }

    @Test
    fun reauthenticatingOAuthAccountWithNullRefreshTokenPreservesExistingRefreshToken() {
        val prefs = preferences()
        val accountA = account("a", refreshToken = "initial_refresh_token")
        prefs.saveAccount(accountA)

        // When token exchange response has null refresh_token, existing refresh_token must be preserved
        val tokenResponseRefreshToken: String? = null
        val updatedTokens = accountA.copy(
            accessToken = "new_access_token",
            refreshToken = tokenResponseRefreshToken ?: accountA.refreshToken,
            tokenExpiresAt = 9999L,
        )
        val success = prefs.updateAccountIfPresent(updatedTokens)
        assertTrue(success)

        val updatedA = prefs.accounts.value.first { it.id == "a" }
        assertEquals("new_access_token", updatedA.accessToken)
        assertEquals("initial_refresh_token", updatedA.refreshToken)

        // Also test defensive fallback in updateAccountIfPresent when account itself has null refreshToken
        val accountWithNullRefresh = updatedA.copy(
            accessToken = "brand_new_access_token",
            refreshToken = null,
        )
        val successDefensive = prefs.updateAccountIfPresent(accountWithNullRefresh)
        assertTrue(successDefensive)

        val updatedADefensive = prefs.accounts.value.first { it.id == "a" }
        assertEquals("brand_new_access_token", updatedADefensive.accessToken)
        assertEquals("initial_refresh_token", updatedADefensive.refreshToken)
    }

    @Test
    fun delayedPeriodicRefreshWithOutdatedGenerationIsRejected() {
        val prefs = preferences()
        val accountA = account("a", accessToken = "token_v0", refreshToken = "refresh_v0")
        prefs.saveAccount(accountA)

        val initialGeneration = prefs.tokenGeneration("a")
        assertEquals(0, initialGeneration)

        // User reauthenticates, bumping generation and saving new tokens
        prefs.bumpTokenGeneration("a")
        val reauthTokens = accountA.copy(accessToken = "token_v1_reauth", refreshToken = "refresh_v1_reauth")
        assertTrue(prefs.updateAccountIfPresent(reauthTokens))

        assertEquals("token_v1_reauth", prefs.accounts.value.first { it.id == "a" }.accessToken)
        assertEquals("refresh_v1_reauth", prefs.accounts.value.first { it.id == "a" }.refreshToken)

        // Delayed periodic refresh completes with old expectedGeneration
        val delayedRefreshed = accountA.copy(accessToken = "token_v0_late_refreshed", refreshToken = "refresh_v0_late")
        val lateUpdateSuccess = prefs.updateAccountIfPresent(delayedRefreshed, expectedGeneration = initialGeneration)
        assertFalse(lateUpdateSuccess)

        // Accounts still retain reauthenticated tokens, not the late refresh tokens
        val currentA = prefs.accounts.value.first { it.id == "a" }
        assertEquals("token_v1_reauth", currentA.accessToken)
        assertEquals("refresh_v1_reauth", currentA.refreshToken)
    }
}
