package org.parkjw.apps.oot.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ProviderAccount(
    val id: String,
    val provider: ProviderType,
    val name: String,
    val authType: AuthType = provider.primaryAuthType,
    val apiKey: String = "",
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val tokenExpiresAt: Long? = null,
    val accountEmail: String? = null,
    val customEndpoint: String? = null,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun activeCredential(): String {
        return when (authType) {
            AuthType.OAUTH -> accessToken?.takeIf { it.isNotBlank() } ?: apiKey
            AuthType.API_KEY -> apiKey
        }
    }

    val isTokenExpired: Boolean
        get() = tokenExpiresAt != null && System.currentTimeMillis() >= tokenExpiresAt
}
