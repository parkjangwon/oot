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

                    if (!limitsArray.isNullOrEmpty()) {
                        // Find the most relevant limit: CREDIT_LIMIT or TOKENS_LIMIT or first entry
                        val primaryLimit = limitsArray.firstOrNull { elem ->
                            val type = elem.jsonObject["type"]?.jsonPrimitive?.content ?: elem.jsonObject["limitType"]?.jsonPrimitive?.content
                            type == "CREDIT_LIMIT" || type == "TOKENS_LIMIT"
                        }?.jsonObject ?: limitsArray.first().jsonObject

                        val limitType = primaryLimit["type"]?.jsonPrimitive?.content
                            ?: primaryLimit["limitType"]?.jsonPrimitive?.content ?: "쿼터"

                        val current = primaryLimit["currentValue"]?.jsonPrimitive?.doubleOrNull
                            ?: primaryLimit["usage"]?.jsonPrimitive?.doubleOrNull ?: 0.0

                        val total = primaryLimit["threshold"]?.jsonPrimitive?.doubleOrNull
                            ?: primaryLimit["total"]?.jsonPrimitive?.doubleOrNull ?: 0.0

                        val expireTime = primaryLimit["expireTime"]?.jsonPrimitive?.longOrNull
                            ?: primaryLimit["resetTime"]?.jsonPrimitive?.longOrNull

                        val remaining = (total - current).coerceAtLeast(0.0)
                        val percent = if (total > 0) ((current / total) * 100).toFloat().coerceIn(0f, 100f) else null

                        val unitName = when (limitType) {
                            "CREDIT_LIMIT" -> "크레딧"
                            "TOKENS_LIMIT" -> "토큰"
                            else -> "포인트"
                        }

                        val remainingText = if (total > 0) {
                            String.format(Locale.US, "%,.0f %s 남음", remaining, unitName)
                        } else {
                            String.format(Locale.US, "사용량: %,.0f %s", current, unitName)
                        }

                        val totalText = if (total > 0) {
                            String.format(Locale.US, "총 한도 %,.0f %s (%s)", total, unitName, limitType)
                        } else {
                            "Z.ai 코딩 플랜 ($limitType)"
                        }

                        val resetText = expireTime?.let {
                            formatResetTime(it)
                        }

                        return@withContext TokenStatus(
                            accountId = account.id,
                            provider = ProviderType.ZAI,
                            accountName = account.name,
                            remainingFormatted = remainingText,
                            totalLimitFormatted = totalText,
                            usagePercent = percent,
                            resetTimeFormatted = resetText,
                            statusSummary = if (total > 0) String.format(Locale.US, "%,.0f %s", remaining, unitName) else "Z.ai 활성",
                            isWarning = (percent != null && percent > 80f),
                            isCritical = (percent != null && percent > 95f)
                        )
                    }

                    // No limits array, but success response
                    return@withContext TokenStatus(
                        accountId = account.id,
                        provider = ProviderType.ZAI,
                        accountName = account.name,
                        remainingFormatted = "Z.ai 코딩 플랜 활성화됨",
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

                // Fallback: ping OpenAI-compatible coding endpoint
                return@withContext checkCodingPlanFallback(baseUrl, authHeader, account)
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

    private fun checkCodingPlanFallback(baseUrl: String, authHeader: String, account: ProviderAccount): TokenStatus {
        val modelsUrl = "$baseUrl/api/coding/paas/v4/models"
        return try {
            val req = Request.Builder()
                .url(modelsUrl)
                .addHeader("Authorization", authHeader)
                .build()
            client.newCall(req).execute().use { res ->
                if (res.isSuccessful) {
                    TokenStatus(
                        accountId = account.id,
                        provider = ProviderType.ZAI,
                        accountName = account.name,
                        remainingFormatted = "Z.ai 코딩 플랜 연동 성공",
                        totalLimitFormatted = "GLM 코딩 플랜 활성",
                        statusSummary = "Z.ai 정상"
                    )
                } else {
                    TokenStatus(
                        accountId = account.id,
                        provider = ProviderType.ZAI,
                        accountName = account.name,
                        remainingFormatted = "HTTP ${res.code}",
                        statusSummary = "오류 (${res.code})",
                        isError = true,
                        errorMessage = "Z.ai 응답 오류 (${res.code})"
                    )
                }
            }
        } catch (e: Exception) {
            TokenStatus(
                accountId = account.id,
                provider = ProviderType.ZAI,
                accountName = account.name,
                remainingFormatted = "연결 실패",
                statusSummary = "오류",
                isError = true,
                errorMessage = e.localizedMessage ?: "연결 실패"
            )
        }
    }

    private fun formatResetTime(timestampMs: Long): String {
        val now = System.currentTimeMillis()
        val diffMs = timestampMs - now
        return if (diffMs > 0) {
            val hours = diffMs / (1000 * 60 * 60)
            val minutes = (diffMs % (1000 * 60 * 60)) / (1000 * 60)
            if (hours > 0) "리셋: ${hours}시간 ${minutes}분 후" else "리셋: ${minutes}분 후"
        } else {
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            "리셋 완료 (${sdf.format(Date(timestampMs))})"
        }
    }
}
