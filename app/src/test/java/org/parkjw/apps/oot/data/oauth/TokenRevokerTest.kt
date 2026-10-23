package org.parkjw.apps.oot.data.oauth

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.parkjw.apps.oot.data.model.AuthType
import org.parkjw.apps.oot.data.model.ProviderAccount
import org.parkjw.apps.oot.data.model.ProviderType

class TokenRevokerTest {
    private val calls = mutableListOf<Pair<String, Map<String, String>>>()

    private fun oauth(provider: ProviderType, refresh: String? = "rt-test-000") =
        ProviderAccount(
            id = "a", provider = provider, name = "demo@example.com", authType = AuthType.OAUTH,
            accessToken = "at-test-000", refreshToken = refresh,
        )

    private fun apiKey(provider: ProviderType) =
        ProviderAccount(id = "k", provider = provider, name = provider.displayName, authType = AuthType.API_KEY, apiKey = "sk-test-000")

    private fun revoker(status: Int = 200, timeoutMs: Long = 1_000) =
        TokenRevoker({ u, f -> calls += u to f; status }, clientIdFor = { "client-${it.name}" }, timeoutMs = timeoutMs)

    // --- server revoke providers: success / failure ---

    @Test
    fun openAiSuccessRevokesRefreshTokenAtDocumentedEndpoint() = runBlocking {
        assertEquals(RevokeResult.REVOKED, revoker().revoke(oauth(ProviderType.OPENAI)))
        val (url, form) = calls.single()
        assertEquals("https://auth.openai.com/api/accounts/oauth/revoke", url)
        assertEquals("rt-test-000", form["token"])
        assertEquals("refresh_token", form["token_type_hint"])
        assertEquals("client-OPENAI", form["client_id"])
    }

    @Test
    fun xaiSuccessRevokesRefreshTokenAtDocumentedEndpoint() = runBlocking {
        assertEquals(RevokeResult.REVOKED, revoker().revoke(oauth(ProviderType.XAI)))
        assertEquals("https://auth.x.ai/oauth2/revoke", calls.single().first)
        assertEquals("rt-test-000", calls.single().second["token"])
    }

    @Test
    fun fallsBackToAccessTokenWithoutRefreshToken() = runBlocking {
        assertEquals(RevokeResult.REVOKED, revoker().revoke(oauth(ProviderType.XAI, refresh = null)))
        assertEquals("at-test-000", calls.single().second["token"])
        assertEquals("access_token", calls.single().second["token_type_hint"])
    }

    @Test
    fun openAiServerErrorIsFailure() = runBlocking {
        assertEquals(RevokeResult.FAILED, revoker(status = 503).revoke(oauth(ProviderType.OPENAI)))
    }

    @Test
    fun xaiServerErrorIsFailure() = runBlocking {
        assertEquals(RevokeResult.FAILED, revoker(status = 400).revoke(oauth(ProviderType.XAI)))
    }

    @Test
    fun networkErrorIsFailureAndDoesNotThrow() = runBlocking {
        for (provider in listOf(ProviderType.OPENAI, ProviderType.XAI)) {
            val r = TokenRevoker({ _, _ -> error("network down") }).revoke(oauth(provider))
            assertEquals(RevokeResult.FAILED, r)
        }
    }

    @Test
    fun timeoutIsFailureAndDoesNotThrow() = runBlocking {
        val r = TokenRevoker({ _, _ -> Thread.sleep(500); 200 }, timeoutMs = 50).revoke(oauth(ProviderType.XAI))
        assertEquals(RevokeResult.FAILED, r)
    }

    // --- unsupported: device-only and not-covered providers make no call ---

    @Test
    fun claudeOAuthIsUnsupportedAndMakesNoCall() = runBlocking {
        assertEquals(RevokeResult.UNSUPPORTED, revoker().revoke(oauth(ProviderType.CLAUDE)))
        assertTrue(calls.isEmpty())
    }

    @Test
    fun apiKeyAccountsAreUnsupportedForEveryProvider() = runBlocking {
        for (provider in ProviderType.entries) {
            assertEquals(provider.name, RevokeResult.UNSUPPORTED, revoker().revoke(apiKey(provider)))
        }
        assertTrue(calls.isEmpty())
    }

    @Test
    fun oauthWithoutTokensIsUnsupported() = runBlocking {
        val empty = oauth(ProviderType.OPENAI, refresh = null).copy(accessToken = null)
        assertEquals(RevokeResult.UNSUPPORTED, revoker().revoke(empty))
        assertTrue(calls.isEmpty())
    }

    // --- coverage ---

    @Test
    fun revocationModePerProvider() {
        assertEquals(RevocationMode.SERVER, oauth(ProviderType.OPENAI).revocationMode())
        assertEquals(RevocationMode.SERVER, oauth(ProviderType.XAI).revocationMode())
        assertEquals(RevocationMode.DEVICE_ONLY, oauth(ProviderType.CLAUDE).revocationMode())
        for (p in listOf(ProviderType.CLAUDE, ProviderType.OPENAI, ProviderType.XAI, ProviderType.OPENROUTER, ProviderType.DEEPSEEK)) {
            assertEquals(p.name, RevocationMode.DEVICE_ONLY, apiKey(p).revocationMode())
        }
        for (p in listOf(ProviderType.ZAI, ProviderType.OLLAMA_CLOUD, ProviderType.OPENCODE_GO)) {
            assertNull(p.name, apiKey(p).revocationMode())
        }
    }

    @Test
    fun onlyDocumentedProvidersHaveRevocationEndpoint() {
        // Only endpoints published by the provider (OIDC metadata revocation_endpoint); see docs/token-revocation.md.
        val enabled = ProviderType.entries.filter { it.oauthRevocationEndpoint != null }.toSet()
        assertEquals(setOf(ProviderType.OPENAI, ProviderType.XAI), enabled)
        assertTrue(enabled.all { it.oauthRevocationEndpoint!!.startsWith("https://") })
    }
}
