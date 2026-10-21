package org.parkjw.apps.oot.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.parkjw.apps.oot.data.model.ProviderAccount
import org.parkjw.apps.oot.data.model.ProviderType
import org.parkjw.apps.oot.data.model.Remaining
import org.parkjw.apps.oot.data.model.TokenStatus
import org.parkjw.apps.oot.data.model.UsageWindow
import org.parkjw.apps.oot.notification.NotificationContent
import kotlin.math.roundToInt

class OllamaCloudApi(
    private val client: OkHttpClient,
    private val json: Json
) {
    suspend fun fetchStatus(account: ProviderAccount): TokenStatus = withContext(Dispatchers.IO) {
        val credential = account.activeCredential().trim().removePrefix("Bearer ").trim()
        if (credential.isBlank()) {
            return@withContext errorStatus(
                account,
                remainingFormatted = "API 키 없음",
                errorMessage = "Ollama Cloud API 키를 등록해주세요."
            )
        }

        val baseUrl = account.customEndpoint?.takeIf { it.isNotBlank() } ?: ProviderType.OLLAMA_CLOUD.baseUrl
        val request = Request.Builder()
            .url("$baseUrl/api/balance")
            .addHeader("Authorization", "Bearer $credential")
            .addHeader("Accept", "application/json")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                when {
                    response.isSuccessful -> {
                        val windows = OllamaCloudCodec.parse(body, json)
                        when {
                            windows == null -> errorStatus(
                                account,
                                remainingFormatted = "조회 실패",
                                errorMessage = "Ollama Cloud 응답을 해석하지 못했어요."
                            )
                            windows.isEmpty() -> errorStatus(
                                account,
                                remainingFormatted = "사용량 정보 없음",
                                errorMessage = "Ollama Cloud 사용량 정보가 비어 있어요."
                            )
                            else -> successStatus(account, windows)
                        }
                    }
                    response.code == 401 -> errorStatus(
                        account,
                        remainingFormatted = "인증 실패 (401)",
                        errorMessage = "Ollama Cloud API 키를 확인해주세요."
                    )
                    else -> errorStatus(
                        account,
                        remainingFormatted = "HTTP ${response.code}",
                        errorMessage = "Ollama Cloud 서버 응답 오류 (${response.code})"
                    )
                }
            }
        } catch (e: Exception) {
            errorStatus(
                account,
                remainingFormatted = "네트워크 오류",
                errorMessage = e.localizedMessage ?: "연결 실패"
            )
        }
    }

    private fun successStatus(account: ProviderAccount, windows: List<UsageWindow>): TokenStatus {
        val text = windows.joinToString(" | ") { window ->
            when (val remaining = window.remaining) {
                is Remaining.Percent -> "${window.label} ${remaining.percentLeft.roundToInt()}% 남음"
                is Remaining.Balance -> "${window.label} ${NotificationContent.formatBalance(remaining.amountLeft, remaining.currency)}"
            }
        }
        return TokenStatus(
            accountId = account.id,
            provider = ProviderType.OLLAMA_CLOUD,
            accountName = account.name,
            remainingFormatted = text,
            statusSummary = "OL: ${windows.first().label}",
            windows = windows
        )
    }

    private fun errorStatus(
        account: ProviderAccount,
        remainingFormatted: String,
        errorMessage: String
    ): TokenStatus = TokenStatus(
        accountId = account.id,
        provider = ProviderType.OLLAMA_CLOUD,
        accountName = account.name,
        remainingFormatted = remainingFormatted,
        statusSummary = "확인 필요",
        isError = true,
        errorMessage = errorMessage
    )
}
