package org.parkjw.apps.oot.update

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class UpdateViewModel(application: Application) : AndroidViewModel(application) {
    private val versionName = application.installedVersionName()
    private val userAgent = "oot/$versionName (Android)"
    private val flow = UpdateFlow(
        checker = UpdateChecker(GitHubReleaseSource(userAgent)),
        downloader = FileApkDownloader(File(application.cacheDir, UPDATE_CACHE_DIR), HttpsApkTransport(userAgent)),
        verifier = PackageManagerApkVerifier(application),
        permission = InstallPermission { application.packageManager.canRequestPackageInstalls() },
        store = DataStoreUpdateStore(application),
        currentVersionName = { versionName },
        today = { LocalDate.now().toEpochDay() },
    )
    val state: StateFlow<UpdateUiState> = flow.state

    private fun run(block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) { block() }
    }

    fun autoCheckOnLaunch(enabled: Boolean) = run { flow.autoCheckOnLaunch(enabled) }

    fun manualCheck() = run { flow.manualCheck() }

    fun later() = run { flow.later() }

    fun downloadAndInstall() = run { flow.downloadAndInstall() }

    fun onResumed() = run { if (flow.onResumed()) flow.downloadAndInstall() }

    fun installerLaunched(launched: Boolean) = flow.installerLaunched(launched)
}
