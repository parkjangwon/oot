package org.parkjw.apps.oot.update

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.parkjw.apps.oot.R

enum class UpdateAction(@StringRes val label: Int) {
    Check(R.string.update_ok),
    Download(R.string.update_download),
    Later(R.string.update_later),
    OpenSettings(R.string.update_open_settings),
    OpenReleasePage(R.string.update_open_release),
}

sealed interface UpdateText {
    data class Res(@StringRes val res: Int, val arg: Any? = null) : UpdateText
    data class WithCurrentVersion(@StringRes val res: Int?) : UpdateText
    data object CurrentVersionLatest : UpdateText
}

enum class UpdateTrailing {
    None,
    Spinner,
    Check,
    Download,
}

data class UpdateRow(
    val title: UpdateText,
    val subtitle: UpdateText?,
    val trailing: UpdateTrailing = UpdateTrailing.None,
    val progress: Float? = null,
    val hint: UpdateText? = null,
    val bottomAction: UpdateAction? = null,
)

fun displayVersion(release: Release): String = "v" + (release.version?.toString() ?: release.tagName.removePrefix("v"))

fun displayVersion(versionName: String): String = if (versionName.isEmpty()) "" else "v" + versionName.removePrefix("v")

private val CheckTitle = UpdateText.Res(R.string.update_check)

private fun availableTitle(release: Release) = UpdateText.Res(R.string.update_available, displayVersion(release))

fun updateRow(state: UpdateUiState): UpdateRow = when (state) {
    UpdateUiState.Idle -> UpdateRow(CheckTitle, UpdateText.WithCurrentVersion(null), UpdateTrailing.Check)
    UpdateUiState.Checking -> UpdateRow(CheckTitle, UpdateText.Res(R.string.update_checking), UpdateTrailing.Spinner)
    UpdateUiState.UpToDate -> UpdateRow(CheckTitle, UpdateText.CurrentVersionLatest, UpdateTrailing.Check)
    is UpdateUiState.Available -> UpdateRow(
        availableTitle(state.release),
        UpdateText.WithCurrentVersion(R.string.update_current_version),
        UpdateTrailing.Download,
    )
    is UpdateUiState.NeedsInstallPermission -> UpdateRow(
        availableTitle(state.release),
        UpdateText.Res(R.string.update_need_permission),
        bottomAction = UpdateAction.OpenSettings,
    )
    is UpdateUiState.Downloading -> downloadingRow(state.percent)
    is UpdateUiState.ReadyToInstall -> downloadingRow(100)
    is UpdateUiState.Failed -> when (state.problem) {
        UpdateProblem.Check -> UpdateRow(CheckTitle, UpdateText.Res(R.string.update_check_failed), UpdateTrailing.Check)
        else -> UpdateRow(
            state.release?.let(::availableTitle) ?: CheckTitle,
            UpdateText.Res(
                when (state.problem) {
                    UpdateProblem.Download -> R.string.update_download_failed
                    UpdateProblem.Verify -> R.string.update_verify_failed
                    UpdateProblem.Install, UpdateProblem.Check -> R.string.update_install_failed
                },
            ),
            bottomAction = UpdateAction.OpenReleasePage,
        )
    }
}

private fun downloadingRow(percent: Int) = UpdateRow(
    title = UpdateText.Res(R.string.update_downloading, percent),
    subtitle = null,
    progress = percent / 100f,
    hint = UpdateText.Res(R.string.update_download_hint),
)

@Composable
fun UpdateText.render(currentVersion: String): String = when (this) {
    is UpdateText.Res -> if (arg == null) stringResource(res) else stringResource(res, arg)
    is UpdateText.WithCurrentVersion ->
        if (res == null) displayVersion(currentVersion) else stringResource(res, displayVersion(currentVersion))
    UpdateText.CurrentVersionLatest -> listOf(displayVersion(currentVersion), stringResource(R.string.update_latest))
        .filter { it.isNotEmpty() }
        .joinToString(" · ")
}
