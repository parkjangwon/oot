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
                    val root = try { json.parseToJsonElement(body).jsonObject } catch (e: Exception) { null }
                    val data = root?.get("data")?.jsonObject ?: root

                    val limitsArray = data?.get("limits")?.jsonArray
                        ?: root?.get("limits")?.jsonArray

                    val level = data?.get("level")?.jsonPrimitive?.content ?: "GLM"

                    if (!limitsArray.isNullOrEmpty()) {
                        // unit: 3 = 5-hour rolling session limit
                        // unit: 6 = weekly quota limit
                        val fiveHourLimit = limitsArray.firstOrNull { elem ->
                            elem.jsonObject["unit"]?.jsonPrimitive?.intOrNull == 3
                        }?.jsonObject ?: limitsArray.firstOrNull()?.jsonObject

                        val weeklyLimit = limitsArray.firstOrNull { elem ->
                            elem.jsonObject["unit"]?.jsonPrimitive?.intOrNull == 6
                        }?.jsonObject ?: limitsArray.getOrNull(1)?.jsonObject

                        val fiveHourPct = fiveHourLimit?.get("percentage")?.jsonPrimitive?.intOrNull ?: 0
                        val fiveHourResetTime = fiveHourLimit?.get("nextResetTime")?.jsonPrimitive?.longOrNull
                            ?: fiveHourLimit?.get("expireTime")?.jsonPrimitive?.longOrNull

                        val weeklyPct = weeklyLimit?.get("percentage")?.jsonPrimitive?.intOrNull ?: 0
                        val weeklyResetTime = weeklyLimit?.get("nextResetTime")?.jsonPrimitive?.longOrNull
                            ?: weeklyLimit?.get("expireTime")?.jsonPrimitive?.longOrNull

                        val fiveHourRemaining = (100 - fiveHourPct).coerceIn(0, 100)
                        val weeklyRemaining = (100 - weeklyPct).coerceIn(0, 100)

                        val fiveHourResetFormatted = fiveHourResetTime?.let { formatRelativeTime(it) } ?: ""
                        val weeklyResetFormatted = weeklyResetTime?.let { formatRelativeTime(it) } ?: ""

                        // Main Text: Show BOTH 5-Hour and Weekly Remaining
                        val remainingText = "5시간: ${fiveHourRemaining}% 남음 | 주간: ${weeklyRemaining}% 남음"

                        // Subtitle: Usage percentages, reset timers, and plan level
                        val totalLimitText = buildString {
                            append("5h 사용: ${fiveHourPct}%")
                            if (fiveHourResetFormatted.isNotBlank()) append(" ($fiveHourResetFormatted)")
                            append(" | 주간 사용: ${weeklyPct}%")
                            if (weeklyResetFormatted.isNotBlank()) append(" ($weeklyResetFormatted)")
                            append(" [${level.uppercase()}]")
                        }

                        // Compact ticker for status bar notification
                        val tickerText = "5h: ${fiveHourRemaining}% | 주: ${weeklyRemaining}%"

                        val isWarn = fiveHourPct > 80 || weeklyPct > 80
                        val isCrit = fiveHourPct > 95 || weeklyPct > 95

                        return@withContext TokenStatus(
                            accountId = account.id,
                            provider = ProviderType.ZAI,
                            accountName = account.name,
                            remainingFormatted = remainingText,
                            totalLimitFormatted = totalLimitText,
                            usagePercent = fiveHourPct.toFloat(),
                            resetTimeFormatted = if (fiveHourResetFormatted.isNotBlank()) "5h $fiveHourResetFormatted" else null,
                            statusSummary = tickerText,
                            isWarning = isWarn,
                            isCritical = isCrit
                        )
                    }

                    // Fallback if limits array is empty but 200 OK
                    return@withContext TokenStatus(
                        accountId = account.id,
                        provider = ProviderType.ZAI,
                        accountName = account.name,
                        remainingFormatted = "Z.ai 코딩 플랜 ($level) 활성",
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
