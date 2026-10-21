package org.parkjw.apps.oot.update

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class UpdateFlowTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private class MemoryStore : UpdateStore {
        var day: Long? = null
        var snoozed: UpdateSnooze? = null
        override suspend fun lastAutoCheckDay() = day
        override suspend fun setLastAutoCheckDay(epochDay: Long) {
            day = epochDay
        }
        override suspend fun snooze() = snoozed
        override suspend fun setSnooze(snooze: UpdateSnooze) {
            snoozed = snooze
        }
    }

    private var response: () -> ReleaseResponse = { ReleaseResponse(200, ReleaseFixtures.list(ReleaseFixtures.release("v1.1.0"))) }
    private var fetches = 0
    private var canInstall = true
    private var signatureOk = true
    private var downloadError: IOException? = null
    private val progress = mutableListOf<Int>()
    private val store = MemoryStore()
    private var today = 100L

    private fun flow() = UpdateFlow(
        checker = UpdateChecker {
            fetches++
            response()
        },
        downloader = { asset, onProgress ->
            downloadError?.let { throw it }
            listOf(0, 50, 100).forEach {
                progress += it
                onProgress(it)
            }
            tmp.newFile(asset.name)
        },
        verifier = { signatureOk },
        permission = { canInstall },
        store = store,
        currentVersionName = { "1.0.0" },
        today = { today },
    )

    @Test
    fun autoCheckIsDailyAndRespectsSwitch() = runBlocking {
        flow().autoCheckOnLaunch(enabled = false)
        assertEquals(0, fetches)
        val f = flow()
        f.autoCheckOnLaunch(enabled = true)
        assertEquals(1, fetches)
        assertEquals(100L, store.day)
        flow().autoCheckOnLaunch(enabled = true)
        assertEquals(1, fetches)
        today = 101
        flow().autoCheckOnLaunch(enabled = true)
        assertEquals(2, fetches)
    }

    @Test
    fun autoCheckPromptsAndLaterSnoozesUntilNextDay() = runBlocking {
        val f = flow()
        f.autoCheckOnLaunch(enabled = true)
        val available = f.state.value as UpdateUiState.Available
        assertTrue(available.prompt)
        f.later()
        assertEquals(UpdateSnooze("1.1.0", 100), store.snoozed)
        assertFalse((f.state.value as UpdateUiState.Available).prompt)
        store.day = null
        val again = flow().also { it.autoCheckOnLaunch(enabled = true) }
        assertFalse((again.state.value as UpdateUiState.Available).prompt)
        today = 101
        val nextDay = flow().also { it.autoCheckOnLaunch(enabled = true) }
        assertTrue((nextDay.state.value as UpdateUiState.Available).prompt)
    }

    @Test
    fun autoCheckFailureStaysSilentAndRetriesOnNextLaunch() = runBlocking {
        response = { throw IOException("offline") }
        val failing = flow().also { it.autoCheckOnLaunch(enabled = true) }
        assertEquals(UpdateUiState.Idle, failing.state.value)
        assertEquals(1, fetches)
        assertEquals(null, store.day)
        response = { ReleaseResponse(503, "") }
        flow().autoCheckOnLaunch(enabled = true)
        assertEquals(2, fetches)
        assertEquals(null, store.day)
        response = { ReleaseResponse(200, ReleaseFixtures.list(ReleaseFixtures.release("v1.0.0"))) }
        val latest = flow().also { it.autoCheckOnLaunch(enabled = true) }
        assertEquals(UpdateUiState.Idle, latest.state.value)
        assertEquals(3, fetches)
        assertEquals(100L, store.day)
        flow().autoCheckOnLaunch(enabled = true)
        assertEquals(3, fetches)
    }

    @Test
    fun unknownCurrentVersionDoesNotCountAsSuccess() = runBlocking {
        val f = UpdateFlow(
            checker = UpdateChecker {
                ReleaseResponse(200, ReleaseFixtures.list(ReleaseFixtures.release("v1.1.0")))
            },
            downloader = { _, _ -> throw IOException() },
            verifier = { false },
            permission = { true },
            store = store,
            currentVersionName = { "" },
            today = { today },
        )
        f.autoCheckOnLaunch(enabled = true)
        assertEquals(null, store.day)
        assertEquals(UpdateUiState.Idle, f.state.value)
    }

    @Test
    fun manualCheckIgnoresSwitchAndThrottleAndShowsEveryOutcome() = runBlocking {
        store.day = today
        val f = flow()
        f.manualCheck()
        assertEquals(1, fetches)
        assertFalse((f.state.value as UpdateUiState.Available).prompt)
        response = { ReleaseResponse(200, ReleaseFixtures.list(ReleaseFixtures.release("v1.0.0"))) }
        f.manualCheck()
        assertEquals(UpdateUiState.UpToDate, f.state.value)
        response = { throw IOException("offline") }
        f.manualCheck()
        assertEquals(UpdateUiState.Failed(UpdateProblem.Check, RELEASES_PAGE_URL), f.state.value)
        response = { ReleaseResponse(500, "") }
        f.manualCheck()
        assertEquals(UpdateUiState.Failed(UpdateProblem.Check, RELEASES_PAGE_URL), f.state.value)
    }

    @Test
    fun downloadVerifiesAndHandsOffToInstaller() = runBlocking {
        val f = flow().also { it.manualCheck() }
        f.downloadAndInstall()
        assertEquals(listOf(0, 50, 100), progress)
        val ready = f.state.value as UpdateUiState.ReadyToInstall
        assertEquals("oot-v1.1.0.apk", ready.apk.name)
        f.installerLaunched(true)
        assertTrue(f.state.value is UpdateUiState.Available)
    }

    @Test
    fun mismatchedSignatureNeverReachesInstallerAndLinksReleasePage() = runBlocking {
        signatureOk = false
        val f = flow().also { it.manualCheck() }
        f.downloadAndInstall()
        val failed = f.state.value as UpdateUiState.Failed
        assertEquals(UpdateProblem.Verify, failed.problem)
        assertEquals("https://github.com/parkjangwon/oot/releases/tag/v1.1.0", failed.releasePageUrl)
        assertEquals("v1.1.0", failed.release?.tagName)
        assertFalse(File(tmp.root, "oot-v1.1.0.apk").exists())
    }

    @Test
    fun downloadFailureAndMissingApkLinkReleasePage() = runBlocking {
        downloadError = IOException("reset")
        val f = flow().also { it.manualCheck() }
        f.downloadAndInstall()
        assertEquals(UpdateProblem.Download, (f.state.value as UpdateUiState.Failed).problem)

        downloadError = null
        response = {
            ReleaseResponse(200, ReleaseFixtures.list(ReleaseFixtures.release("v1.1.0", assets = emptyList())))
        }
        val noApk = flow().also { it.manualCheck() }
        noApk.downloadAndInstall()
        val failed = noApk.state.value as UpdateUiState.Failed
        assertEquals(UpdateProblem.Download, failed.problem)
        assertEquals("https://github.com/parkjangwon/oot/releases/tag/v1.1.0", failed.releasePageUrl)
    }

    @Test
    fun missingInstallPermissionWaitsThenResumes() = runBlocking {
        canInstall = false
        val f = flow().also { it.manualCheck() }
        f.downloadAndInstall()
        assertTrue(f.state.value is UpdateUiState.NeedsInstallPermission)
        assertFalse(f.onResumed())
        assertTrue(f.state.value is UpdateUiState.NeedsInstallPermission)
        canInstall = true
        assertTrue(f.onResumed())
        f.downloadAndInstall()
        assertTrue(f.state.value is UpdateUiState.ReadyToInstall)
    }

    @Test
    fun installerLaunchFailureFallsBackToReleasePage() = runBlocking {
        val f = flow().also { it.manualCheck() }
        f.downloadAndInstall()
        f.installerLaunched(false)
        assertEquals(UpdateProblem.Install, (f.state.value as UpdateUiState.Failed).problem)
    }

    @Test
    fun releasePageFallsBackWhenHtmlUrlIsForeign() = runBlocking {
        signatureOk = false
        response = {
            ReleaseResponse(
                200,
                """[{"tag_name":"v1.1.0","html_url":"https://evil.example/x","assets":[{"name":"oot-v1.1.0.apk","browser_download_url":"https://github.com/parkjangwon/oot/releases/download/v1.1.0/oot-v1.1.0.apk","size":1}]}]""",
            )
        }
        val f = flow().also { it.manualCheck() }
        f.downloadAndInstall()
        val failed = f.state.value as UpdateUiState.Failed
        assertEquals(UpdateProblem.Verify, failed.problem)
        assertEquals(RELEASES_PAGE_URL, failed.releasePageUrl)
    }

    @Test
    fun promptFromAutoCheckIsClearedOnceInstallerOpens() = runBlocking {
        val f = flow().also { it.autoCheckOnLaunch(enabled = true) }
        assertTrue((f.state.value as UpdateUiState.Available).prompt)
        f.downloadAndInstall()
        f.installerLaunched(true)
        assertFalse((f.state.value as UpdateUiState.Available).prompt)
    }

    @Test
    fun autoCheckNeverOverridesAnExistingState() = runBlocking {
        val f = flow().also { it.manualCheck() }
        val before = f.state.value
        store.day = null
        today = 200
        f.autoCheckOnLaunch(enabled = true)
        assertEquals(before, f.state.value)
        assertEquals(1, fetches)
    }
}
