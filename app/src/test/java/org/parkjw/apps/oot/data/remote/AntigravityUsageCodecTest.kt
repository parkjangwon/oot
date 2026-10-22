package org.parkjw.apps.oot.data.remote

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.parkjw.apps.oot.data.model.Remaining
import org.parkjw.apps.oot.data.model.UsageWindow
import org.parkjw.apps.oot.data.model.VendorError
import java.time.Instant

// Fixtures are FAKE: ids, projects and numbers are made up to mirror the documented response shapes.

// Robolectric: error texts come from string resources (AppText), Korean by default.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AntigravityUsageCodecTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun windows(result: AntigravityQuotaResult): List<UsageWindow> =
        (result as AntigravityQuotaResult.Success).windows

    private fun percent(window: UsageWindow): Double = (window.remaining as Remaining.Percent).percentLeft

    private fun failure(result: AntigravityQuotaResult): VendorError =
        (result as AntigravityQuotaResult.Failure).error

    // -- retrieveUserQuotaSummary --

    // Shape of CodexBar's reference summary fixture (real bucket ids, fake numbers).
    private fun summaryBody(geminiWeekly: Double, gemini5h: Double, thirdWeekly: Double, third5h: Double) = """
        {
          "response": {
            "description": "Within each group, models share a weekly limit and a 5-hour limit.",
            "groups": [
              {
                "displayName": "Gemini Models",
                "description": "Models within this group: Gemini Flash, Gemini Pro",
                "buckets": [
                  {"bucketId": "gemini-weekly", "displayName": "Weekly Limit",
                   "remaining": {"remainingFraction": $geminiWeekly},
                   "description": "You have used some of your weekly limit, it will fully refresh in 5 days, 11 hours.",
                   "resetTime": "2026-06-19T08:45:39Z"},
                  {"bucketId": "gemini-5h", "displayName": "Five Hour Limit",
                   "remaining": {"remainingFraction": $gemini5h},
                   "description": "You have used some of your 5-hour limit, it will fully refresh in 4 hours.",
                   "resetTime": "2026-06-15T11:39:34Z"}
                ]
              },
              {
                "displayName": "Claude and GPT models",
                "description": "Models within this group: Claude Opus, Claude Sonnet, GPT-OSS",
                "buckets": [
                  {"bucketId": "3p-weekly", "displayName": "Weekly Limit",
                   "remaining": {"remainingFraction": $thirdWeekly},
                   "description": "You have used some of your weekly limit, it will fully refresh in 6 days, 22 hours.",
                   "resetTime": "2026-06-20T00:39:54Z"},
                  {"bucketId": "3p-5h", "displayName": "Five Hour Limit",
                   "remaining": {"remainingFraction": $third5h},
                   "description": "You have used some of your 5-hour limit, it will fully refresh in 3 hours, 38 minutes.",
                   "resetTime": "2026-06-15T12:52:10Z"}
                ]
              }
            ]
          }
        }
    """.trimIndent()

    @Test
    fun `summary shape yields the lowest bucket of each group with its own reset`() {
        // weekly is the lowest bucket on both sides
        val weekly = windows(parseAntigravityQuotaSummary(200, summaryBody(0.82, 0.91, 0.64, 0.73), json))

        assertEquals(listOf("google", "third_party"), weekly.map { it.id })
        assertEquals(listOf("Google 모델", "타사 모델"), weekly.map { it.label })
        assertEquals(82.0, percent(weekly[0]), 1e-9)
        assertEquals(Instant.parse("2026-06-19T08:45:39Z"), weekly[0].resetsAt)
        assertEquals(64.0, percent(weekly[1]), 1e-9)
        assertEquals(Instant.parse("2026-06-20T00:39:54Z"), weekly[1].resetsAt)

        // the 5-hour bucket is the lowest on both sides
        val fiveHour = windows(parseAntigravityQuotaSummary(200, summaryBody(0.9, 0.62, 0.8, 0.4), json))

        assertEquals(62.0, percent(fiveHour[0]), 1e-9)
        assertEquals(Instant.parse("2026-06-15T11:39:34Z"), fiveHour[0].resetsAt)
        assertEquals(40.0, percent(fiveHour[1]), 1e-9)
        assertEquals(Instant.parse("2026-06-15T12:52:10Z"), fiveHour[1].resetsAt)
    }

    @Test
    fun `oneof remaining value shape is read too`() {
        val body = """
            {"response": {"groups": [{"displayName": "Gemini Models", "buckets": [
              {"bucketId": "gemini-weekly", "remaining": {"case": "remainingFraction", "value": 0.5}}
            ]}]}}
        """.trimIndent()

        assertEquals(50.0, percent(windows(parseAntigravityQuotaSummary(200, body, json)).single()), 1e-9)
    }

    @Test
    fun `summary groups are also read from summary and from the root`() {
        val groups = """"groups": [{"name": "Gemini", "buckets": [{"id": "gemini-5h", "remainingFraction": 0.5}]}]"""

        assertEquals(50.0, percent(windows(parseAntigravityQuotaSummary(200, "{\"summary\": {$groups}}", json)).single()), 1e-9)
        assertEquals(50.0, percent(windows(parseAntigravityQuotaSummary(200, "{$groups}", json)).single()), 1e-9)
    }

    @Test
    fun `ordinary cadence buckets win over a lower model-specific allowance`() {
        val body = """
            {"groups": [{"displayName": "Gemini Models", "buckets": [
              {"bucketId": "gemini-3-pro-allowance", "displayName": "Gemini 3 Pro", "remainingFraction": 0.0},
              {"bucketId": "gemini-5h", "remainingFraction": 0.7}
            ]}]}
        """.trimIndent()

        assertEquals(70.0, percent(windows(parseAntigravityQuotaSummary(200, body, json)).single()), 1e-9)
    }

    @Test
    fun `explicit window cadence marks an ordinary bucket and model-specific buckets are the fallback`() {
        val body = """
            {"groups": [{"displayName": "Gemini Models", "buckets": [
              {"bucketId": "opaque-1", "window": "weekly", "remainingFraction": 0.9},
              {"bucketId": "gemini-3-pro-allowance", "remainingFraction": 0.1}
            ]}, {"displayName": "Claude and GPT models", "buckets": [
              {"bucketId": "claude-a", "remainingFraction": 0.6},
              {"bucketId": "gpt-b", "remainingFraction": 0.3}
            ]}]}
        """.trimIndent()

        val result = windows(parseAntigravityQuotaSummary(200, body, json))
        assertEquals(90.0, percent(result[0]), 1e-9)
        assertEquals(30.0, percent(result[1]), 1e-9)
    }

    @Test
    fun `disabled and unmeasured buckets are ignored and a numeric reset is epoch seconds`() {
        val body = """
            {"groups": [{"displayName": "Gemini Models", "buckets": [
              {"bucketId": "gemini-5h", "disabled": true, "remainingFraction": 0.0},
              {"bucketId": "gemini-weekly", "resetTime": "2026-10-26T00:00:00Z"},
              {"bucketId": "gemini-session", "remainingFraction": 0.25, "resetTime": "1792591200"}
            ]}]}
        """.trimIndent()

        val google = windows(parseAntigravityQuotaSummary(200, body, json)).single()
        assertEquals(25.0, percent(google), 1e-9)
        assertEquals(Instant.ofEpochSecond(1792591200L), google.resetsAt)
    }

    @Test
    fun `summary with only one group yields only that window`() {
        val body = """
            {"groups": [{"displayName": "Claude and GPT models", "buckets": [
              {"bucketId": "claude-gpt-5h", "remainingFraction": 0.55}
            ]}]}
        """.trimIndent()

        val result = windows(parseAntigravityQuotaSummary(200, body, json))
        assertEquals(listOf("third_party"), result.map { it.id })
        assertEquals(55.0, percent(result.single()), 1e-9)
        assertNull(result.single().resetsAt)
    }

    @Test
    fun `summary without any measured group is a failure so the caller can fall back`() {
        assertEquals(VendorError.Unknown("한도 정보를 찾지 못했어요"), failure(parseAntigravityQuotaSummary(200, """{"groups": []}""", json)))
        assertEquals(VendorError.Unknown("한도 정보를 찾지 못했어요"), failure(parseAntigravityQuotaSummary(200, "{}", json)))
        // legacy model-bucket answer from the summary endpoint
        val legacy = """{"buckets": [{"modelId": "gemini-3-pro", "remainingFraction": 0.5}]}"""
        assertEquals(VendorError.Unknown("한도 정보를 찾지 못했어요"), failure(parseAntigravityQuotaSummary(200, legacy, json)))
    }

    // -- fetchAvailableModels --

    private val modelsBody = """
        {
          "models": {
            "gemini-3-pro-high": {"displayName": "Gemini 3 Pro (High)",
              "quotaInfo": {"remainingFraction": 0.62, "resetTime": "2026-10-21T14:00:00Z"}},
            "gemini-3-flash": {"displayName": "Gemini 3 Flash",
              "quotaInfo": {"remainingFraction": 0.9, "resetTime": "2026-10-21T14:00:00Z"}},
            "gemini-2.5-flash-lite": {"quotaInfo": {"remainingFraction": 0.01, "resetTime": "2026-10-21T13:00:00Z"}},
            "gemini-3-pro-image": {"quotaInfo": {"remainingFraction": 0.02}},
            "tab_flash_lite_preview": {"quotaInfo": {"remainingFraction": 0.03}},
            "chat_20706": {"quotaInfo": {"remainingFraction": 0.04}},
            "claude-sonnet-4-5": {"displayName": "Claude Sonnet 4.5",
              "quotaInfo": {"remainingFraction": 0.4, "resetTime": "2026-10-21T15:30:00Z"}},
            "gpt-oss-120b-medium": {"displayName": "GPT-OSS 120B (Medium)",
              "quotaInfo": {"remainingFraction": 0.7, "resetTime": "2026-10-21T16:00:00Z"}},
            "no-quota-model": {"displayName": "Gemini 3 Pro Preview"}
          }
        }
    """.trimIndent()

    @Test
    fun `fetchAvailableModels shape yields the lowest Gemini and Claude-GPT model`() {
        val result = windows(parseAntigravityAvailableModels(200, modelsBody, json))

        assertEquals(listOf("google", "third_party"), result.map { it.id })
        assertEquals(62.0, percent(result[0]), 1e-9)
        assertEquals(Instant.parse("2026-10-21T14:00:00Z"), result[0].resetsAt)
        assertEquals(40.0, percent(result[1]), 1e-9)
        assertEquals(Instant.parse("2026-10-21T15:30:00Z"), result[1].resetsAt)
    }

    @Test
    fun `fetchAvailableModels classifies by label when the id says nothing`() {
        val body = """{"models": {"model-9": {"displayName": "Gemini 3 Flash", "quotaInfo": {"remainingFraction": 0.3}}}}"""

        val result = windows(parseAntigravityAvailableModels(200, body, json))
        assertEquals(listOf("google"), result.map { it.id })
        assertEquals(30.0, percent(result.single()), 1e-9)
    }

    @Test
    fun `fetchAvailableModels without a Gemini model yields only the third-party window`() {
        val body = """{"models": {"claude-opus-4-5": {"quotaInfo": {"remainingFraction": 1.0}}}}"""

        assertEquals(listOf("third_party"), windows(parseAntigravityAvailableModels(200, body, json)).map { it.id })
    }

    @Test
    fun `fetchAvailableModels with no measurable model is a failure`() {
        val body = """{"models": {"gemini-3-pro": {"quotaInfo": {"resetTime": "2026-10-21T14:00:00Z"}}}}"""

        assertEquals(VendorError.Unknown("한도 정보를 찾지 못했어요"), failure(parseAntigravityAvailableModels(200, body, json)))
        assertEquals(VendorError.Parse, failure(parseAntigravityAvailableModels(200, """{"models": []}""", json)))
    }

    // -- HTTP outcomes --

    private val unauthorizedBody =
        """{"error": {"code": 401, "message": "Request had invalid authentication credentials.", "status": "UNAUTHENTICATED"}}"""

    @Test
    fun `401 is Unauthorized for both endpoints so the caller refreshes and retries`() {
        assertEquals(VendorError.Unauthorized, mapAntigravityHttpError(401))
        assertEquals(VendorError.Unauthorized, failure(parseAntigravityQuotaSummary(401, unauthorizedBody, json)))
        assertEquals(VendorError.Unauthorized, failure(parseAntigravityAvailableModels(401, unauthorizedBody, json)))
    }

    @Test
    fun `other http errors use the shared mapping`() {
        assertEquals(VendorError.ScopeMissing, failure(parseAntigravityQuotaSummary(403, "{}", json)))
        assertEquals(VendorError.RateLimited, failure(parseAntigravityAvailableModels(429, "{}", json)))
        assertEquals(VendorError.Http(503), failure(parseAntigravityQuotaSummary(503, "", json)))
    }

    @Test
    fun `unparseable success body is a parse failure`() {
        assertEquals(VendorError.Parse, failure(parseAntigravityQuotaSummary(200, "not json", json)))
        assertEquals(VendorError.Parse, failure(parseAntigravityAvailableModels(200, "[]", json)))
    }

    // -- loadCodeAssist --

    @Test
    fun `code assist project id as string or object`() {
        assertEquals("demo-project-000", parseAntigravityCodeAssist("""{"cloudaicompanionProject": "demo-project-000"}""", json)?.projectId)
        assertEquals("demo-project-001", parseAntigravityCodeAssist("""{"cloudaicompanionProject": {"id": "demo-project-001"}}""", json)?.projectId)
        assertEquals("demo-project-002", parseAntigravityCodeAssist("""{"cloudaicompanionProject": {"projectId": " demo-project-002 "}}""", json)?.projectId)
        assertNull(parseAntigravityCodeAssist("""{"cloudaicompanionProject": ""}""", json)?.projectId)
        assertNull(parseAntigravityCodeAssist("{}", json)?.projectId)
    }

    @Test
    fun `code assist flags a client Google refused for consumer sign-in`() {
        val rejected = """{"ineligibleTiers": [{"reasonCode": "GOOGLE_TOS_NOT_SUPPORTED_BY_CLIENT"}]}"""
        assertTrue(parseAntigravityCodeAssist(rejected, json)!!.clientRejected)

        val withTier = """{"currentTier": {"id": "free-tier"}, "ineligibleTiers": [{"reasonCode": "GOOGLE_TOS_NOT_SUPPORTED_BY_CLIENT"}]}"""
        assertFalse(parseAntigravityCodeAssist(withTier, json)!!.clientRejected)

        val withProject = """{"cloudaicompanionProject": "demo-project-000", "ineligibleTiers": [{"reasonCode": "GOOGLE_TOS_NOT_SUPPORTED_BY_CLIENT"}]}"""
        assertFalse(parseAntigravityCodeAssist(withProject, json)!!.clientRejected)
        assertFalse(parseAntigravityCodeAssist("{}", json)!!.clientRejected)
    }

    @Test
    fun `code assist parse failure is null`() {
        assertNull(parseAntigravityCodeAssist("<html>", json))
    }
}
