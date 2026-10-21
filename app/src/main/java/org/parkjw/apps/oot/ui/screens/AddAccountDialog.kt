package org.parkjw.apps.oot.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import org.parkjw.apps.oot.data.model.AuthType
import org.parkjw.apps.oot.data.model.ProviderAccount
import org.parkjw.apps.oot.data.model.ProviderType
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAccountDialog(
    onDismiss: () -> Unit,
    onStartOAuth: (ProviderType) -> Unit,
    onSaveManualAccount: (ProviderAccount) -> Unit
) {
    var selectedProvider by remember { mutableStateOf(ProviderType.CLAUDE) }
    var accountName by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var customEndpoint by remember { mutableStateOf("") }
    var showApiKey by remember { mutableStateOf(false) }
    var showManualInputForOAuth by remember { mutableStateOf(false) }
    var isProviderDropdownExpanded by remember { mutableStateOf(false) }

    val isOAuthProvider = selectedProvider.primaryAuthType == AuthType.OAUTH
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "AI 계정 연동",
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
                        value = "${selectedProvider.displayName} (${if (isOAuthProvider) "OAuth" else "API Key"})",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("제공자 (Provider)") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isProviderDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = isProviderDropdownExpanded,
                        onDismissRequest = { isProviderDropdownExpanded = false }
                    ) {
                        ProviderType.entries.forEach { provider ->
                            val isOAuth = provider.primaryAuthType == AuthType.OAUTH
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(provider.displayName, fontWeight = FontWeight.SemiBold)
                                            AssistChip(
                                                onClick = {},
                                                label = { Text(if (isOAuth) "OAuth" else "Key", style = MaterialTheme.typography.labelSmall) },
                                                modifier = Modifier.height(24.dp)
                                            )
                                        }
                                        Text(provider.description, style = MaterialTheme.typography.labelSmall)
                                    }
                                },
                                onClick = {
                                    selectedProvider = provider
                                    accountName = provider.displayName
                                    showManualInputForOAuth = false
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
                    label = { Text("계정 별칭") },
                    placeholder = { Text(selectedProvider.displayName) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // OAuth Provider View
                if (isOAuthProvider) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "🔒 OAuth 2.0 간편 인증",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = when (selectedProvider) {
                                    ProviderType.CLAUDE -> "Claude 웹 계정에 로그인하여 Pro/Team 플랜 토큰 사용량을 연동합니다."
                                    ProviderType.OPENAI -> "OpenAI에 로그인하여 ChatGPT Plus/Team/Pro 구독 및 API 쿼터를 연동합니다."
                                    ProviderType.XAI -> "X(트위터) 계정으로 인증하여 X 통합 요금제 및 Grok 사용량을 연동합니다."
                                    ProviderType.CURSOR -> "Cursor 계정으로 로그인하여 빠른 요청(Fast Requests) 잔여량을 연동합니다."
                                    else -> "브라우저를 통해 안전하게 인증합니다."
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                            Button(
                                onClick = { onStartOAuth(selectedProvider) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.OpenInBrowser, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("${selectedProvider.displayName} 로그인 연동")
                            }
                        }
                    }

                    // Optional manual token fallback toggle
                    TextButton(
                        onClick = { showManualInputForOAuth = !showManualInputForOAuth },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text(if (showManualInputForOAuth) "직접 입력 닫기" else "또는 API Key / 세션 토큰 직접 입력")
                    }
                }

                // API Key / Token input
                AnimatedVisibility(visible = !isOAuthProvider || showManualInputForOAuth) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            label = { Text(if (isOAuthProvider) "세션 토큰 / Bearer Key" else "API Key") },
                            placeholder = {
                                Text(
                                    when (selectedProvider) {
                                        ProviderType.OPENROUTER -> "sk-or-v1-..."
                                        ProviderType.ZAI -> "zai-..."
                                        ProviderType.CLAUDE -> "sk-ant-..."
                                        ProviderType.OPENAI -> "sk-proj-..."
                                        ProviderType.XAI -> "xai-..."
                                        ProviderType.CURSOR -> "Bearer token"
                                    }
                                )
                            },
                            visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                IconButton(onClick = { showApiKey = !showApiKey }) {
                                    Icon(
                                        imageVector = if (showApiKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = if (showApiKey) "Hide key" else "Show key"
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Custom Endpoint (Optional)
                        OutlinedTextField(
                            value = customEndpoint,
                            onValueChange = { customEndpoint = it },
                            label = { Text("엔드포인트 (선택)") },
                            placeholder = { Text(selectedProvider.baseUrl) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (!isOAuthProvider || showManualInputForOAuth) {
                Button(
                    onClick = {
                        val finalName = accountName.ifBlank { selectedProvider.displayName }
                        val account = ProviderAccount(
                            id = UUID.randomUUID().toString(),
                            provider = selectedProvider,
                            name = finalName,
                            authType = if (isOAuthProvider && showManualInputForOAuth) AuthType.API_KEY else selectedProvider.primaryAuthType,
                            apiKey = apiKey.trim(),
                            customEndpoint = customEndpoint.trim().takeIf { it.isNotBlank() }
                        )
                        onSaveManualAccount(account)
                    },
                    enabled = apiKey.isNotBlank()
                ) {
                    Text("수동 저장")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("닫기")
            }
        }
    )
}
