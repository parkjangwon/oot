package org.parkjw.apps.oot.update

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class UpdateCheckerTest {
    private class FakeReleaseSource(private val respond: () -> ReleaseResponse) : ReleaseSource {
        var calls = 0
        override fun fetchReleases(): ReleaseResponse {
            calls++
            return respond()
        }
    }

    private fun ok(vararg releases: String) = ReleaseResponse(200, ReleaseFixtures.list(*releases))

    private fun check(current: String?, response: ReleaseResponse): UpdateCheckResult =
        UpdateChecker(FakeReleaseSource { response }).check(current)

    @Test
    fun sameVersionWithTagPrefixIsUpToDate() {
        val result = check("1.0.0", ok(ReleaseFixtures.release("v1.0.0")))
        result as UpdateCheckResult.UpToDate
        assertEquals(AppVersion(1, 0, 0), result.current)
        assertEquals("v1.0.0", result.latest?.tagName)
    }

    @Test
    fun newerStableReleaseIsAvailableWithApk() {
        val result = check("1.0.0", ok(ReleaseFixtures.release("v1.1.0"), ReleaseFixtures.release("v1.0.0")))
        result as UpdateCheckResult.Available
        assertEquals("v1.1.0", result.release.tagName)
        assertEquals("oot-v1.1.0.apk", result.apk?.name)
    }

    @Test
    fun picksHighestVersionRegardlessOfListOrder() {
        val result = check(
            "1.0.0",
            ok(ReleaseFixtures.release("v1.1.0"), ReleaseFixtures.release("v1.10.0"), ReleaseFixtures.release("v1.9.0")),
        )
        assertEquals("v1.10.0", (result as UpdateCheckResult.Available).release.tagName)
    }

    @Test
    fun draftsAndPrereleasesAreExcluded() {
        val result = check(
            "1.0.0",
            ok(
                ReleaseFixtures.release("v1.2.0", draft = true),
                ReleaseFixtures.release("v1.1.0", prerelease = true),
                ReleaseFixtures.release("v1.1.0-rc.1"),
                ReleaseFixtures.release("nightly"),
                ReleaseFixtures.release("v1.0.0"),
            ),
        )
        result as UpdateCheckResult.UpToDate
        assertEquals("v1.0.0", result.latest?.tagName)
    }

    @Test
    fun noStableReleaseIsUpToDateWithoutLatest() {
        val onlyPre = check("1.0.0", ok(ReleaseFixtures.release("v1.1.0", prerelease = true)))
        assertNull((onlyPre as UpdateCheckResult.UpToDate).latest)
        val empty = check("1.0.0", ReleaseResponse(200, "[]"))
        assertNull((empty as UpdateCheckResult.UpToDate).latest)
    }

    @Test
    fun installedNewerThanLatestIsUpToDate() {
        val result = check("1.2.0", ok(ReleaseFixtures.release("v1.1.0")))
        assertTrue(result is UpdateCheckResult.UpToDate)
    }

    @Test
    fun installedPreReleaseSeesFinalRelease() {
        val result = check("1.1.0-rc.1", ok(ReleaseFixtures.release("v1.1.0")))
        assertEquals("v1.1.0", (result as UpdateCheckResult.Available).release.tagName)
    }

    @Test
    fun newerReleaseWithoutApkIsStillReportedWithNullApk() {
        val result = check("1.0.0", ok(ReleaseFixtures.release("v1.1.0", assets = emptyList())))
        result as UpdateCheckResult.Available
        assertNull(result.apk)
    }

    @Test
    fun unknownCurrentVersionFailsWithoutNetworkCall() {
        listOf(null, "", "dev").forEach { name ->
            val source = FakeReleaseSource { ok(ReleaseFixtures.release("v1.1.0")) }
            assertEquals(
                UpdateCheckResult.Failed(UpdateFailure.UnknownCurrentVersion),
                UpdateChecker(source).check(name),
            )
            assertEquals(0, source.calls)
        }
    }

    @Test
    fun ioExceptionsMapToNetworkFailure() {
        listOf(IOException("reset"), UnknownHostException("api.github.com"), SocketTimeoutException()).forEach { error ->
            val result = UpdateChecker { throw error }.check("1.0.0")
            assertEquals(UpdateCheckResult.Failed(UpdateFailure.Network), result)
        }
    }

    @Test
    fun rateLimitResponsesAreRecognised() {
        assertEquals(
            UpdateCheckResult.Failed(UpdateFailure.RateLimited),
            check("1.0.0", ReleaseResponse(403, "{}", mapOf("x-ratelimit-remaining" to "0"))),
        )
        assertEquals(
            UpdateCheckResult.Failed(UpdateFailure.RateLimited),
            check("1.0.0", ReleaseResponse(429, "{}", mapOf("Retry-After" to "60"))),
        )
        assertEquals(
            UpdateCheckResult.Failed(UpdateFailure.RateLimited),
            check("1.0.0", ReleaseResponse(403, "{}", mapOf("retry-after" to " 30 "))),
        )
    }

    @Test
    fun otherHttpErrorsKeepStatusCode() {
        listOf(301, 403, 404, 500, 503).forEach { code ->
            assertEquals(
                UpdateCheckResult.Failed(UpdateFailure.Http(code)),
                check("1.0.0", ReleaseResponse(code, """{"message":"x"}""", mapOf("X-RateLimit-Remaining" to "42"))),
            )
        }
    }

    @Test
    fun malformedBodyFails() {
        listOf("", "<html>", """{"message":"Not Found"}""").forEach { body ->
            assertEquals(
                UpdateCheckResult.Failed(UpdateFailure.MalformedResponse),
                check("1.0.0", ReleaseResponse(200, body)),
            )
        }
    }
}
