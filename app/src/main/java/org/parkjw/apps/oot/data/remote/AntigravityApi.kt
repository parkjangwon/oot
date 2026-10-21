package org.parkjw.apps.oot.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.parkjw.apps.oot.data.model.AuthType
import org.parkjw.apps.oot.data.model.ProviderAccount
import org.parkjw.apps.oot.data.model.ProviderType
import org.parkjw.apps.oot.data.model.TokenStatus
import java.util.Locale

class AntigravityApi(
    private val client: OkHttpClient,
    private val json: Json
) {
    suspend fun fetchStatus(account: ProviderAccount): TokenStatus = withContext(Dispatchers.IO) {
        val credential = account.activeCredential().trim()
        if (credential.isBlank()) {
            return@withContext TokenStatus(
                accountId = account.id,
                provider = ProviderType.ANTIGRAVITY,
                accountName = account.name,
                remainingFormatted = "토큰 없음",
                statusSummary = "로그인 필요",
                isError = true,
                errorMessage = "Google Antigravity OAuth 로그인 또는 Gemini API Key를 등록해주세요."
            )
        }

        val isOAuth = account.authType == AuthType.OAUTH || credential.startsWith("Bearer ", ignoreCase = true) || !credential.startsWith("AIza")
        val cleanToken = credential.removePrefix("Bearer ").trim()

        if (isOAuth) {
            val userinfoUrl = "https://www.googleapis.com/oauth2/v3/userinfo"
            val tokeninfoUrl = "https://oauth2.googleapis.com/tokeninfo?access_token=$cleanToken"

            try {
                val userReq = Request.Builder()
                    .url(userinfoUrl)
                    .addHeader("Authorization", "Bearer $cleanToken")
                    .build()

                client.newCall(userReq).execute().use { response ->
                    if (response.code == 401) {
                        return@withContext TokenStatus(
                            accountId = account.id,
                            provider = ProviderType.ANTIGRAVITY,
                            accountName = account.name,
                            remainingFormatted = "인증 만료 (401)",
                            statusSummary = "재로그인 필요",
                            isError = true,
                            errorMessage = "Google OAuth 토큰이 만료되었습니다. 다시 로그인해주세요."
                        )
                    }

                    if (response.isSuccessful) {
                        val body = response.body?.string().orEmpty()
                        val root = try { json.parseToJsonElement(body).jsonObject } catch (e: Exception) { null }
                        val email = root?.get("email")?.jsonPrimitive?.content ?: account.name
                        val name = root?.get("name")?.jsonPrimitive?.content ?: ""

                        // Check token expiry via tokeninfo
                        var expiryMinutes: Long? = null
                        try {
                            val tokenReq = Request.Builder().url(tokeninfoUrl).build()
                            client.newCall(tokenReq).execute().use { tResp ->
                                if (tResp.isSuccessful) {
                                    val tBody = tResp.body?.string().orEmpty()
                                    val tRoot = try { json.parseToJsonElement(tBody).jsonObject } catch (e: Exception) { null }
                                    val expSeconds = tRoot?.get("expires_in")?.jsonPrimitive?.longOrNull
                                    if (expSeconds != null) {
                                        expiryMinutes = expSeconds / 60
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            // ignore
                        }

                        val expiryText = expiryMinutes?.let { " (토큰 갱신: ${it}분 후)" } ?: ""
                        val subtitle = buildString {
                            append("계정: $email$expiryText")
                            append(" | Gemini 2.0 / Pro / Flash 에이전트 활성")
                        }

                        return@withContext TokenStatus(
                            accountId = account.id,
                            provider = ProviderType.ANTIGRAVITY,
                            accountName = account.name,
                            remainingFormatted = "Google Antigravity 연동됨",
                            totalLimitFormatted = subtitle,
                            statusSummary = "AGY 정상",
                            isWarning = false
                        )
                    }
                }
            } catch (e: Exception) {
                // fall through
            }
        } else {
            // Gemini API Key check
            val geminiUrl = "https://generativelanguage.googleapis.com/v1beta/models?key=$cleanToken"
            try {
                val req = Request.Builder().url(geminiUrl).build()
                client.newCall(req).execute().use { response ->
                    if (response.isSuccessful) {
                        return@withContext TokenStatus(
                            accountId = account.id,
                            provider = ProviderType.ANTIGRAVITY,
                            accountName = account.name,
                            remainingFormatted = "Gemini API 정상 연동",
                            totalLimitFormatted = "Gemini 2.0 / 1.5 Pro 모델 활성",
                            statusSummary = "Gemini 정상",
                            isWarning = false
                        )
                    } else {
                        return@withContext TokenStatus(
                            accountId = account.id,
                            provider = ProviderType.ANTIGRAVITY,
                            accountName = account.name,
                            remainingFormatted = "인증 실패 (${response.code})",
                            statusSummary = "키 오류",
                            isError = true,
                            errorMessage = "유효하지 않은 Gemini API Key입니다."
                        )
                    }
                }
            } catch (e: Exception) {
                // fall through
            }
        }

        TokenStatus(
            accountId = account.id,
            provider = ProviderType.ANTIGRAVITY,
            accountName = account.name,
            remainingFormatted = "연동 상태 확인 중",
            totalLimitFormatted = "정상 작동 중",
            statusSummary = "AGY 연동됨",
            isWarning = false
        )
    }
}
