package org.parkjw.apps.oot.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ProviderAccount(
    val id: String,
    val provider: ProviderType,
    val name: String,
    val apiKey: String,
    val customEndpoint: String? = null,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
