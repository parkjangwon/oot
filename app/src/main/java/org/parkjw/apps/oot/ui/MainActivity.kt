package org.parkjw.apps.oot.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.parkjw.apps.oot.OotApp
import org.parkjw.apps.oot.service.TokenMonitorForegroundService
import org.parkjw.apps.oot.ui.screens.AddAccountDialog
import org.parkjw.apps.oot.ui.screens.DashboardScreen
import org.parkjw.apps.oot.ui.theme.OotTheme

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

        setContent {
            OotTheme {
                val accounts by app.preferences.accounts.collectAsState()
                val isServiceEnabled by app.preferences.isServiceEnabled.collectAsState()
                val refreshInterval by app.preferences.refreshIntervalMinutes.collectAsState()
                val statuses by app.repository.statuses.collectAsState()
                val isRefreshing by app.repository.isRefreshing.collectAsState()

                var showAddDialog by remember { mutableStateOf(false) }

                DashboardScreen(
                    accounts = accounts,
                    statuses = statuses,
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
                        onSave = { newAccount ->
                            showAddDialog = false
                            app.preferences.saveAccount(newAccount)
                            lifecycleScope.launch {
                                val updated = app.repository.refreshAll()
                                app.notificationManager.updateNotification(updated)
                            }
                        }
                    )
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
