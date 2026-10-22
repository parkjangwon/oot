package org.parkjw.apps.oot.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.parkjw.apps.oot.data.model.ProviderAccount
import org.parkjw.apps.oot.data.model.ProviderType
import org.parkjw.apps.oot.data.model.Remaining
import org.parkjw.apps.oot.data.model.TokenStatus
import org.parkjw.apps.oot.data.model.UsageWindow
import org.parkjw.apps.oot.data.model.VendorError
import org.parkjw.apps.oot.data.oauth.OAuthManager
import java.util.Locale

// Google Antigravity quota through the Cloud Code endpoints documented by the open-source CodexBar provider.
// Only an OAuth access token can read it; the parsing lives in AntigravityUsageCodec.
class AntigravityApi(
    private val client: OkHttpClient,
    private val json: Json,
    private val oauthManager: OAuthManager,
    private val onAccountRefreshed: (ProviderAccount) -> Unit
) {
    private class HttpResult(val code: Int, val body: String)

    suspend fun fetchStatus(account: ProviderAccount): TokenStatus = withContext(Dispatchers.IO) {
        val credential = account.activeCredential().trim()
        if (credential.isBlank()) return@withContext errorStatus(account, "Google 로그인 필요")
        // A Gemini API key cannot read Cloud Code quota.
        if (credential.startsWith("AIza")) return@withContext errorStatus(account, "Google 로그인 필요 (API 키 미지원)")

        var result = fetchQuota(credential.bearerToken())
        if (result is AntigravityQuotaResult.Failure && result.error == VendorError.Unauthorized) {
            result = refreshAndRetry(account) ?: result
        }
        when (val outcome = result) {
            is AntigravityQuotaResult.Success -> successStatus(account, outcome.windows)
            is AntigravityQuotaResult.Failure -> errorStatus(account, claudeErrorText(outcome.error))
        }
    }

    // 401: refresh once and retry. Null means there was nothing to refresh with, so the 401 stands.
    private suspend fun refreshAndRetry(account: ProviderAccount): AntigravityQuotaResult? {
        if (!oauthManager.isAntigravityConfigured) {
            return AntigravityQuotaResult.Failure(VendorError.Unknown(OAuthManager.ANTIGRAVITY_NOT_CONFIGURED))
        }
        if (account.refreshToken.isNullOrBlank()) return null
        val refreshed = try {
            oauthManager.refreshTokenIfNeeded(account, force = true)
        } catch (e: Exception) {
            Log.w(TAG, "token refresh attempt threw error=${e.javaClass.simpleName}")
            return null
        }
        if (refreshed == account) {
            Log.w(TAG, "token refresh failed -> Unauthorized")
            return null
        }
        onAccountRefreshed(refreshed)
        Log.i(TAG, "token refresh ok, retrying quota once")
        return fetchQuota(refreshed.activeCredential().bearerToken())
    }

    private fun fetchQuota(token: String): AntigravityQuotaResult {
        val assist = post(LOAD_CODE_ASSIST_URL, token, LOAD_CODE_ASSIST_BODY)
            ?: return AntigravityQuotaResult.Failure(VendorError.Network)
        if (assist.code !in 200..299) return AntigravityQuotaResult.Failure(mapAntigravityHttpError(assist.code))
        val codeAssist = parseAntigravityCodeAssist(assist.body, json)
            ?: return AntigravityQuotaResult.Failure(VendorError.Parse)
        // Google refused the OAuth client this token came from, so its quota would be a placeholder: sign in again.
        if (codeAssist.clientRejected) return AntigravityQuotaResult.Failure(VendorError.ScopeMissing)

        val quotaBody = buildJsonObject { codeAssist.projectId?.let { put("project", it) } }.toString()
        // Summary first; anything but success or 401 falls back to the model list, like CodexBar.
        val summary = post(SUMMARY_URL, token, quotaBody)?.let { parseAntigravityQuotaSummary(it.code, it.body, json) }
        when {
            summary is AntigravityQuotaResult.Success -> return summary
            summary is AntigravityQuotaResult.Failure && summary.error == VendorError.Unauthorized -> return summary
        }
        val models = post(MODELS_URL, token, quotaBody) ?: return AntigravityQuotaResult.Failure(VendorError.Network)
        return parseAntigravityAvailableModels(models.code, models.body, json)
    }

    // Logs only the endpoint path and status: never tokens, headers or bodies.
    private fun post(url: String, token: String, body: String): HttpResult? {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("User-Agent", USER_AGENT)
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        val path = request.url.encodedPath
        return try {
            client.newCall(request).execute().use { response ->
                Log.i(TAG, "response endpoint=$path http=${response.code}")
                HttpResult(response.code, response.body?.string().orEmpty())
            }
        } catch (e: Exception) {
            Log.w(TAG, "request failed endpoint=$path error=${e.javaClass.simpleName}")
            null
        }
    }

    private fun successStatus(account: ProviderAccount, windows: List<UsageWindow>): TokenStatus {
        val text = windows.joinToString(" | ") {
            String.format(Locale.US, "%s: %.0f%% 남음", it.label, (it.remaining as Remaining.Percent).percentLeft)
        }
        return TokenStatus(
            accountId = account.id,
            provider = ProviderType.ANTIGRAVITY,
            accountName = account.name,
            remainingFormatted = text,
            statusSummary = text,
            windows = windows
        )
    }

    private fun errorStatus(account: ProviderAccount, text: String): TokenStatus = TokenStatus(
        accountId = account.id,
        provider = ProviderType.ANTIGRAVITY,
        accountName = account.name,
        remainingFormatted = text,
        statusSummary = "확인 필요",
        isError = true,
        errorMessage = text
    )

    private fun String.bearerToken(): String = removePrefix("Bearer ").trim()

    companion object {
        private const val TAG = "oot.Antigravity"
        private const val LOAD_CODE_ASSIST_URL = "https://cloudcode-pa.googleapis.com/v1internal:loadCodeAssist"
        private const val MODELS_URL = "https://cloudcode-pa.googleapis.com/v1internal:fetchAvailableModels"

        // The quota summary is served from the daily host.
        private const val SUMMARY_URL = "https://daily-cloudcode-pa.googleapis.com/v1internal:retrieveUserQuotaSummary"
        private const val LOAD_CODE_ASSIST_BODY =
            """{"metadata":{"ideType":"ANTIGRAVITY","platform":"PLATFORM_UNSPECIFIED","pluginType":"GEMINI"}}"""

        // Cloud Code only answers quota calls for the Hub client family, so pin CodexBar's compatibility identity.
        private const val USER_AGENT = "antigravity/hub/2.9.1 linux/arm64"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
