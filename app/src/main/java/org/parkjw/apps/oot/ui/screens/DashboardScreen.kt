package org.parkjw.apps.oot.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.parkjw.apps.oot.data.model.ProviderAccount
import org.parkjw.apps.oot.data.model.ProviderType
import org.parkjw.apps.oot.data.model.TokenStatus
import org.parkjw.apps.oot.ui.theme.StatusGreen
import org.parkjw.apps.oot.ui.theme.StatusRed
import org.parkjw.apps.oot.ui.theme.StatusYellow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    accounts: List<ProviderAccount>,
    statuses: List<TokenStatus>,
    isRefreshing: Boolean,
    isServiceEnabled: Boolean,
    refreshIntervalMinutes: Int,
    onRefresh: () -> Unit,
    onToggleService: (Boolean) -> Unit,
    onSetRefreshInterval: (Int) -> Unit,
    onAddAccountClick: () -> Unit,
    onDeleteAccount: (String) -> Unit,
    onToggleAccount: (String, Boolean) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "OOT: Out of Tokens",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "AI 구독 및 API 토큰 모니터",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onRefresh,
                        enabled = !isRefreshing
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddAccountClick,
                icon = { Icon(Icons.Filled.Add, contentDescription = "Add") },
                text = { Text("계정 추가") }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                // Service & Notification Control Banner
                NotificationControlCard(
                    isServiceEnabled = isServiceEnabled,
                    refreshIntervalMinutes = refreshIntervalMinutes,
                    onToggleService = onToggleService,
                    onSetRefreshInterval = onSetRefreshInterval
                )
            }

            if (accounts.isEmpty()) {
                item {
                    EmptyAccountsBanner(onAddClick = onAddAccountClick)
                }
            } else {
                item {
                    Text(
                        text = "등록된 AI 서비스 (${accounts.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                items(accounts, key = { it.id }) { account ->
                    val status = statuses.find { it.accountId == account.id }
                    AccountStatusCard(
                        account = account,
                        status = status,
                        onDelete = { onDeleteAccount(account.id) },
                        onToggle = { isEnabled -> onToggleAccount(account.id, isEnabled) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

@Composable
fun NotificationControlCard(
    isServiceEnabled: Boolean,
    refreshIntervalMinutes: Int,
    onToggleService: (Boolean) -> Unit,
    onSetRefreshInterval: (Int) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = if (isServiceEnabled) Icons.Filled.Notifications else Icons.Filled.NotificationsOff,
                        contentDescription = null,
                        tint = if (isServiceEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                    Column {
                        Text(
                            text = "상단 알림바 실시간 표시",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isServiceEnabled) "알림창에서 토큰 잔액 상시 확인 중" else "알림바 모니터링 꺼짐",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = isServiceEnabled,
                    onCheckedChange = onToggleService
                )
            }

            AnimatedVisibility(visible = isServiceEnabled) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Divider()
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "동기화 주기: ${refreshIntervalMinutes}분마다 갱신",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(5, 15, 30, 60).forEach { minutes ->
                            FilterChip(
                                selected = refreshIntervalMinutes == minutes,
                                onClick = { onSetRefreshInterval(minutes) },
                                label = { Text("${minutes}분") }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AccountStatusCard(
    account: ProviderAccount,
    status: TokenStatus?,
    onDelete: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    val cardBorderColor = when {
        status?.isCritical == true -> StatusRed
        status?.isWarning == true -> StatusYellow
        else -> Color.Transparent
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProviderBadge(provider = account.provider)
                    Column {
                        Text(
                            text = account.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = account.provider.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = account.isEnabled,
                        onCheckedChange = onToggle
                    )
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Body
            if (!account.provider.isSelectable) {
                Text(
                    text = "지원 안 함 · 이 계정은 삭제해주세요",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (status == null) {
                Text(
                    text = "동기화 대기 중...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (status.isError) {
                Surface(
                    color = StatusRed.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "❌ " + (status.errorMessage ?: "오류 발생"),
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusRed,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            } else {
                Text(
                    text = status.remainingFormatted,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = when {
                        status.isCritical -> StatusRed
                        status.isWarning -> StatusYellow
                        else -> MaterialTheme.colorScheme.primary
                    }
                )

                if (status.totalLimitFormatted != null) {
                    Text(
                        text = status.totalLimitFormatted,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (status.usagePercent != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { (status.usagePercent / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = when {
                            status.isCritical -> StatusRed
                            status.isWarning -> StatusYellow
                            else -> StatusGreen
                        }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "사용량: ${status.usagePercent.toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (status.resetTimeFormatted != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "⏳ " + status.resetTimeFormatted,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun ProviderBadge(provider: ProviderType) {
    val (bgColor, textColor) = when (provider) {
        ProviderType.OPENROUTER -> Color(0xFF6366F1) to Color.White
        ProviderType.CLAUDE -> Color(0xFFD97706) to Color.White
        ProviderType.OPENAI -> Color(0xFF10A37F) to Color.White
        ProviderType.XAI -> Color(0xFF1D9BF0) to Color.White
        ProviderType.CURSOR -> Color(0xFF000000) to Color.White
        ProviderType.ZAI -> Color(0xFFF97316) to Color.White
        ProviderType.ANTIGRAVITY -> Color(0xFF4285F4) to Color.White
    }

    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = when (provider) {
                ProviderType.OPENROUTER -> "OR"
                ProviderType.CLAUDE -> "C"
                ProviderType.OPENAI -> "AI"
                ProviderType.XAI -> "𝕏"
                ProviderType.CURSOR -> "Cu"
                ProviderType.ZAI -> "Z"
                ProviderType.ANTIGRAVITY -> "AG"
            },
            color = textColor,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }
}

@Composable
fun EmptyAccountsBanner(onAddClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "💡 등록된 AI 계정이 없습니다",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "클로드, OpenAI, xAI (Grok), Cursor는 OAuth 로그인으로, OpenRouter와 ZAI는 API 키로 간편하게 연동하여 알림바에서 남은 토큰과 쿼터를 실시간 모니터링할 수 있습니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onAddClick) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("첫 번째 계정 등록하기")
            }
        }
    }
}
