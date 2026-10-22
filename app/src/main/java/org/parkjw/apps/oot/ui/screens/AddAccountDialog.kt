package org.parkjw.apps.oot.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import org.parkjw.apps.oot.ui.theme.ButtonCorner
import org.parkjw.apps.oot.ui.theme.DialogCorner
import org.parkjw.apps.oot.ui.theme.ootInkButtonColors
import org.parkjw.apps.oot.ui.theme.ootOutlinedButtonColors
import org.parkjw.apps.oot.ui.theme.ootTextButtonColors
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAccountDialog(
    onDismiss: () -> Unit,
    onStartOAuth: (ProviderType) -> Unit,
    onOpenExternalBrowser: (String) -> Unit,
    onStartXaiDeviceFlow: (
        onCodeReceived: (userCode: String, verificationUrl: String) -> Unit,
        onTokenReceived: (OAuthTokenResult) -> Unit,
        onError: (String) -> Unit
    ) -> Unit,
    onExchangeClaudeCode: (
        code: String,
        onTokenReceived: (OAuthTokenResult) -> Unit,
        onError: (String) -> Unit
    ) -> Unit,
    onStartOpenAiDeviceFlow: (
        onCodeReceived: (userCode: String, verificationUrl: String) -> Unit,
        onTokenReceived: (OAuthTokenResult) -> Unit,
        onError: (String) -> Unit
    ) -> Unit,
    onSaveAccount: (ProviderAccount) -> Unit
) {
    var selectedProvider by remember { mutableStateOf(ProviderType.ZAI) }
    var accountName by remember { mutableStateOf(ProviderType.ZAI.displayName) }
    var apiKey by remember { mutableStateOf("") }
    var customEndpoint by remember { mutableStateOf("") }
    var showApiKey by remember { mutableStateOf(false) }
    var isProviderDropdownExpanded by remember { mutableStateOf(false) }

    // xAI Device Flow state
    var isXaiWaiting by remember { mutableStateOf(false) }
    var xaiUserCode by remember { mutableStateOf<String?>(null) }
    var xaiVerificationUrl by remember { mutableStateOf<String?>(null) }
    var xaiErrorMessage by remember { mutableStateOf<String?>(null) }

    // OpenAI ChatGPT Device Flow state
    var isOpenAiWaiting by remember { mutableStateOf(false) }
    var openAiUserCode by remember { mutableStateOf<String?>(null) }
    var openAiVerificationUrl by remember { mutableStateOf<String?>(null) }
    var openAiErrorMessage by remember { mutableStateOf<String?>(null) }

    // Claude OAuth Code exchange state
    var claudeAuthCode by remember { mutableStateOf("") }
    var isClaudeExchanging by remember { mutableStateOf(false) }
    var claudeErrorMessage by remember { mutableStateOf<String?>(null) }
    var showClaudeCodeInput by remember { mutableStateOf(false) }

    val clipboardManager = LocalClipboardManager.current
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        shape = DialogCorner,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = {
            Text(
                text = stringResource(R.string.add_account_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Provider Selection Dropdown
                ExposedDropdownMenuBox(
                    expanded = isProviderDropdownExpanded,
                    onExpandedChange = { isProviderDropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedProvider.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.add_account_provider_label)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isProviderDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = isProviderDropdownExpanded,
                        onDismissRequest = { isProviderDropdownExpanded = false }
                    ) {
                        ProviderType.entries.filter { it.isSelectable }.forEach { provider ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(provider.displayName, fontWeight = FontWeight.SemiBold)
                                        Text(stringResource(provider.descriptionRes), style = MaterialTheme.typography.labelSmall)
                                    }
                                },
                                onClick = {
                                    selectedProvider = provider
                                    accountName = provider.displayName
                                    customEndpoint = ""
                                    isXaiWaiting = false
                                    xaiUserCode = null
                                    xaiErrorMessage = null
                                    showClaudeCodeInput = false
                                    claudeErrorMessage = null
                                    isProviderDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // Account Name / Label
                OutlinedTextField(
                    value = accountName,
                    onValueChange = { accountName = it },
                    label = { Text(stringResource(R.string.add_account_display_name_label)) },
                    placeholder = { Text(selectedProvider.displayName) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Quick Link to Provider Key / Dashboard
                OutlinedButton(
                    onClick = { onOpenExternalBrowser(selectedProvider.dashboardUrl) },
                    shape = ButtonCorner,
                    colors = ootOutlinedButtonColors(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.OpenInBrowser, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        when (selectedProvider) {
                            ProviderType.ZAI -> stringResource(R.string.add_account_open_zai)
                            ProviderType.OPENROUTER -> stringResource(R.string.add_account_open_openrouter)
                            ProviderType.CLAUDE -> stringResource(R.string.add_account_open_claude)
                            ProviderType.OPENAI -> stringResource(R.string.add_account_open_openai)
                            ProviderType.XAI -> stringResource(R.string.add_account_open_xai)
                            ProviderType.DEEPSEEK -> stringResource(R.string.add_account_open_deepseek)
                            ProviderType.OLLAMA_CLOUD -> stringResource(R.string.add_account_open_ollama_cloud)
                            ProviderType.OPENCODE_GO -> stringResource(R.string.add_account_open_opencode_go)
                        }
                    )
                }

                // Provider-Specific OAuth / Device Flow Sections
                if (selectedProvider == ProviderType.XAI) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.add_account_xai_title),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = stringResource(R.string.add_account_xai_desc),
                                style = MaterialTheme.typography.bodySmall
                            )

                            if (!isXaiWaiting) {
                                Button(
                                    onClick = {
                                        isXaiWaiting = true
                                        xaiErrorMessage = null
                                        onStartXaiDeviceFlow(
                                            { code, url ->
                                                xaiUserCode = code
                                                xaiVerificationUrl = url
                                            },
                                            { tokenResult ->
                                                val newAccount = ProviderAccount(
                                                    id = UUID.randomUUID().toString(),
                                                    provider = ProviderType.XAI,
                                                    name = accountName.ifBlank { "xAI (Grok)" },
                                                    authType = AuthType.OAUTH,
                                                    accessToken = tokenResult.accessToken,
                                                    refreshToken = tokenResult.refreshToken,
                                                    tokenExpiresAt = tokenResult.expiresAt
                                                )
                                                onSaveAccount(newAccount)
                                            },
                                            { errorMsg ->
                                                xaiErrorMessage = errorMsg
                                                isXaiWaiting = false
                                            }
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Filled.VpnKey, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.add_account_xai_start))
                                }
                            } else {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                    if (xaiUserCode != null) {
                                        val code = xaiUserCode ?: ""
                                        Text(
                                            text = stringResource(R.string.add_account_auth_code_value, code),
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = stringResource(R.string.add_account_code_copied),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                    Text(
                                        text = stringResource(R.string.add_account_xai_waiting),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    xaiVerificationUrl?.let { url ->
                                        TextButton(onClick = { onOpenExternalBrowser(url) }) {
                                            Text(stringResource(R.string.add_account_reopen_browser))
                                        }
                                    }
                                }
                            }

                            xaiErrorMessage?.let {
                                Text(
                                    text = it,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }

                if (selectedProvider == ProviderType.OPENAI) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.add_account_openai_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.add_account_openai_desc),
                                style = MaterialTheme.typography.bodySmall
                            )

                            if (!isOpenAiWaiting) {
                                Button(
                                    onClick = {
                                        isOpenAiWaiting = true
                                        openAiErrorMessage = null
                                        onStartOpenAiDeviceFlow(
                                            { code, url ->
                                                openAiUserCode = code
                                                openAiVerificationUrl = url
                                            },
                                            { tokenResult ->
                                                val newAccount = ProviderAccount(
                                                    id = UUID.randomUUID().toString(),
                                                    provider = ProviderType.OPENAI,
                                                    name = accountName.ifBlank { "OpenAI (ChatGPT)" },
                                                    authType = AuthType.OAUTH,
                                                    accessToken = tokenResult.accessToken,
                                                    refreshToken = tokenResult.refreshToken,
                                                    tokenExpiresAt = tokenResult.expiresAt,
                                                    chatgptAccountId = tokenResult.chatgptAccountId,
                                                    accountEmail = tokenResult.accountEmail
                                                )
                                                onSaveAccount(newAccount)
                                            },
                                            { errorMsg ->
                                                openAiErrorMessage = errorMsg
                                                isOpenAiWaiting = false
                                            }
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Filled.VpnKey, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.add_account_openai_signin))
                                }
                            } else {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                    if (openAiUserCode != null) {
                                        val code = openAiUserCode ?: ""
                                        Text(
                                            text = stringResource(R.string.add_account_auth_code_value, code),
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = stringResource(R.string.add_account_code_copied),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                    Text(
                                        text = stringResource(R.string.add_account_openai_waiting),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    openAiVerificationUrl?.let { url ->
                                        TextButton(onClick = { onOpenExternalBrowser(url) }) {
                                            Text(stringResource(R.string.add_account_reopen_browser))
                                        }
                                    }
                                }
                            }

                            openAiErrorMessage?.let {
                                Text(
                                    text = it,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }

                if (selectedProvider == ProviderType.CLAUDE) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.add_account_claude_title),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = stringResource(R.string.add_account_claude_desc),
                                style = MaterialTheme.typography.bodySmall
                            )

                            Button(
                                onClick = {
                                    showClaudeCodeInput = true
                                    claudeErrorMessage = null
                                    onStartOAuth(ProviderType.CLAUDE)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.OpenInBrowser, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.add_account_claude_login_browser))
                            }

                            AnimatedVisibility(visible = showClaudeCodeInput) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedTextField(
                                        value = claudeAuthCode,
                                        onValueChange = { claudeAuthCode = it },
                                        label = { Text(stringResource(R.string.add_account_claude_code_label)) },
                                        placeholder = { Text(stringResource(R.string.add_account_claude_code_placeholder)) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Password,
                                            autoCorrectEnabled = false
                                        ),
                                        trailingIcon = {
                                            IconButton(onClick = {
                                                val text = clipboardManager.getText()?.text
                                                if (!text.isNullOrBlank()) {
                                                    claudeAuthCode = text.trim()
                                                }
                                            }) {
                                                Icon(Icons.Filled.ContentPaste, contentDescription = stringResource(R.string.add_account_paste))
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Button(
                                        onClick = {
                                            isClaudeExchanging = true
                                            claudeErrorMessage = null
                                            onExchangeClaudeCode(
                                                claudeAuthCode,
                                                { tokenResult ->
                                                    isClaudeExchanging = false
                                                    val newAccount = ProviderAccount(
                                                        id = UUID.randomUUID().toString(),
                                                        provider = ProviderType.CLAUDE,
                                                        name = accountName.ifBlank { "Claude (OAuth)" },
                                                        authType = AuthType.OAUTH,
                                                        accessToken = tokenResult.accessToken,
                                                        refreshToken = tokenResult.refreshToken,
                                                        tokenExpiresAt = tokenResult.expiresAt
                                                    )
                                                    onSaveAccount(newAccount)
                                                },
                                                { errorMsg ->
                                                    isClaudeExchanging = false
                                                    claudeErrorMessage = errorMsg
                                                }
                                            )
                                        },
                                        enabled = claudeAuthCode.isNotBlank() && !isClaudeExchanging,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        if (isClaudeExchanging) {
                                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(stringResource(R.string.add_account_exchanging))
                                        } else {
                                            Icon(Icons.Filled.CheckCircle, contentDescription = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(stringResource(R.string.add_account_issue_register_token))
                                        }
                                    }

                                    claudeErrorMessage?.let {
                                        Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }

                // Standard API Key / Token input
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.add_account_manual_key_hint),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text(stringResource(R.string.add_account_api_key_label)) },
                        placeholder = {
                            Text(
                                when (selectedProvider) {
                                    ProviderType.ZAI -> stringResource(R.string.add_account_key_placeholder_zai)
                                    ProviderType.OPENROUTER -> "sk-or-v1-..."
                                    ProviderType.CLAUDE -> stringResource(R.string.add_account_key_placeholder_claude)
                                    ProviderType.OPENAI -> stringResource(R.string.add_account_key_placeholder_openai)
                                    ProviderType.XAI -> stringResource(R.string.add_account_key_placeholder_xai)
                                    ProviderType.DEEPSEEK -> "sk-..."
                                    ProviderType.OLLAMA_CLOUD -> stringResource(R.string.add_account_key_placeholder_ollama_cloud)
                                    ProviderType.OPENCODE_GO -> stringResource(R.string.add_account_key_placeholder_opencode_go)
                                }
                            )
                        },
                        visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            autoCorrectEnabled = false
                        ),
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = {
                                    val text = clipboardManager.getText()?.text
                                    if (!text.isNullOrBlank()) {
                                        apiKey = text.trim().removeSurrounding("\"").removeSurrounding("'")
                                    }
                                }) {
                                    Icon(Icons.Filled.ContentPaste, contentDescription = stringResource(R.string.add_account_paste))
                                }
                                IconButton(onClick = { showApiKey = !showApiKey }) {
                                    Icon(
                                        imageVector = if (showApiKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = stringResource(
                                            if (showApiKey) R.string.add_account_hide_key else R.string.add_account_show_key,
                                        ),
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = when (selectedProvider) {
                            ProviderType.ZAI -> stringResource(R.string.add_account_helper_zai)
                            ProviderType.OPENROUTER -> stringResource(R.string.add_account_helper_openrouter)
                            ProviderType.CLAUDE -> stringResource(R.string.add_account_helper_claude)
                            ProviderType.OPENAI -> stringResource(R.string.add_account_helper_openai)
                            ProviderType.XAI -> stringResource(R.string.add_account_helper_xai)
                            ProviderType.DEEPSEEK -> stringResource(R.string.add_account_helper_deepseek)
                            ProviderType.OLLAMA_CLOUD -> stringResource(R.string.add_account_helper_ollama_cloud)
                            ProviderType.OPENCODE_GO -> stringResource(R.string.add_account_helper_opencode_go)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Custom Endpoint (Optional)
                OutlinedTextField(
                    value = customEndpoint,
                    onValueChange = { customEndpoint = it },
                    label = { Text(stringResource(R.string.add_account_custom_endpoint_label)) },
                    placeholder = { Text(selectedProvider.baseUrl) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanedKey = apiKey.trim()
                        .removePrefix("Bearer ")
                        .trim()
                        .removeSurrounding("\"")
                        .removeSurrounding("'")

                    val finalName = accountName.ifBlank { selectedProvider.displayName }
                    val account = ProviderAccount(
                        id = UUID.randomUUID().toString(),
                        provider = selectedProvider,
                        name = finalName,
                        authType = AuthType.API_KEY,
                        apiKey = cleanedKey,
                        customEndpoint = customEndpoint.trim().takeIf { it.isNotBlank() }
                    )
                    onSaveAccount(account)
                },
                shape = ButtonCorner,
                colors = ootInkButtonColors(),
                enabled = apiKey.isNotBlank()
            ) {
                Text(stringResource(R.string.add_account_save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ootTextButtonColors()
            ) {
                Text(stringResource(R.string.add_account_cancel))
            }
        }
    )
}
