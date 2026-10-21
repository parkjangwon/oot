package org.parkjw.apps.oot.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.parkjw.apps.oot.data.model.ProviderAccount
import org.parkjw.apps.oot.data.model.ProviderType
import org.parkjw.apps.oot.data.model.TokenStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class ZaiApi(
    private val client: OkHttpClient,
    private val json: Json
) {
    suspend fun fetchStatus(account: ProviderAccount): TokenStatus = withContext(Dispatchers.IO) {
        val credential = account.activeCredential().trim().removePrefix("Bearer ").trim()
        if (credential.isBlank()) {
            return@withContext TokenStatus(
                accountId = account.id,
                provider = ProviderType.ZAI,
                accountName = account.name,
                remainingFormatted = "API 키 없음",
                statusSummary = "키 필요",
                isError = true,
                errorMessage = "Z.ai 코딩 플랜 API 키를 등록해주세요."
            )
        }

        val baseUrl = account.customEndpoint?.takeIf { it.isNotBlank() } ?: ProviderType.ZAI.baseUrl
        // Official Z.ai GLM Coding Plan monitoring endpoint
        val quotaLimitUrl = "$baseUrl/api/monitor/usage/quota/limit"
        val authHeader = "Bearer $credential"

        try {
            val request = Request.Builder()
                .url(quotaLimitUrl)
                .addHeader("Authorization", authHeader)
                .addHeader("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()

                if (response.isSuccessful) {
                    val usage = parseZaiUsage(body, json)
                    if (usage.windows.isNotEmpty()) {
                        return@withContext successStatus(account, usage)
                    }

                    // Fallback if no 5-hour/weekly limit is reported but 200 OK
                    return@withContext TokenStatus(
                        accountId = account.id,
                        provider = ProviderType.ZAI,
                        accountName = account.name,
                        remainingFormatted = "Z.ai 코딩 플랜 (${usage.level}) 활성",
                        totalLimitFormatted = "정상 작동 중",
                        statusSummary = "Z.ai 정상",
                        isWarning = false
                    )
                }

                if (response.code == 401 || response.code == 403) {
                    return@withContext TokenStatus(
                        accountId = account.id,
                        provider = ProviderType.ZAI,
                        accountName = account.name,
                        remainingFormatted = "인증 실패 (${response.code})",
                        statusSummary = "Z.ai 인증 실패",
                        isError = true,
                        errorMessage = "Z.ai 코딩 플랜 API 키를 확인해주세요."
                    )
                }

                TokenStatus(
                    accountId = account.id,
                    provider = ProviderType.ZAI,
                    accountName = account.name,
                    remainingFormatted = "HTTP ${response.code}",
                    statusSummary = "오류 (${response.code})",
                    isError = true,
                    errorMessage = "Z.ai 서버 응답 오류 (${response.code})"
                )
            }
        } catch (e: Exception) {
            TokenStatus(
                accountId = account.id,
                provider = ProviderType.ZAI,
                accountName = account.name,
                remainingFormatted = "네트워크 오류",
                statusSummary = "연결 실패",
                isError = true,
                errorMessage = e.localizedMessage ?: "연결 실패"
            )
        }
    }

    private fun successStatus(account: ProviderAccount, usage: ZaiUsage): TokenStatus {
        val windows = usage.windows
        val first = windows.first()
        val maxUsed = windows.maxOf { it.percentUsed }
        val limitText = windows.joinToString(" | ") { w ->
            val reset = w.resetsAt?.let { formatRelativeTime(it.toEpochMilli()) }
            "${w.label} 사용: ${w.percentUsed.roundToInt()}%" + (reset?.let { " ($it)" } ?: "")
        } + " [${usage.level.uppercase()}]"

        return TokenStatus(
            accountId = account.id,
            provider = ProviderType.ZAI,
            accountName = account.name,
            remainingFormatted = windows.joinToString(" | ") { "${it.label}: ${it.percentLeft.roundToInt()}% 남음" },
            totalLimitFormatted = limitText,
            usagePercent = first.percentUsed.toFloat(),
            resetTimeFormatted = first.resetsAt?.let { "${first.label} ${formatRelativeTime(it.toEpochMilli())}" },
            statusSummary = windows.joinToString(" | ") { "${it.label} ${it.percentLeft.roundToInt()}%" },
            isWarning = maxUsed > 80,
            isCritical = maxUsed > 95,
            windows = windows.map { it.toUsageWindow() }
        )
    }

    private fun formatRelativeTime(timestampMs: Long): String {
        val now = System.currentTimeMillis()
        val diffMs = timestampMs - now
        return if (diffMs > 0) {
            val totalMinutes = diffMs / (1000 * 60)
            val hours = totalMinutes / 60
            val minutes = totalMinutes % 60
            val days = hours / 24

            when {
                days > 0 -> "${days}일 ${hours % 24}시간 후 리셋"
                hours > 0 -> "${hours}시간 ${minutes}분 후 리셋"
                else -> "${minutes}분 후 리셋"
            }
        } else {
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            "리셋됨 (${sdf.format(Date(timestampMs))})"
        }
    }
}
