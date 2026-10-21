package org.parkjw.apps.oot.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
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
    onOpenExternalBrowser: (String) -> Unit,
    onSaveAccount: (ProviderAccount) -> Unit
) {
    var selectedProvider by remember { mutableStateOf(ProviderType.ZAI) }
    var accountName by remember { mutableStateOf(ProviderType.ZAI.displayName) }
    var apiKey by remember { mutableStateOf("") }
    var customEndpoint by remember { mutableStateOf("") }
    var showApiKey by remember { mutableStateOf(false) }
    var showOAuthSection by remember { mutableStateOf(false) }
    var isProviderDropdownExpanded by remember { mutableStateOf(false) }

    val clipboardManager = LocalClipboardManager.current
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
                                    accountName = provider.displayName
                                    customEndpoint = ""
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

                // Quick Link to Provider Key / Dashboard
                OutlinedButton(
                    onClick = { onOpenExternalBrowser(selectedProvider.dashboardUrl) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.OpenInBrowser, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        when (selectedProvider) {
                            ProviderType.ZAI -> "Z.ai 콘솔(키 발급) 열기"
                            ProviderType.OPENROUTER -> "OpenRouter 키 페이지 열기"
                            ProviderType.CLAUDE -> "Claude Console 키 발급 열기"
                            ProviderType.OPENAI -> "OpenAI Platform 키 발급 열기"
                            ProviderType.XAI -> "xAI Console 키 발급 열기"
                            ProviderType.CURSOR -> "Cursor 설정 페이지 열기"
                        }
                    )
                }

                // API Key / Token input
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("API Key / 토큰") },
                        placeholder = {
                            Text(
                                when (selectedProvider) {
                                    ProviderType.ZAI -> "Z.ai 코딩 플랜 API Key"
                                    ProviderType.OPENROUTER -> "sk-or-v1-..."
                                    ProviderType.CLAUDE -> "sk-ant-... 또는 세션 키"
                                    ProviderType.OPENAI -> "sk-proj-... 또는 API Key"
                                    ProviderType.XAI -> "xai-... API Key"
                                    ProviderType.CURSOR -> "Cursor 세션 토큰 / Key"
                                }
                            )
                        },
                        visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = {
                                    val text = clipboardManager.getText()?.text
                                    if (!text.isNullOrBlank()) {
                                        apiKey = text.trim().removeSurrounding("\"").removeSurrounding("'")
                                    }
                                }) {
                                    Icon(Icons.Filled.ContentPaste, contentDescription = "붙여넣기")
                                }
                                IconButton(onClick = { showApiKey = !showApiKey }) {
                                    Icon(
                                        imageVector = if (showApiKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = if (showApiKey) "Hide" else "Show"
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = when (selectedProvider) {
                            ProviderType.ZAI -> "💡 공식 모니터링 엔드포인트(api.z.ai/api/monitor/usage/quota/limit)를 통해 남은 5시간/주간 쿼터와 크레딧을 정확히 측정합니다."
                            ProviderType.OPENROUTER -> "💡 OpenRouter API 키를 등록하면 실시간 남은 달러($) 잔액과 키 한도를 확인합니다."
                            ProviderType.CLAUDE -> "💡 Anthropic Console에서 발급한 API 키(sk-ant-...)를 입력해주세요."
                            ProviderType.OPENAI -> "💡 OpenAI API 키(sk-...)를 입력하면 모델 레이트 리밋과 잔여량을 조회합니다."
                            ProviderType.XAI -> "💡 xAI Console에서 발급한 API 키를 입력하면 Grok 요청 한도를 모니터링합니다."
                            ProviderType.CURSOR -> "💡 Cursor 세션 토큰 또는 API Key를 입력하여 Fast Requests 잔여량을 확인합니다."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Optional OAuth Section Toggle
                if (selectedProvider.oauthAuthEndpoint != null) {
                    TextButton(
                        onClick = { showOAuthSection = !showOAuthSection },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text(if (showOAuthSection) "외부 브라우저 OAuth 접기" else "또는 외부 브라우저 OAuth 인증 시도")
                    }

                    AnimatedVisibility(visible = showOAuthSection) {
                        Button(
                            onClick = { onStartOAuth(selectedProvider) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.OpenInBrowser, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("폰 기본 브라우저로 ${selectedProvider.displayName} 로그인")
                        }
                    }
                }

                // Custom Endpoint (Optional)
                OutlinedTextField(
                    value = customEndpoint,
                    onValueChange = { customEndpoint = it },
                    label = { Text("커스텀 엔드포인트 (선택)") },
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
                enabled = apiKey.isNotBlank()
            ) {
                Text("저장하기")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}
