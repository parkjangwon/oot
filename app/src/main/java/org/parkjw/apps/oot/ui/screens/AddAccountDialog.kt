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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import org.parkjw.apps.oot.data.model.AuthType
import org.parkjw.apps.oot.data.model.ProviderAccount
import org.parkjw.apps.oot.data.model.ProviderType
import org.parkjw.apps.oot.data.oauth.OAuthTokenResult
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
    onStartAntigravityOAuth: (
        onTokenReceived: (OAuthTokenResult) -> Unit,
        onError: (String) -> Unit
    ) -> Unit,
    onExchangeClaudeCode: (
        code: String,
        onTokenReceived: (OAuthTokenResult) -> Unit,
        onError: (String) -> Unit
    ) -> Unit,
    onExchangeAntigravityCode: (
        code: String,
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

    // Claude OAuth Code exchange state
    var claudeAuthCode by remember { mutableStateOf("") }
    var isClaudeExchanging by remember { mutableStateOf(false) }
    var claudeErrorMessage by remember { mutableStateOf<String?>(null) }
    var showClaudeCodeInput by remember { mutableStateOf(false) }

    // Antigravity OAuth state
    var antigravityAuthCode by remember { mutableStateOf("") }
    var isAntigravityExchanging by remember { mutableStateOf(false) }
    var antigravityErrorMessage by remember { mutableStateOf<String?>(null) }
    var showAntigravityCodeInput by remember { mutableStateOf(false) }

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
                                    isXaiWaiting = false
                                    xaiUserCode = null
                                    xaiErrorMessage = null
                                    showClaudeCodeInput = false
                                    claudeErrorMessage = null
                                    showAntigravityCodeInput = false
                                    antigravityErrorMessage = null
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
                            ProviderType.ANTIGRAVITY -> "Google Antigravity 사이트 열기"
                        }
                    )
                }

                // Provider-Specific OAuth / Device Flow Sections
                if (selectedProvider == ProviderType.ANTIGRAVITY) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "✨ Google Antigravity OAuth 2.0 연동",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Google 계정 로그인으로 Antigravity / Gemini 에이전트 쿼터를 자동 연동합니다.",
                                style = MaterialTheme.typography.bodySmall
                            )

                            Button(
                                onClick = {
                                    showAntigravityCodeInput = true
                                    antigravityErrorMessage = null
                                    onStartAntigravityOAuth(
                                        { tokenResult ->
                                            val newAccount = ProviderAccount(
                                                id = UUID.randomUUID().toString(),
                                                provider = ProviderType.ANTIGRAVITY,
                                                name = accountName.ifBlank { "Google Antigravity" },
                                                authType = AuthType.OAUTH,
                                                accessToken = tokenResult.accessToken,
                                                refreshToken = tokenResult.refreshToken,
                                                tokenExpiresAt = tokenResult.expiresAt
                                            )
                                            onSaveAccount(newAccount)
                                        },
                                        { errorMsg ->
                                            antigravityErrorMessage = errorMsg
                                        }
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.OpenInBrowser, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("외부 브라우저로 Google 로그인")
                            }

                            AnimatedVisibility(visible = showAntigravityCodeInput) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "브라우저에서 로그인 후 자동으로 등록되거나, 콜백 URL/코드를 여기에 직접 붙여넣으실 수 있습니다:",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    OutlinedTextField(
                                        value = antigravityAuthCode,
                                        onValueChange = { antigravityAuthCode = it },
                                        label = { Text("인증 코드 또는 콜백 URL") },
                                        placeholder = { Text("http://localhost:4000/?code=... 또는 코드") },
                                        singleLine = true,
                                        trailingIcon = {
                                            IconButton(onClick = {
                                                val text = clipboardManager.getText()?.text
                                                if (!text.isNullOrBlank()) {
                                                    antigravityAuthCode = text.trim()
                                                }
                                            }) {
                                                Icon(Icons.Filled.ContentPaste, contentDescription = "붙여넣기")
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Button(
                                        onClick = {
                                            isAntigravityExchanging = true
                                            antigravityErrorMessage = null
                                            onExchangeAntigravityCode(
                                                antigravityAuthCode,
                                                { tokenResult ->
                                                    isAntigravityExchanging = false
                                                    val newAccount = ProviderAccount(
                                                        id = UUID.randomUUID().toString(),
                                                        provider = ProviderType.ANTIGRAVITY,
                                                        name = accountName.ifBlank { "Google Antigravity" },
                                                        authType = AuthType.OAUTH,
                                                        accessToken = tokenResult.accessToken,
                                                        refreshToken = tokenResult.refreshToken,
                                                        tokenExpiresAt = tokenResult.expiresAt
                                                    )
                                                    onSaveAccount(newAccount)
                                                },
                                                { errorMsg ->
                                                    isAntigravityExchanging = false
                                                    antigravityErrorMessage = errorMsg
                                                }
                                            )
                                        },
                                        enabled = antigravityAuthCode.isNotBlank() && !isAntigravityExchanging,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        if (isAntigravityExchanging) {
                                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("토큰 교환 중...")
                                        } else {
                                            Icon(Icons.Filled.CheckCircle, contentDescription = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("인증 코드로 토큰 등록")
                                        }
                                    }

                                    antigravityErrorMessage?.let {
                                        Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }

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
                                text = "🔑 xAI Device Flow 로그인 (RFC 8628)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "SuperGrok / X 계정으로 외부 브라우저에서 인증하고 주간 사용량 및 한도를 확인합니다.",
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
                                    Text("xAI Device 인증 시작")
                                }
                            } else {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                    if (xaiUserCode != null) {
                                        Text(
                                            text = "인증 코드: ${xaiUserCode}",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "(코드가 클립보드에 복사되었습니다)",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                    Text(
                                        text = "외부 브라우저에서 인증 완료를 대기 중입니다...",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    xaiVerificationUrl?.let { url ->
                                        TextButton(onClick = { onOpenExternalBrowser(url) }) {
                                            Text("인증 브라우저 다시 열기")
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
                                text = "🌐 Claude 브라우저 로그인 (5h/주간 쿼터 모니터링)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Claude Pro/Max 계정 로그인 시 5시간 롤링 세션 및 주간 사용량과 리셋 타이머가 자동 연동됩니다.",
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
                                Text("외부 브라우저로 Claude 로그인")
                            }

                            AnimatedVisibility(visible = showClaudeCodeInput) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedTextField(
                                        value = claudeAuthCode,
                                        onValueChange = { claudeAuthCode = it },
                                        label = { Text("인증 코드 (code#state)") },
                                        placeholder = { Text("브라우저에 표시된 코드 복사/붙여넣기") },
                                        singleLine = true,
                                        trailingIcon = {
                                            IconButton(onClick = {
                                                val text = clipboardManager.getText()?.text
                                                if (!text.isNullOrBlank()) {
                                                    claudeAuthCode = text.trim()
                                                }
                                            }) {
                                                Icon(Icons.Filled.ContentPaste, contentDescription = "붙여넣기")
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
                                            Text("토큰 교환 중...")
                                        } else {
                                            Icon(Icons.Filled.CheckCircle, contentDescription = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("인증 코드로 토큰 발급 및 등록")
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
                        text = "또는 직접 API Key / 토큰 등록:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )

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
                                    ProviderType.ANTIGRAVITY -> "AIza... 또는 Bearer 토큰"
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
                            ProviderType.CLAUDE -> "💡 Anthropic Console API 키(sk-ant-...) 또는 OAuth 토큰을 입력해주세요."
                            ProviderType.OPENAI -> "💡 OpenAI API 키(sk-...)를 입력하면 모델 레이트 리밋과 잔여량을 조회합니다."
                            ProviderType.XAI -> "💡 xAI Console API 키 또는 Grok OAuth 토큰으로 주간 한도를 모니터링합니다."
                            ProviderType.CURSOR -> "💡 Cursor 세션 토큰 또는 API Key를 입력하여 Fast Requests 잔여량을 확인합니다."
                            ProviderType.ANTIGRAVITY -> "💡 Google Antigravity OAuth 토큰 또는 Gemini API Key를 등록하여 에이전트 쿼터를 조회합니다."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
