package org.parkjw.apps.oot.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.parkjw.apps.oot.OotApp
import org.parkjw.apps.oot.R
import org.parkjw.apps.oot.i18n.AppLanguage
import org.parkjw.apps.oot.i18n.AppLocale
import org.parkjw.apps.oot.data.model.AuthType
import org.parkjw.apps.oot.data.model.ProviderAccount
import org.parkjw.apps.oot.service.TokenMonitorForegroundService
import org.parkjw.apps.oot.ui.screens.AddAccountDialog
import org.parkjw.apps.oot.ui.screens.DashboardScreen
import org.parkjw.apps.oot.ui.screens.SettingsScreen
import org.parkjw.apps.oot.ui.screens.SOURCE_CODE_URL
import org.parkjw.apps.oot.ui.theme.OotTheme
import org.parkjw.apps.oot.update.RELEASES_PAGE_URL
import org.parkjw.apps.oot.update.UpdateAction
import org.parkjw.apps.oot.update.UpdateUiState
import org.parkjw.apps.oot.update.UpdateViewModel
import org.parkjw.apps.oot.update.installedVersionName
import org.parkjw.apps.oot.update.launchApkInstaller
import org.parkjw.apps.oot.update.openBrowsableUrl
import org.parkjw.apps.oot.update.openInstallPermissionSettings
import org.parkjw.apps.oot.update.openReleasePage
import java.util.UUID

class MainActivity : ComponentActivity() {

    private val app by lazy { applicationContext as OotApp }
    private val updateViewModel: UpdateViewModel by viewModels()
    internal var exitBackPressPolicy = ExitBackPressPolicy()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted && app.preferences.isServiceEnabled.value) {
            TokenMonitorForegroundService.startService(this)
        }
    }

    // The UI follows the language chosen in Settings (Korean by default), not the device locale.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        applyOverrideConfiguration(AppLocale.overrideConfiguration())
    }

    private fun changeLanguage(language: AppLanguage) {
        if (language == AppLocale.current) return
        app.setLanguage(language)
        // Android 13+ recreates the activity itself once LocaleManager gets the new locale.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) recreate()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkNotificationPermission()
        handleIncomingIntent(intent)
        updateViewModel.autoCheckOnLaunch(app.preferences.isAutoUpdateCheckEnabled.value)

        setContent {
            OotTheme {
                val accounts by app.preferences.accounts.collectAsState()
                val isServiceEnabled by app.preferences.isServiceEnabled.collectAsState()
                val refreshInterval by app.preferences.refreshIntervalMinutes.collectAsState()
                val autoUpdateCheck by app.preferences.isAutoUpdateCheckEnabled.collectAsState()
                val notificationHiddenIds by app.preferences.notificationHiddenAccountIds.collectAsState()
                val vendorUsages by app.repository.vendorUsages.collectAsState(initial = emptyList())
                val isRefreshing by app.repository.isRefreshing.collectAsState()

                var showAddDialog by remember { mutableStateOf(false) }
                var resetCount by remember { mutableStateOf(0) }
                // Saveable so a language change (which recreates the activity) stays on Settings.
                var showSettings by rememberSaveable { mutableStateOf(false) }
                val context = LocalContext.current
                val versionName = remember { context.installedVersionName() }
                val updateState by updateViewModel.state.collectAsState()
                val snackbarHostState = remember { SnackbarHostState() }
                var secureStorageAvailable by remember {
                    mutableStateOf(app.preferences.isSecureStorageAvailable)
                }
                var resetInProgress by remember { mutableStateOf(false) }

                LaunchedEffect(updateState) {
                    val ready = updateState as? UpdateUiState.ReadyToInstall ?: return@LaunchedEffect
                    updateViewModel.installerLaunched(context.launchApkInstaller(ready.apk))
                }

                if (showSettings) {
                    SettingsScreen(
                        isServiceEnabled = isServiceEnabled,
                        refreshIntervalMinutes = refreshInterval,
                        accounts = accounts.filter { it.provider.isSelectable },
                        notificationHiddenIds = notificationHiddenIds,
                        autoUpdateCheckEnabled = autoUpdateCheck,
                        language = AppLocale.current,
                        versionName = versionName,
                        updateState = updateState,
                        onBack = { showSettings = false },
                        onToggleService = { enabled ->
                            app.preferences.setServiceEnabled(enabled)
                            if (enabled) {
                                TokenMonitorForegroundService.startService(this@MainActivity)
                            } else {
                                TokenMonitorForegroundService.stopService(this@MainActivity)
                            }
                        },
                        onSetRefreshInterval = { minutes -> app.preferences.setRefreshInterval(minutes) },
                        onNotificationShownChange = { accountId, shown ->
                            app.preferences.setShownInNotification(accountId, shown)
                        },
                        onAutoUpdateCheckChange = { enabled -> app.preferences.setAutoUpdateCheckEnabled(enabled) },
                        onLanguageChange = ::changeLanguage,
                        onUpdateAction = { action ->
                            when (action) {
                                UpdateAction.Check -> updateViewModel.manualCheck()
                                UpdateAction.Download -> updateViewModel.downloadAndInstall()
                                UpdateAction.Later -> updateViewModel.later()
                                UpdateAction.OpenSettings -> context.openInstallPermissionSettings()
                                UpdateAction.OpenReleasePage -> context.openReleasePage(
                                    (updateState as? UpdateUiState.Failed)?.releasePageUrl ?: RELEASES_PAGE_URL,
                                )
                            }
                        },
                        onOpenSourceCode = { context.openBrowsableUrl(SOURCE_CODE_URL) },
                    )
                } else {
                    BackHandler {
                        if (exitBackPressPolicy.onBackPressed()) {
                            finish()
                        } else {
                            Toast.makeText(
                                this@MainActivity,
                                getString(ExitBackPressPolicy.EXIT_PROMPT_MESSAGE),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }

                    val selectableAccounts = accounts.filter { it.provider.isSelectable }
                    val legacyAccounts = accounts.filter { !it.provider.isSelectable }
                    val displayUsages = selectableAccounts.map { account ->
                        vendorUsages.find { it.accountId == account.id } ?: org.parkjw.apps.oot.data.model.VendorUsage(
                            accountId = account.id,
                            providerType = account.provider,
                            displayName = account.name,
                            windows = emptyList()
                        )
                    }

                    DashboardScreen(
                        vendorUsages = displayUsages,
                        legacyAccounts = legacyAccounts,
                        isRefreshing = isRefreshing,
                        isServiceEnabled = isServiceEnabled,
                        refreshIntervalMinutes = refreshInterval,
                        onRefresh = {
                            lifecycleScope.launch {
                                app.repository.refreshAll()
                                app.notificationManager.updateNotification(app.repository.vendorUsages.value)
                            }
                        },
                        onToggleService = { enabled ->
                            app.preferences.setServiceEnabled(enabled)
                            if (enabled) {
                                TokenMonitorForegroundService.startService(this)
                            } else {
                                TokenMonitorForegroundService.stopService(this)
                            }
                        },
                        onSetRefreshInterval = { minutes ->
                            app.preferences.setRefreshInterval(minutes)
                        },
                        onAddAccountClick = {
                            showAddDialog = true
                        },
                        onDeleteAccount = { accountId ->
                            app.preferences.deleteAccount(accountId)
                            lifecycleScope.launch {
                                app.repository.refreshAll()
                                app.notificationManager.updateNotification(app.repository.vendorUsages.value)
                            }
                        },
                        onToggleAccount = { accountId, isEnabled ->
                            app.preferences.toggleAccount(accountId, isEnabled)
                            lifecycleScope.launch {
                                app.repository.refreshAll()
                                app.notificationManager.updateNotification(app.repository.vendorUsages.value)
                            }
                        },
                        onReorderVendors = { accountIds ->
                            app.repository.applyAccountOrder(accountIds)
                            app.notificationManager.updateNotification(app.repository.vendorUsages.value)
                        },
                        onSettingsClick = { showSettings = true },
                        isSecureStorageAvailable = secureStorageAvailable,
                        isResettingSecureStorage = resetInProgress,
                        snackbarHostState = snackbarHostState,
                        onResetSecureStorage = {
                            if (!resetInProgress) {
                                resetInProgress = true
                                lifecycleScope.launch {
                                    try {
                                        // Close the sign-in dialog: its pending callbacks are dropped by the reset.
                                        showAddDialog = false
                                        app.oauthManager.cancelPendingFlows()
                                        app.repository.onAccountsReset()
                                        val reset = withContext(Dispatchers.IO) {
                                            app.preferences.resetSecureStorage()
                                        }
                                        // Again after the reset: a refresh started in the gap read the old accounts.
                                        app.repository.onAccountsReset()
                                        secureStorageAvailable = app.preferences.isSecureStorageAvailable
                                        resetCount++
                                        if (reset) showAddDialog = true
                                        app.repository.refreshAll()
                                        app.notificationManager.updateNotification(app.repository.vendorUsages.value)
                                        snackbarHostState.showSnackbar(
                                            getString(
                                                if (reset) R.string.secure_storage_reset_done
                                                else R.string.secure_storage_reset_failed,
                                            ),
                                        )
                                    } finally {
                                        resetInProgress = false
                                    }
                                }
                            }
                        },
                    )
                }

                // Keyed on the reset count so a dialog reopened after a reset never keeps pre-reset state.
                if (showAddDialog) key(resetCount) {
                    AddAccountDialog(
                        onDismiss = { showAddDialog = false },
                        onStartOAuth = { provider ->
                            app.oauthManager.startOAuthFlow(provider)
                        },
                        onOpenExternalBrowser = { url ->
                            app.oauthManager.openExternalBrowser(url)
                        },
                        onStartXaiDeviceFlow = { onCodeReceived, onTokenReceived, onError ->
                            val generation = app.preferences.resetGeneration()
                            app.oauthManager.startXaiDeviceFlow(
                                onCodeReceived = onCodeReceived,
                                onTokenReceived = { result ->
                                    if (generation == app.preferences.resetGeneration()) onTokenReceived(result)
                                },
                                onError = onError
                            )
                        },
                        onStartOpenAiDeviceFlow = { onCodeReceived, onTokenReceived, onError ->
                            val generation = app.preferences.resetGeneration()
                            app.oauthManager.startOpenAiDeviceFlow(
                                onCodeReceived = onCodeReceived,
                                onTokenReceived = { result ->
                                    if (generation == app.preferences.resetGeneration()) onTokenReceived(result)
                                },
                                onError = onError
                            )
                        },
                        onExchangeClaudeCode = { code, onTokenReceived, onError ->
                            lifecycleScope.launch {
                                val generation = app.preferences.resetGeneration()
                                val result = app.oauthManager.exchangeClaudeAuthCode(code)
                                if (generation == app.preferences.resetGeneration()) {
                                    result.onSuccess(onTokenReceived).onFailure { onError(it.message ?: getString(R.string.token_exchange_failed)) }
                                }
                            }
                        },
                        onSaveAccount = { newAccount ->
                            showAddDialog = false
                            app.preferences.saveAccount(newAccount)
                            lifecycleScope.launch {
                                app.repository.refreshAll()
                                app.notificationManager.updateNotification(app.repository.vendorUsages.value)
                                Toast.makeText(
                                    this@MainActivity,
                                    getString(R.string.toast_provider_added, newAccount.name),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateViewModel.onResumed()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val uri: Uri = intent?.data ?: return
        if (uri.scheme == "oot" && uri.host == "oauth") {
            lifecycleScope.launch {
                val generation = app.preferences.resetGeneration()
                val result = app.oauthManager.handleOAuthCallback(uri)
                result.onSuccess { tokenResult ->
                    val newAccount = ProviderAccount(
                        id = UUID.randomUUID().toString(),
                        provider = tokenResult.provider,
                        name = getString(R.string.account_name_subscription, tokenResult.provider.displayName),
                        authType = AuthType.OAUTH,
                        accessToken = tokenResult.accessToken,
                        refreshToken = tokenResult.refreshToken,
                        tokenExpiresAt = tokenResult.expiresAt
                    )
                    // A reset while the callback was in flight cleared the accounts: do not put this one back.
                    if (!app.preferences.saveAccountIfCurrent(newAccount, generation)) return@onSuccess
                    app.repository.refreshAll()
                    app.notificationManager.updateNotification(app.repository.vendorUsages.value)
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.toast_oauth_connected, tokenResult.provider.displayName),
                        Toast.LENGTH_LONG,
                    ).show()
                }.onFailure { error ->
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.toast_auth_error, error.message.orEmpty()),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionStatus = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            )
            if (permissionStatus != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
