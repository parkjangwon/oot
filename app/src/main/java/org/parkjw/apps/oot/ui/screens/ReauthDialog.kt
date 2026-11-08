package org.parkjw.apps.oot.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import org.parkjw.apps.oot.R
import org.parkjw.apps.oot.data.model.AuthType
import org.parkjw.apps.oot.data.model.ProviderAccount
import org.parkjw.apps.oot.data.model.ProviderType
import org.parkjw.apps.oot.data.oauth.OAuthTokenResult
import org.parkjw.apps.oot.ui.theme.ootWordBreak
import org.parkjw.apps.oot.ui.theme.ButtonCorner
import org.parkjw.apps.oot.ui.theme.DialogCorner
import org.parkjw.apps.oot.ui.theme.ootInkButtonColors
import org.parkjw.apps.oot.ui.theme.ootTextButtonColors

sealed interface ReauthFlowType {
    data object ClaudeOAuth : ReauthFlowType
    data class DeviceFlow(val isXai: Boolean) : ReauthFlowType
    data object ApiKey : ReauthFlowType
}

fun resolveReauthFlowType(account: ProviderAccount): ReauthFlowType {
    return if (account.authType == AuthType.OAUTH) {
        when (account.provider) {
            ProviderType.CLAUDE -> ReauthFlowType.ClaudeOAuth
            ProviderType.XAI -> ReauthFlowType.DeviceFlow(isXai = true)
            ProviderType.OPENAI -> ReauthFlowType.DeviceFlow(isXai = false)
            else -> ReauthFlowType.ApiKey
        }
    } else {
        ReauthFlowType.ApiKey
    }
}

fun apiKeyPlaceholderRes(provider: ProviderType): Int = when (provider) {
    ProviderType.ZAI -> R.string.add_account_key_placeholder_zai
    ProviderType.MINIMAX -> R.string.add_account_key_placeholder_minimax
    ProviderType.OPENROUTER -> R.string.add_account_key_placeholder_openrouter
    ProviderType.CLAUDE -> R.string.add_account_key_placeholder_claude
    ProviderType.OPENAI -> R.string.add_account_key_placeholder_openai
    ProviderType.XAI -> R.string.add_account_key_placeholder_xai
    ProviderType.DEEPSEEK -> R.string.add_account_key_placeholder_deepseek
    ProviderType.OLLAMA_CLOUD -> R.string.add_account_key_placeholder_ollama_cloud
    ProviderType.OPENCODE_GO -> R.string.add_account_key_placeholder_opencode_go
}

@Composable
fun ReauthDialog(
    account: ProviderAccount,
    onDismiss: () -> Unit,
    onOpenExternalBrowser: (String) -> Unit = {},
    onStartOAuth: (ProviderType) -> Unit = {},
    onStartXaiDeviceFlow: (
        onCodeReceived: (userCode: String, verificationUrl: String) -> Unit,
        onTokenReceived: (OAuthTokenResult) -> Unit,
        onError: (String) -> Unit
    ) -> Unit = { _, _, _ -> },
    onStartOpenAiDeviceFlow: (
        onCodeReceived: (userCode: String, verificationUrl: String) -> Unit,
        onTokenReceived: (OAuthTokenResult) -> Unit,
        onError: (String) -> Unit
    ) -> Unit = { _, _, _ -> },
    onExchangeClaudeCode: (
        code: String,
        onTokenReceived: (OAuthTokenResult) -> Unit,
        onError: (String) -> Unit
    ) -> Unit = { _, _, _ -> },
    onCancelPendingFlows: () -> Unit = {},
    onSaveApiKey: (accountId: String, apiKey: String) -> Boolean = { _, _ -> false },
    onSaveTokens: (accountId: String, tokenResult: OAuthTokenResult) -> Boolean = { _, _ -> false },
    onSuccess: () -> Unit = {},
) {
    val flowType = remember(account) { resolveReauthFlowType(account) }
    val clipboardManager = LocalClipboardManager.current

    // Claude state
    var claudeAuthCode by remember { mutableStateOf("") }
    var isClaudeExchanging by remember { mutableStateOf(false) }
    var claudeErrorMessage by remember { mutableStateOf<String?>(null) }

    // Device Flow state (OpenAI / xAI)
    var isDeviceWaiting by remember { mutableStateOf(false) }
    var deviceUserCode by remember { mutableStateOf<String?>(null) }
    var deviceVerificationUrl by remember { mutableStateOf<String?>(null) }
    var deviceErrorMessage by remember { mutableStateOf<String?>(null) }

    // API Key state
    var apiKeyInput by remember { mutableStateOf("") }
    var showApiKey by remember { mutableStateOf(false) }
    var isApiKeySaving by remember { mutableStateOf(false) }
    var apiKeyErrorMessage by remember { mutableStateOf<String?>(null) }

    val titleRes = when (flowType) {
        ReauthFlowType.ClaudeOAuth -> R.string.reauth_title_claude
        is ReauthFlowType.DeviceFlow -> if (flowType.isXai) R.string.reauth_title_xai else R.string.reauth_title_openai
        ReauthFlowType.ApiKey -> R.string.reauth_title_api_key
    }

    val bodyRes = when (flowType) {
        ReauthFlowType.ClaudeOAuth -> R.string.reauth_body_claude
        is ReauthFlowType.DeviceFlow -> R.string.reauth_body_device
        ReauthFlowType.ApiKey -> R.string.reauth_body_api_key
    }

    val isBusy = isClaudeExchanging || isApiKeySaving

    fun startDeviceFlow() {
        isDeviceWaiting = true
        deviceErrorMessage = null
        deviceUserCode = null
        deviceVerificationUrl = null

        val onCode = { code: String, url: String ->
            deviceUserCode = code
            deviceVerificationUrl = url
        }
        val onToken = { tokenResult: OAuthTokenResult ->
            isDeviceWaiting = false
            val saved = onSaveTokens(account.id, tokenResult)
            if (saved) {
                onSuccess()
            } else {
                deviceErrorMessage = ""
            }
        }
        val onError = { err: String ->
            isDeviceWaiting = false
            deviceErrorMessage = err
        }

        if (flowType is ReauthFlowType.DeviceFlow) {
            if (flowType.isXai) {
                onStartXaiDeviceFlow(onCode, onToken, onError)
            } else {
                onStartOpenAiDeviceFlow(onCode, onToken, onError)
            }
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (!isBusy) {
                if (isDeviceWaiting) onCancelPendingFlows()
                onDismiss()
            }
        },
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        shape = DialogCorner,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(bodyRes),
                    style = MaterialTheme.typography.bodySmall.ootWordBreak(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                when (flowType) {
                    ReauthFlowType.ClaudeOAuth -> {
                        Button(
                            onClick = {
                                claudeErrorMessage = null
                                onStartOAuth(ProviderType.CLAUDE)
                            },
                            enabled = !isClaudeExchanging,
                            shape = ButtonCorner,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Filled.OpenInBrowser, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.add_account_claude_login_browser),
                                style = LocalTextStyle.current.ootWordBreak(),
                            )
                        }

                        OutlinedTextField(
                            value = claudeAuthCode,
                            onValueChange = {
                                claudeAuthCode = it
                                if (claudeErrorMessage != null) claudeErrorMessage = null
                            },
                            enabled = !isClaudeExchanging,
                            label = { Text(stringResource(R.string.add_account_claude_code_label), style = LocalTextStyle.current.ootWordBreak()) },
                            placeholder = { Text(stringResource(R.string.add_account_claude_code_placeholder), style = LocalTextStyle.current.ootWordBreak()) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                autoCorrectEnabled = false,
                            ),
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        val text = clipboardManager.getText()?.text
                                        if (!text.isNullOrBlank()) {
                                            claudeAuthCode = text.trim()
                                            if (claudeErrorMessage != null) claudeErrorMessage = null
                                        }
                                    },
                                    enabled = !isClaudeExchanging,
                                ) {
                                    Icon(Icons.Filled.ContentPaste, contentDescription = stringResource(R.string.add_account_paste))
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )

                        if (isClaudeExchanging) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.add_account_exchanging),
                                    style = MaterialTheme.typography.bodySmall.ootWordBreak(),
                                )
                            }
                        }

                        claudeErrorMessage?.let { err ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = stringResource(R.string.reauth_failed_kept),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall.ootWordBreak(),
                                    fontWeight = FontWeight.SemiBold,
                                )
                                if (err.isNotBlank()) {
                                    Text(
                                        text = err,
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall.ootWordBreak(),
                                    )
                                }
                            }
                        }
                    }

                    is ReauthFlowType.DeviceFlow -> {
                        if (!isDeviceWaiting) {
                            val buttonTextRes = if (flowType.isXai) R.string.add_account_xai_start else R.string.add_account_openai_signin
                            Button(
                                onClick = { startDeviceFlow() },
                                shape = ButtonCorner,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Filled.VpnKey, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(buttonTextRes), style = LocalTextStyle.current.ootWordBreak())
                            }
                        } else {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                                deviceUserCode?.let { code ->
                                    Text(
                                        text = stringResource(R.string.add_account_auth_code_value, code),
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium.ootWordBreak(),
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        text = stringResource(R.string.add_account_code_copied),
                                        style = MaterialTheme.typography.labelSmall.ootWordBreak(),
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.add_account_openai_waiting),
                                    style = MaterialTheme.typography.bodySmall.ootWordBreak(),
                                )
                                deviceVerificationUrl?.let { url ->
                                    TextButton(onClick = { onOpenExternalBrowser(url) }) {
                                        Text(stringResource(R.string.add_account_reopen_browser), style = LocalTextStyle.current.ootWordBreak())
                                    }
                                }
                            }
                        }

                        deviceErrorMessage?.let { err ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = stringResource(R.string.reauth_failed_kept),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall.ootWordBreak(),
                                    fontWeight = FontWeight.SemiBold,
                                )
                                if (err.isNotBlank()) {
                                    Text(
                                        text = err,
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall.ootWordBreak(),
                                    )
                                }
                            }
                        }
                    }

                    ReauthFlowType.ApiKey -> {
                        OutlinedTextField(
                            value = apiKeyInput,
                            onValueChange = {
                                apiKeyInput = it
                                if (apiKeyErrorMessage != null) apiKeyErrorMessage = null
                            },
                            enabled = !isApiKeySaving,
                            label = { Text(stringResource(R.string.reauth_key_label), style = LocalTextStyle.current.ootWordBreak()) },
                            placeholder = {
                                Text(
                                    stringResource(apiKeyPlaceholderRes(account.provider)),
                                    style = LocalTextStyle.current.ootWordBreak(),
                                )
                            },
                            visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                autoCorrectEnabled = false,
                            ),
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { showApiKey = !showApiKey },
                                        enabled = !isApiKeySaving,
                                    ) {
                                        Icon(
                                            imageVector = if (showApiKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                            contentDescription = stringResource(
                                                if (showApiKey) R.string.add_account_hide_key else R.string.add_account_show_key,
                                            ),
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            val text = clipboardManager.getText()?.text
                                            if (!text.isNullOrBlank()) {
                                                apiKeyInput = text.trim()
                                                if (apiKeyErrorMessage != null) apiKeyErrorMessage = null
                                            }
                                        },
                                        enabled = !isApiKeySaving,
                                    ) {
                                        Icon(Icons.Filled.ContentPaste, contentDescription = stringResource(R.string.add_account_paste))
                                    }
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        if (isApiKeySaving) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.reauth_saving),
                                    style = MaterialTheme.typography.bodySmall.ootWordBreak(),
                                )
                            }
                        }

                        apiKeyErrorMessage?.let { err ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = stringResource(R.string.reauth_failed_kept),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall.ootWordBreak(),
                                    fontWeight = FontWeight.SemiBold,
                                )
                                if (err.isNotBlank()) {
                                    Text(
                                        text = err,
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall.ootWordBreak(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (flowType) {
                ReauthFlowType.ClaudeOAuth -> {
                    Button(
                        onClick = {
                            isClaudeExchanging = true
                            claudeErrorMessage = null
                            onExchangeClaudeCode(
                                claudeAuthCode.trim(),
                                { tokenResult ->
                                    isClaudeExchanging = false
                                    val saved = onSaveTokens(account.id, tokenResult)
                                    if (saved) {
                                        onSuccess()
                                    } else {
                                        claudeErrorMessage = ""
                                    }
                                },
                                { err ->
                                    isClaudeExchanging = false
                                    claudeErrorMessage = err
                                },
                            )
                        },
                        enabled = claudeAuthCode.isNotBlank() && !isClaudeExchanging,
                        shape = ButtonCorner,
                        colors = ootInkButtonColors(),
                    ) {
                        Text(
                            stringResource(if (claudeErrorMessage != null) R.string.reauth_retry else R.string.add_account_save),
                            style = LocalTextStyle.current.ootWordBreak(),
                        )
                    }
                }

                is ReauthFlowType.DeviceFlow -> {
                    if (deviceErrorMessage != null && !isDeviceWaiting) {
                        Button(
                            onClick = { startDeviceFlow() },
                            shape = ButtonCorner,
                            colors = ootInkButtonColors(),
                        ) {
                            Text(stringResource(R.string.reauth_retry), style = LocalTextStyle.current.ootWordBreak())
                        }
                    }
                }

                ReauthFlowType.ApiKey -> {
                    Button(
                        onClick = {
                            isApiKeySaving = true
                            apiKeyErrorMessage = null
                            val saved = onSaveApiKey(account.id, apiKeyInput.trim())
                            isApiKeySaving = false
                            if (saved) {
                                onSuccess()
                            } else {
                                apiKeyErrorMessage = ""
                            }
                        },
                        enabled = apiKeyInput.isNotBlank() && !isApiKeySaving,
                        shape = ButtonCorner,
                        colors = ootInkButtonColors(),
                    ) {
                        Text(
                            stringResource(if (apiKeyErrorMessage != null) R.string.reauth_retry else R.string.add_account_save),
                            style = LocalTextStyle.current.ootWordBreak(),
                        )
                    }
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    if (isDeviceWaiting) onCancelPendingFlows()
                    onDismiss()
                },
                enabled = !isBusy,
                colors = ootTextButtonColors(),
            ) {
                Text(stringResource(R.string.add_account_cancel))
            }
        },
    )
}
