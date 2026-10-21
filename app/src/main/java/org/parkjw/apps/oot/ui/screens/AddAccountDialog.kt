package org.parkjw.apps.oot.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import org.parkjw.apps.oot.data.model.ProviderAccount
import org.parkjw.apps.oot.data.model.ProviderType
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAccountDialog(
    onDismiss: () -> Unit,
    onSave: (ProviderAccount) -> Unit
) {
    var selectedProvider by remember { mutableStateOf(ProviderType.OPENROUTER) }
    var accountName by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var customEndpoint by remember { mutableStateOf("") }
    var showApiKey by remember { mutableStateOf(false) }
    var isProviderDropdownExpanded by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "AI 계정 등록",
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
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(provider.displayName, fontWeight = FontWeight.SemiBold)
                                        Text(provider.description, style = MaterialTheme.typography.labelSmall)
                                    }
                                },
                                onClick = {
                                    selectedProvider = provider
                                    if (accountName.isBlank()) {
                                        accountName = provider.displayName
                                    }
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

                // API Key Input
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key / Token") },
                    placeholder = {
                        Text(
                            when (selectedProvider) {
                                ProviderType.OPENROUTER -> "sk-or-v1-..."
                                ProviderType.XAI -> "xai-..."
                                ProviderType.ZAI -> "zai-..."
                                ProviderType.CLAUDE -> "sk-ant-..."
                            }
                        )
                    },
                    visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showApiKey = !showApiKey }) {
                            Icon(
                                imageVector = if (showApiKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (showApiKey) "Hide API key" else "Show API key"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                // Provider specific hint
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "💡 ${selectedProvider.displayName} 안내",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = when (selectedProvider) {
                                ProviderType.OPENROUTER -> "OpenRouter 키의 크레딧 잔액 및 사용량을 실시간으로 추적합니다."
                                ProviderType.XAI -> "X 통합 요금제 및 xAI API 상태와 요청 한도를 추적합니다."
                                ProviderType.ZAI -> "ZAI 코딩 플랜 API의 남은 토큰 및 플랜 할당량을 모니터링합니다."
                                ProviderType.CLAUDE -> "Anthropic Claude API 레이트 리밋 및 토큰 할당량을 조회합니다."
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                // Custom Endpoint (Optional)
                OutlinedTextField(
                    value = customEndpoint,
                    onValueChange = { customEndpoint = it },
                    label = { Text("사용자 정의 엔드포인트 (선택)") },
                    placeholder = { Text(selectedProvider.baseUrl) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalName = accountName.ifBlank { selectedProvider.displayName }
                    val account = ProviderAccount(
                        id = UUID.randomUUID().toString(),
                        provider = selectedProvider,
                        name = finalName,
                        apiKey = apiKey.trim(),
                        customEndpoint = customEndpoint.trim().takeIf { it.isNotBlank() }
                    )
                    onSave(account)
                },
                enabled = apiKey.isNotBlank()
            ) {
                Text("추가하기")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}
