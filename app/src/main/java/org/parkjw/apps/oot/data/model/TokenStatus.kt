package org.parkjw.apps.oot.data.model

import kotlinx.serialization.Serializable

@Serializable
data class TokenStatus(
    val accountId: String,
    val provider: ProviderType,
    val accountName: String,
    val remainingFormatted: String,
    val totalLimitFormatted: String? = null,
    val usagePercent: Float? = null, // 0.0 ~ 100.0
    val resetTimeFormatted: String? = null,
    val statusSummary: String, // Short ticker for notification (e.g. "$12.45 남음")
    val isWarning: Boolean = false,
    val isCritical: Boolean = false,
    val isError: Boolean = false,
    val errorMessage: String? = null,
    val lastUpdated: Long = System.currentTimeMillis()
)
