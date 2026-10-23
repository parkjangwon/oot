package org.parkjw.apps.oot.data.oauth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.parkjw.apps.oot.data.model.ProviderAccount

/**
 * Deletes an account from the device first, then asks the provider to revoke it in the background.
 * The local delete never waits for, or depends on, the revoke call.
 */
class AccountDeletion(
    private val deleteLocal: (accountId: String) -> Unit,
    private val revoke: suspend (ProviderAccount) -> RevokeResult,
    private val scope: CoroutineScope,
) {
    fun delete(account: ProviderAccount): Job {
        deleteLocal(account.id)
        return scope.launch {
            try {
                revoke(account)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                // best effort: the account is already gone from the device
            }
        }
    }
}
