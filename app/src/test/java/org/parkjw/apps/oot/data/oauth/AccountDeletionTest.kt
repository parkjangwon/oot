package org.parkjw.apps.oot.data.oauth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.parkjw.apps.oot.data.model.AuthType
import org.parkjw.apps.oot.data.model.ProviderAccount
import org.parkjw.apps.oot.data.model.ProviderType

class AccountDeletionTest {
    private val stored = mutableListOf<ProviderAccount>()
    private val events = mutableListOf<String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private fun account(provider: ProviderType, auth: AuthType = AuthType.OAUTH) = ProviderAccount(
        id = "id-${provider.name}-$auth", provider = provider, name = provider.displayName, authType = auth,
        apiKey = if (auth == AuthType.API_KEY) "sk-test-000" else "", accessToken = "at-test-000", refreshToken = "rt-test-000",
    ).also { stored += it }

    private fun deletion(revoke: suspend (ProviderAccount) -> RevokeResult) = AccountDeletion(
        deleteLocal = { id -> events += "delete"; stored.removeAll { it.id == id } },
        revoke = { events += "revoke"; revoke(it) },
        scope = scope,
    )

    private fun deleteWith(post: (String, Map<String, String>) -> Int, a: ProviderAccount) = runBlocking {
        deletion(TokenRevoker(post)::revoke).delete(a).join()
    }

    @Test
    fun serverRevokeSuccessDeletesLocallyFirst() {
        for (p in listOf(ProviderType.OPENAI, ProviderType.XAI)) {
            events.clear()
            val a = account(p)
            deleteWith({ _, _ -> 200 }, a)
            assertFalse(stored.contains(a))
            assertEquals(listOf("delete", "revoke"), events)
        }
    }

    @Test
    fun serverRevokeFailureStillCompletesDelete() {
        for (p in listOf(ProviderType.OPENAI, ProviderType.XAI)) {
            val a = account(p)
            deleteWith({ _, _ -> 500 }, a)
            assertFalse(stored.contains(a))
            val b = account(p)
            deleteWith({ _, _ -> throw java.io.IOException("offline") }, b)
            assertFalse(stored.contains(b))
        }
        assertTrue(stored.isEmpty())
    }

    @Test
    fun revokerThatThrowsDoesNotUndoDeleteOrCrashScope() = runBlocking {
        val a = account(ProviderType.XAI)
        deletion { error("boom") }.delete(a).join()
        assertFalse(stored.contains(a))
        // the scope is still usable for the next deletion
        val b = account(ProviderType.OPENAI)
        deletion { RevokeResult.REVOKED }.delete(b).join()
        assertFalse(stored.contains(b))
    }

    @Test
    fun unsupportedProvidersAreDeletedWithoutServerCall() {
        var called = false
        val all = listOf(
            account(ProviderType.CLAUDE),
            account(ProviderType.CLAUDE, AuthType.API_KEY),
            account(ProviderType.OPENAI, AuthType.API_KEY),
            account(ProviderType.XAI, AuthType.API_KEY),
            account(ProviderType.OPENROUTER, AuthType.API_KEY),
            account(ProviderType.DEEPSEEK, AuthType.API_KEY),
            account(ProviderType.ZAI, AuthType.API_KEY),
        )
        all.forEach { deleteWith({ _, _ -> called = true; 200 }, it) }
        assertTrue(stored.isEmpty())
        assertFalse(called)
    }
}
