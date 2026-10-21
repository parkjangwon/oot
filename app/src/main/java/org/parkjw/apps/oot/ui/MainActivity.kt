package org.parkjw.apps.oot.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.parkjw.apps.oot.OotApp
import org.parkjw.apps.oot.data.model.AuthType
import org.parkjw.apps.oot.data.model.ProviderAccount
import org.parkjw.apps.oot.service.TokenMonitorForegroundService
import org.parkjw.apps.oot.ui.screens.AddAccountDialog
import org.parkjw.apps.oot.ui.screens.DashboardScreen
import org.parkjw.apps.oot.ui.theme.OotTheme
import java.util.UUID

class MainActivity : ComponentActivity() {

    private val app by lazy { applicationContext as OotApp }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted && app.preferences.isServiceEnabled.value) {
            TokenMonitorForegroundService.startService(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkNotificationPermission()
        handleIncomingIntent(intent)

        setContent {
            OotTheme {
                val accounts by app.preferences.accounts.collectAsState()
                val isServiceEnabled by app.preferences.isServiceEnabled.collectAsState()
                val refreshInterval by app.preferences.refreshIntervalMinutes.collectAsState()
                val vendorUsages by app.repository.vendorUsages.collectAsState(initial = emptyList())
                val isRefreshing by app.repository.isRefreshing.collectAsState()

                var showAddDialog by remember { mutableStateOf(false) }

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
                            val updated = app.repository.refreshAll()
                            app.notificationManager.updateNotification(updated)
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
                            val updated = app.repository.refreshAll()
                            app.notificationManager.updateNotification(updated)
                        }
                    },
                    onToggleAccount = { accountId, isEnabled ->
                        app.preferences.toggleAccount(accountId, isEnabled)
                        lifecycleScope.launch {
                            val updated = app.repository.refreshAll()
                            app.notificationManager.updateNotification(updated)
                        }
                    }
                )

                if (showAddDialog) {
                    AddAccountDialog(
                        onDismiss = { showAddDialog = false },
                        onStartOAuth = { provider ->
                            app.oauthManager.startOAuthFlow(provider)
                        },
                        onOpenExternalBrowser = { url ->
                            app.oauthManager.openExternalBrowser(url)
                        },
                        onStartXaiDeviceFlow = { onCodeReceived, onTokenReceived, onError ->
                            app.oauthManager.startXaiDeviceFlow(
                                onCodeReceived = onCodeReceived,
                                onTokenReceived = onTokenReceived,
                                onError = onError
                            )
                        },
                        onStartAntigravityOAuth = { onTokenReceived, onError ->
                            app.oauthManager.startAntigravityOAuthFlow(
                                onTokenReceived = onTokenReceived,
                                onError = onError
                            )
                        },
                        onExchangeClaudeCode = { code, onTokenReceived, onError ->
                            lifecycleScope.launch {
                                val result = app.oauthManager.exchangeClaudeAuthCode(code)
                                result.onSuccess(onTokenReceived).onFailure { onError(it.message ?: "토큰 교환 실패") }
                            }
                        },
                        onExchangeAntigravityCode = { code, onTokenReceived, onError ->
                            lifecycleScope.launch {
                                val result = app.oauthManager.exchangeAntigravityCode(code)
                                result.onSuccess(onTokenReceived).onFailure { onError(it.message ?: "토큰 교환 실패") }
                            }
                        },
                        onSaveAccount = { newAccount ->
                            showAddDialog = false
                            app.preferences.saveAccount(newAccount)
                            lifecycleScope.launch {
                                val updated = app.repository.refreshAll()
                                app.notificationManager.updateNotification(updated)
                                Toast.makeText(this@MainActivity, "${newAccount.name} 계정이 등록되었습니다.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }
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
                val result = app.oauthManager.handleOAuthCallback(uri)
                result.onSuccess { tokenResult ->
                    val newAccount = ProviderAccount(
                        id = UUID.randomUUID().toString(),
                        provider = tokenResult.provider,
                        name = "${tokenResult.provider.displayName} (구독)",
                        authType = AuthType.OAUTH,
                        accessToken = tokenResult.accessToken,
                        refreshToken = tokenResult.refreshToken,
                        tokenExpiresAt = tokenResult.expiresAt
                    )
                    app.preferences.saveAccount(newAccount)
                    val updated = app.repository.refreshAll()
                    app.notificationManager.updateNotification(updated)
                    Toast.makeText(this@MainActivity, "${tokenResult.provider.displayName} OAuth 연동이 완료되었습니다!", Toast.LENGTH_LONG).show()
                }.onFailure { error ->
                    Toast.makeText(this@MainActivity, "인증 오류: ${error.message}", Toast.LENGTH_LONG).show()
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
