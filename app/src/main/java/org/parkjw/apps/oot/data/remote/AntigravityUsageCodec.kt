package org.parkjw.apps.oot.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import org.parkjw.apps.oot.data.model.Remaining
import org.parkjw.apps.oot.data.model.UsageWindow
import org.parkjw.apps.oot.data.model.VendorError
import java.time.DateTimeException
import java.time.Instant

// Pure parsing of the Cloud Code quota responses, shaped after the open-source CodexBar Antigravity provider
// (docs/antigravity.md + AntigravityRemoteUsageFetcher / AntigravityQuotaSummaryParser).

const val ANTIGRAVITY_GOOGLE_WINDOW_ID = "google"
const val ANTIGRAVITY_THIRD_PARTY_WINDOW_ID = "third_party"

private const val GOOGLE_LABEL = "Google 모델"
private const val THIRD_PARTY_LABEL = "타사 모델"
private const val NO_QUOTA_TEXT = "한도 정보를 찾지 못했어요"
private const val CLIENT_REJECTED_REASON = "GOOGLE_TOS_NOT_SUPPORTED_BY_CLIENT"
private val cadenceAliases = listOf("session", "5h", "5-hour", "five hour", "five-hour", "weekly")

sealed interface AntigravityQuotaResult {
    data class Success(val windows: List<UsageWindow>) : AntigravityQuotaResult
    data class Failure(val error: VendorError) : AntigravityQuotaResult
}

// 401 stays Unauthorized so the caller can refresh the token and retry once; the rest reuse the shared mapping.
fun mapAntigravityHttpError(code: Int): VendorError =
    if (code == 401) VendorError.Unauthorized else mapClaudeHttpError(code)

// Google's error envelope reduced to its codes: `error.status` and each `error.details[].reason`,
// e.g. "PERMISSION_DENIED · SERVICE_DISABLED". The free-text message is left out (it can name the project).
fun googleErrorReason(body: String, json: Json = Json): String? {
    val error = parseObject(body, json)?.get("error") as? JsonObject ?: return null
    val status = error.string("status")
    val reasons = (error["details"] as? JsonArray).orEmpty()
        .mapNotNull { (it as? JsonObject)?.string("reason") }
    return (listOfNotNull(status) + reasons).distinct().joinToString(" · ").ifBlank { null }
}

// 401 still means "refresh and retry" and 429 still backs off; any other failure shows Google's status and reason
// on the card, so a phone test tells an accepted client from a rejected one (e.g. 403 PERMISSION_DENIED · SERVICE_DISABLED).
fun antigravityHttpError(code: Int, body: String, json: Json = Json): VendorError = when (code) {
    401, 429 -> mapAntigravityHttpError(code)
    else -> VendorError.Unknown(
        "조회 실패 (HTTP $code" + (googleErrorReason(body, json)?.let { " $it" } ?: "") + ")"
    )
}

data class AntigravityCodeAssist(val projectId: String?, val clientRejected: Boolean)

// loadCodeAssist: the Cloud Code project id, and whether Google refused this OAuth client for consumer sign-in
// (then every quota endpoint answers a placeholder 100%, so the account has to sign in again).
fun parseAntigravityCodeAssist(body: String, json: Json = Json): AntigravityCodeAssist? {
    val root = parseObject(body, json) ?: return null
    val project = root["cloudaicompanionProject"]
    val projectId = when (project) {
        is JsonPrimitive -> project.takeIf { it.isString }?.content
        is JsonObject -> project.string("id") ?: project.string("projectId")
        else -> null
    }?.trim()?.takeIf { it.isNotEmpty() }
    val clientRejected = projectId == null && root["currentTier"] !is JsonObject &&
        (root["ineligibleTiers"] as? JsonArray).orEmpty().any {
            (it as? JsonObject)?.string("reasonCode") == CLIENT_REJECTED_REASON
        }
    return AntigravityCodeAssist(projectId, clientRejected)
}

// retrieveUserQuotaSummary: groups[].buckets[]. A group whose title mentions Gemini is the Google window,
// every other group is the third-party one. Disabled buckets and buckets without a fraction are unmeasured.
fun parseAntigravityQuotaSummary(httpCode: Int, body: String, json: Json = Json): AntigravityQuotaResult {
    httpFailure(httpCode)?.let { return it }
    val root = parseObject(body, json) ?: return AntigravityQuotaResult.Failure(VendorError.Parse)
    // CodexBar decodes the groups from `response`, `summary` or the root object.
    val payload = root["response"] as? JsonObject ?: root["summary"] as? JsonObject ?: root
    val candidates = (payload["groups"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.flatMap { group ->
        val title = group.string("displayName") ?: group.string("name") ?: ""
        val windowId =
            if (title.contains("gemini", ignoreCase = true)) ANTIGRAVITY_GOOGLE_WINDOW_ID else ANTIGRAVITY_THIRD_PARTY_WINDOW_ID
        (group["buckets"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.toBucketCandidate(windowId) }
    }
    return resultOf(windowsOf(candidates))
}

// fetchAvailableModels fallback: models is a map keyed by model id, each with quotaInfo.{remainingFraction,resetTime}.
// shortcut: an all-100% payload may be availability-only (CodexBar re-checks it with retrieveUserQuota), upgrade if seen.
fun parseAntigravityAvailableModels(httpCode: Int, body: String, json: Json = Json): AntigravityQuotaResult {
    httpFailure(httpCode)?.let { return it }
    val root = parseObject(body, json) ?: return AntigravityQuotaResult.Failure(VendorError.Parse)
    val models = root["models"] as? JsonObject ?: return AntigravityQuotaResult.Failure(VendorError.Parse)
    val candidates = models.mapNotNull { (modelId, element) ->
        val model = element as? JsonObject ?: return@mapNotNull null
        val quota = model["quotaInfo"] as? JsonObject ?: return@mapNotNull null
        val fraction = quota.number("remainingFraction") ?: return@mapNotNull null
        val label = model.string("displayName") ?: model.string("label") ?: modelId
        val windowId = modelWindowId(modelId.lowercase(), label.lowercase()) ?: return@mapNotNull null
        Candidate(windowId, fraction, parseAntigravityInstant(quota.string("resetTime")), isCadence = false)
    }
    return resultOf(windowsOf(candidates))
}

private class Candidate(
    val windowId: String,
    val remainingFraction: Double,
    val resetsAt: Instant?,
    val isCadence: Boolean
)

private fun httpFailure(code: Int): AntigravityQuotaResult.Failure? =
    if (code in 200..299) null else AntigravityQuotaResult.Failure(mapAntigravityHttpError(code))

private fun resultOf(windows: List<UsageWindow>): AntigravityQuotaResult =
    if (windows.isEmpty()) AntigravityQuotaResult.Failure(VendorError.Unknown(NO_QUOTA_TEXT))
    else AntigravityQuotaResult.Success(windows)

// One window per side, holding the lowest remaining candidate and its own reset (the earlier reset breaks ties).
// Ordinary 5-hour / weekly buckets win over model-specific allowances, as in CodexBar.
private fun windowsOf(candidates: List<Candidate>): List<UsageWindow> =
    listOf(
        ANTIGRAVITY_GOOGLE_WINDOW_ID to GOOGLE_LABEL,
        ANTIGRAVITY_THIRD_PARTY_WINDOW_ID to THIRD_PARTY_LABEL
    ).mapNotNull { (id, label) ->
        val ofWindow = candidates.filter { it.windowId == id }
        val lowest = ofWindow.filter { it.isCadence }.ifEmpty { ofWindow }
            .minWithOrNull(compareBy<Candidate>({ it.remainingFraction }, { it.resetsAt ?: Instant.MAX }))
            ?: return@mapNotNull null
        UsageWindow(id, label, Remaining.Percent((lowest.remainingFraction * 100).coerceIn(0.0, 100.0)), lowest.resetsAt)
    }

private fun JsonObject.toBucketCandidate(windowId: String): Candidate? {
    if ((this["disabled"] as? JsonPrimitive)?.booleanOrNull == true) return null
    val fraction = remainingFraction() ?: return null
    return Candidate(windowId, fraction, parseAntigravityInstant(string("resetTime")), isCadenceBucket())
}

// remainingFraction sits on the bucket, under `remaining`, or in the {case, value} oneof form.
private fun JsonObject.remainingFraction(): Double? =
    number("remainingFraction") ?: (this["remaining"] as? JsonObject)?.let { remaining ->
        remaining.number("remainingFraction")
            ?: remaining.number("value").takeIf { remaining.string("case") == "remainingFraction" }
    }

// Cadence comes from the explicit `window`, else from the bucket id / display name ("gemini-5h", "Weekly limit").
private fun JsonObject.isCadenceBucket(): Boolean {
    val names = string("window")?.takeIf { it.isNotBlank() }?.let { listOf(it) }
        ?: listOfNotNull(string("bucketId") ?: string("id"), string("displayName") ?: string("name"))
    return names.any { raw ->
        val name = raw.trim().lowercase().replace('_', '-').removeSuffix(" limit")
        cadenceAliases.any { name == it || name.endsWith("-$it") }
    }
}

// CodexBar's remote filter: only Gemini Pro/Flash and Claude/GPT text models count; lite, autocomplete and image rows
// and unknown families are ignored.
private fun modelWindowId(id: String, name: String): String? {
    if (id.startsWith("tab_") || listOf(id, name).any { "lite" in it || "autocomplete" in it || "image" in it }) return null
    return modelFamily(id) ?: modelFamily(name)
}

private fun modelFamily(text: String): String? = when {
    "claude" in text || "gpt" in text || "openai" in text -> ANTIGRAVITY_THIRD_PARTY_WINDOW_ID
    "gemini" in text && ("pro" in text || "flash" in text) -> ANTIGRAVITY_GOOGLE_WINDOW_ID
    else -> null
}

// ISO-8601 first, epoch seconds as the fallback (both accepted by CodexBar).
private fun parseAntigravityInstant(raw: String?): Instant? =
    parseClaudeInstant(raw) ?: raw?.trim()?.toDoubleOrNull()?.takeIf { it.isFinite() }?.let {
        try {
            Instant.ofEpochSecond(it.toLong())
        } catch (e: DateTimeException) {
            null
        }
    }

private fun parseObject(body: String, json: Json): JsonObject? =
    try { json.parseToJsonElement(body) as? JsonObject } catch (e: Exception) { null }

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun JsonObject.number(key: String): Double? =
    (this[key] as? JsonPrimitive)?.doubleOrNull?.takeIf { it.isFinite() }
