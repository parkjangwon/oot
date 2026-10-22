package org.parkjw.apps.oot.data.oauth

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.parkjw.apps.oot.data.model.AuthType
import org.parkjw.apps.oot.data.model.ProviderAccount
import org.parkjw.apps.oot.data.model.ProviderType

class TokenRevokerTest {
    private val calls = mutableListOf<Pair<String, Map<String, String>>>()
    private val endpoint = "https://auth.example.com/revoke"

    private fun oauth(provider: ProviderType = ProviderType.XAI, refresh: String? = "rt-test-000") =
        ProviderAccount(
            id = "a", provider = provider, name = "demo@example.com", authType = AuthType.OAUTH,
            accessToken = "at-test-000", refreshToken = refresh,
        )

    private fun revoker(
        post: (String, Map<String, String>) -> Unit = { u, f -> calls += u to f },
        timeoutMs: Long = 1_000,
    ) = TokenRevoker(post, endpointFor = { if (it == ProviderType.XAI) endpoint else null }, timeoutMs = timeoutMs)

    @Test
    fun supportedProviderCallsEndpointWithRefreshToken() = runBlocking {
        revoker().revoke(oauth())
        assertEquals(endpoint, calls.single().first)
        assertEquals("rt-test-000", calls.single().second["token"])
        assertEquals("refresh_token", calls.single().second["token_type_hint"])
    }

    @Test
    fun fallsBackToAccessToken() = runBlocking {
        revoker().revoke(oauth(refresh = null))
        assertEquals("at-test-000", calls.single().second["token"])
    }

    @Test
    fun unsupportedProviderMakesNoCall() = runBlocking {
        revoker().revoke(oauth(ProviderType.CLAUDE))
        assertTrue(calls.isEmpty())
    }

    @Test
    fun apiKeyAccountMakesNoCall() = runBlocking {
        revoker().revoke(oauth().copy(authType = AuthType.API_KEY, apiKey = "sk-test-000"))
        assertTrue(calls.isEmpty())
    }

    @Test
    fun failureDoesNotPropagate() = runBlocking {
        revoker(post = { _, _ -> error("network down") }).revoke(oauth())
    }

    @Test
    fun timeoutDoesNotPropagate() = runBlocking {
        revoker(post = { _, _ -> Thread.sleep(500) }, timeoutMs = 50).revoke(oauth())
    }

    @Test
    fun onlyDocumentedProvidersHaveRevocationEndpoint() {
        // Only providers with an officially documented endpoint may be listed; see docs/token-revocation.md.
        val enabled = ProviderType.values().filter { it.oauthRevocationEndpoint != null }.toSet()
        assertEquals(setOf(ProviderType.OPENAI, ProviderType.XAI), enabled)
        assertTrue(enabled.all { it.oauthRevocationEndpoint!!.startsWith("https://") })
    }
}
