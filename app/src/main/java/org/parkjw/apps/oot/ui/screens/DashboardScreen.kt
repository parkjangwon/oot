package org.parkjw.apps.oot.ui.screens

import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import org.parkjw.apps.oot.data.local.AccountPreferences
import org.parkjw.apps.oot.data.model.Level
import org.parkjw.apps.oot.data.model.ProviderAccount
import org.parkjw.apps.oot.data.model.ProviderType
import org.parkjw.apps.oot.data.model.Remaining
import org.parkjw.apps.oot.data.model.TokenStatus
import org.parkjw.apps.oot.data.model.UsageWindow
import org.parkjw.apps.oot.data.model.VendorError
import org.parkjw.apps.oot.data.model.VendorUsage
import org.parkjw.apps.oot.data.model.level
import org.parkjw.apps.oot.data.model.toVendorUsage
import org.parkjw.apps.oot.ui.VendorLogo
import org.parkjw.apps.oot.ui.theme.ButtonCorner
import org.parkjw.apps.oot.ui.theme.CardCorner
import org.parkjw.apps.oot.ui.theme.DialogCorner
import org.parkjw.apps.oot.ui.theme.OotMonoFamily
import org.parkjw.apps.oot.ui.theme.OotProgressBar
import org.parkjw.apps.oot.ui.theme.OotShapeTokens
import org.parkjw.apps.oot.ui.theme.OotSpacing
import org.parkjw.apps.oot.ui.theme.extendedColors
import org.parkjw.apps.oot.ui.theme.ootIconButtonColors
import org.parkjw.apps.oot.ui.theme.ootInkButtonColors
import org.parkjw.apps.oot.ui.theme.ootTextButtonColors
import org.parkjw.apps.oot.ui.theme.progressLevel
import org.parkjw.apps.oot.ui.theme.withNumericSpans
import org.parkjw.apps.oot.notification.NotificationContent
import java.time.Instant
import kotlin.math.roundToInt

private val WordWrapFriendly = LineBreak.Paragraph
private val ReorderPlacementSpec = tween<IntOffset>(150)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(
    vendorUsages: List<VendorUsage>,
    modifier: Modifier = Modifier,
    legacyAccounts: List<ProviderAccount> = emptyList(),
    isRefreshing: Boolean = false,
    now: Instant = Instant.now(),
    onRefresh: () -> Unit = {},
    onAddAccountClick: () -> Unit = {},
    onSettingsClick: (() -> Unit)? = null,
    onVendorClick: ((VendorUsage) -> Unit)? = null,
    onDeleteAccount: (String) -> Unit = {},
    onToggleAccount: (String, Boolean) -> Unit = { _, _ -> },
    onReorderVendors: (List<String>) -> Unit = {},
    /** Static drag look for screenshots; null = normal. */
    previewDraggingAccountId: String? = null,
    isServiceEnabled: Boolean = false,
    refreshIntervalMinutes: Int = 15,
    onToggleService: (Boolean) -> Unit = {},
    onSetRefreshInterval: (Int) -> Unit = {},
) {
    var selectedVendorForDetail by remember { mutableStateOf<VendorUsage?>(null) }
    var selectedLegacyForDetail by remember { mutableStateOf<ProviderAccount?>(null) }
    var orderedUsages by remember { mutableStateOf(vendorUsages) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(vendorUsages) {
        if (previewDraggingAccountId != null) return@LaunchedEffect
        // A refresh landing mid-drag still carries the saved order: keep the dragged one, take only the new numbers.
        orderedUsages = if (draggingId == null) {
            vendorUsages
        } else {
            val byId = vendorUsages.associateBy { it.accountId }
            AccountPreferences.mergeAccountOrder(orderedUsages.map { it.accountId }, vendorUsages.map { it.accountId })
                .mapNotNull { byId[it] }
        }
    }
    val listState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val edgePx = with(density) { 48.dp.toPx() }
    val scope = rememberCoroutineScope()
    val activeDraggingId = draggingId ?: previewDraggingAccountId
    // The drag handler outlives recompositions, so it must call the latest callback.
    val reorder by rememberUpdatedState(onReorderVendors)

    fun moveVendor(from: Int, to: Int) {
        if (from == to || from !in orderedUsages.indices || to !in orderedUsages.indices) return
        orderedUsages = orderedUsages.toMutableList().apply {
            add(to, removeAt(from))
        }
        reorder(orderedUsages.map { it.accountId })
    }

    // A drop and a cancelled gesture both leave the list as shown, so report what is shown.
    fun endDrag() {
        if (draggingId == null) return
        val ids = orderedUsages.map { it.accountId }
        draggingId = null
        dragOffsetY = 0f
        reorder(ids)
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "oot",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
                actions = {
                    IconButton(
                        onClick = onRefresh,
                        enabled = !isRefreshing,
                        colors = ootIconButtonColors(),
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "새로고침",
                            )
                        }
                    }
                    IconButton(
                        onClick = onAddAccountClick,
                        colors = ootIconButtonColors(),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "프로바이더 추가",
                        )
                    }
                    IconButton(
                        onClick = { onSettingsClick?.invoke() },
                        colors = ootIconButtonColors(),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "설정",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            LazyColumn(
                state = listState,
                userScrollEnabled = draggingId == null,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = OotSpacing.screenHorizontal),
                contentPadding = PaddingValues(bottom = OotSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(OotSpacing.sm),
            ) {
                val leastItem = findLeastRemainingItem(orderedUsages)
                if (leastItem != null) {
                    item(key = "summary_line") {
                        QuotaSummaryLine(
                            vendorName = leastItem.first.shortVendorName(),
                            window = leastItem.second,
                            now = now,
                            modifier = Modifier.padding(bottom = OotSpacing.xxs),
                        )
                    }
                }

                if (orderedUsages.isEmpty() && legacyAccounts.isEmpty()) {
                    item(key = "empty_state") {
                        EmptyAccountsBanner(onAddClick = onAddAccountClick)
                    }
                } else {
                    itemsIndexed(orderedUsages, key = { _, u -> u.accountId }) { index, usage ->
                        val isDragged = activeDraggingId == usage.accountId
                        VendorCard(
                            usage = usage,
                            now = now,
                            isDragged = isDragged,
                            onClick = {
                                if (draggingId != null) return@VendorCard
                                if (onVendorClick != null) {
                                    onVendorClick(usage)
                                } else {
                                    selectedVendorForDetail = usage
                                }
                            },
                            modifier = Modifier
                                .animateItem(placementSpec = ReorderPlacementSpec)
                                .zIndex(if (isDragged) 1f else 0f)
                                .graphicsLayer {
                                    if (isDragged) {
                                        if (draggingId != null) translationY = dragOffsetY
                                        scaleX = 1.02f
                                        scaleY = 1.02f
                                    }
                                }
                                .semantics {
                                    customActions = buildList {
                                        if (index > 0) {
                                            add(
                                                CustomAccessibilityAction("위로 이동") {
                                                    moveVendor(index, index - 1)
                                                    true
                                                },
                                            )
                                        }
                                        if (index < orderedUsages.lastIndex) {
                                            add(
                                                CustomAccessibilityAction("아래로 이동") {
                                                    moveVendor(index, index + 1)
                                                    true
                                                },
                                            )
                                        }
                                    }
                                }
                                // Keyed by id only: keying on the order restarted the gesture on every swap and lost the drop (#61).
                                .pointerInput(usage.accountId) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            draggingId = usage.accountId
                                            dragOffsetY = 0f
                                        },
                                        onDragCancel = { endDrag() },
                                        onDragEnd = { endDrag() },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragOffsetY += dragAmount.y
                                            val from = orderedUsages.indexOfFirst { it.accountId == draggingId }
                                            if (from < 0) return@detectDragGesturesAfterLongPress
                                            val itemInfo = listState.layoutInfo.visibleItemsInfo
                                                .find { it.key == usage.accountId }
                                                ?: return@detectDragGesturesAfterLongPress
                                            val spacing = with(density) { OotSpacing.sm.toPx() }
                                            val step = itemInfo.size + spacing
                                            val shift = when {
                                                dragOffsetY > step / 2f && from < orderedUsages.lastIndex -> 1
                                                dragOffsetY < -step / 2f && from > 0 -> -1
                                                else -> 0
                                            }
                                            if (shift != 0) {
                                                orderedUsages = orderedUsages.toMutableList().apply {
                                                    add(from + shift, removeAt(from))
                                                }
                                                dragOffsetY -= shift * step
                                            }
                                            val center = itemInfo.offset + itemInfo.size / 2f + dragOffsetY
                                            val viewportStart = listState.layoutInfo.viewportStartOffset.toFloat()
                                            val viewportEnd = listState.layoutInfo.viewportEndOffset.toFloat()
                                            when {
                                                center < viewportStart + edgePx ->
                                                    scope.launch { listState.scrollBy(-edgePx / 3f) }
                                                center > viewportEnd - edgePx ->
                                                    scope.launch { listState.scrollBy(edgePx / 3f) }
                                            }
                                        },
                                    )
                                },
                        )
                    }

                    itemsIndexed(legacyAccounts, key = { _, a -> "legacy_${a.id}" }) { _, account ->
                        UnsupportedLegacyCard(
                            account = account,
                            onClick = { selectedLegacyForDetail = account },
                            modifier = Modifier.animateItem(placementSpec = ReorderPlacementSpec),
                        )
                    }

                    item(key = "add_account_row") {
                        AddAccountRow(onClick = onAddAccountClick)
                    }
                }
            }
        }
    }

    selectedVendorForDetail?.let { vendor ->
        VendorDetailDialog(
            usage = vendor,
            onDismiss = { selectedVendorForDetail = null },
            onDelete = {
                val id = vendor.accountId
                selectedVendorForDetail = null
                onDeleteAccount(id)
            },
        )
    }

    selectedLegacyForDetail?.let { legacy ->
        AlertDialog(
            onDismissRequest = { selectedLegacyForDetail = null },
            shape = DialogCorner,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            title = {
                Text(
                    text = legacy.name,
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            text = {
                Text(
                    text = "지원 안 함 · 이 프로바이더는 더 이상 지원하지 않으므로 삭제할 수 있습니다.",
                    style = MaterialTheme.typography.bodyMedium.copy(lineBreak = WordWrapFriendly),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = legacy.id
                        selectedLegacyForDetail = null
                        onDeleteAccount(id)
                    },
                    colors = ootTextButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("프로바이더 삭제")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { selectedLegacyForDetail = null },
                    colors = ootTextButtonColors(),
                ) {
                    Text("닫기")
                }
            },
        )
    }
}

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
    onToggleAccount: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    now: Instant = Instant.now(),
    onSettingsClick: (() -> Unit)? = null,
) {
    val selectableAccounts = accounts.filter { it.provider.isSelectable }
    val legacyAccounts = accounts.filter { !it.provider.isSelectable }
    val usages = selectableAccounts.map { account ->
        val status = statuses.find { it.accountId == account.id }
        status?.toVendorUsage() ?: VendorUsage(
            accountId = account.id,
            providerType = account.provider,
            displayName = account.name,
            windows = emptyList(),
        )
    }

    DashboardScreen(
        vendorUsages = usages,
        modifier = modifier,
        legacyAccounts = legacyAccounts,
        isRefreshing = isRefreshing,
        now = now,
        onRefresh = onRefresh,
        onAddAccountClick = onAddAccountClick,
        onSettingsClick = onSettingsClick,
        onDeleteAccount = onDeleteAccount,
        onToggleAccount = onToggleAccount,
        isServiceEnabled = isServiceEnabled,
        refreshIntervalMinutes = refreshIntervalMinutes,
        onToggleService = onToggleService,
        onSetRefreshInterval = onSetRefreshInterval,
    )
}

private fun findLeastRemainingItem(usages: List<VendorUsage>): Pair<VendorUsage, UsageWindow>? {
    return usages
        .asSequence()
        .filter { it.error == null }
        .flatMap { vendor ->
            vendor.windows.asSequence()
                .filter { it.remaining is Remaining.Percent }
                .map { vendor to it }
        }
        .minByOrNull { (_, window) ->
            (window.remaining as Remaining.Percent).percentLeft
        }
}

@Composable
fun QuotaSummaryLine(
    vendorName: String,
    window: UsageWindow,
    now: Instant,
    modifier: Modifier = Modifier,
) {
    val pct = ((window.remaining as? Remaining.Percent)?.percentLeft ?: 0.0).roundToInt().coerceIn(0, 100)
    val resetText = formatTimeToReset(window.resetsAt, now)
    val baseStyle = MaterialTheme.typography.bodyMedium.copy(lineBreak = WordWrapFriendly)
    val numericSpan = SpanStyle(
        fontFamily = OotMonoFamily,
        letterSpacing = (-0.2).sp,
    )
    val boldColor = if (pct <= 5) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val summaryAnnotated = buildAnnotatedString {
        withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
            append("가장 적게 남은 곳  ")
        }
        val highlight = buildString {
            append(vendorName)
            if (window.label.isNotBlank() && window.label != "primary") {
                append(" ")
                append(window.label)
            }
            append(" ")
            append(pct)
            append("%")
        }
        withStyle(SpanStyle(color = boldColor, fontWeight = FontWeight.SemiBold)) {
            append(withNumericSpans(highlight, numericSpan))
        }
        if (resetText != null) {
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                append(" · ")
                append(withNumericSpans("$resetText 리셋", numericSpan))
            }
        }
    }

    Text(
        text = summaryAnnotated,
        style = baseStyle,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun VendorCard(
    usage: VendorUsage,
    now: Instant,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDragged: Boolean = false,
) {
    val worstLevel = usage.windows
        .filter { it.remaining is Remaining.Percent }
        .minByOrNull { (it.remaining as Remaining.Percent).percentLeft }
        ?.remaining
        ?.level

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardCorner)
            .clickable(enabled = !isDragged, onClick = onClick),
        shape = CardCorner,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        shadowElevation = if (isDragged) 4.dp else 0.dp,
        border = BorderStroke(
            width = if (isDragged) 1.5.dp else 1.dp,
            color = if (isDragged) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(OotSpacing.md),
            verticalArrangement = Arrangement.spacedBy(OotSpacing.sm),
        ) {
            // Card Header: VendorMark + Name + Status Badge + Kind Chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    VendorMark(provider = usage.providerType)
                    Text(
                        text = usage.displayName.ifBlank { usage.shortVendorName() },
                        style = MaterialTheme.typography.titleMedium.copy(lineBreak = WordWrapFriendly),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (usage.error == null) {
                        when (worstLevel) {
                            Level.DANGER -> SeverityBadge(
                                text = "위험",
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Level.WARN -> SeverityBadge(
                                text = "주의",
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            )
                            else -> Unit
                        }
                    }
                    VendorKindChip(text = usage.kindChipLabel())
                }
            }

            // Card Body
            when {
                !usage.providerType.isSelectable -> {
                    Text(
                        text = "지원 안 함",
                        style = MaterialTheme.typography.bodyMedium.copy(lineBreak = WordWrapFriendly),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                usage.error != null -> {
                    ProblemBox(error = usage.error)
                }
                usage.windows.isEmpty() -> {
                    Text(
                        text = "동기화 대기 중",
                        style = MaterialTheme.typography.bodyMedium.copy(lineBreak = WordWrapFriendly),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(OotSpacing.sm),
                    ) {
                        usage.windows.forEach { window ->
                            when (val rem = window.remaining) {
                                is Remaining.Percent -> {
                                    LimitRow(
                                        label = window.displayLabel(),
                                        remainingPercent = rem.percentLeft,
                                        resetsAt = window.resetsAt,
                                        now = now,
                                    )
                                }
                                is Remaining.Balance -> {
                                    if (window.id == "key") {
                                        KeyLimitRow(
                                            label = window.label,
                                            remaining = rem.amountLeft,
                                            currency = rem.currency,
                                        )
                                    } else {
                                        BalanceRow(
                                            amountLeft = rem.amountLeft,
                                            currency = rem.currency,
                                            subtitle = window.balanceSubtitle(),
                                        )
                                    }
                                }
                            }
                        }
                        if (usage.providerType == ProviderType.OPENROUTER &&
                            usage.windows.none { it.id == "key" }
                        ) {
                            Text(
                                text = "키 한도 없음",
                                style = MaterialTheme.typography.bodyMedium.copy(lineBreak = WordWrapFriendly),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LimitRow(
    label: String,
    remainingPercent: Double,
    resetsAt: Instant?,
    now: Instant,
    modifier: Modifier = Modifier,
) {
    val pctInt = remainingPercent.roundToInt().coerceIn(0, 100)
    val level = progressLevel(pctInt)
    val resetText = formatTimeToReset(resetsAt, now)
    // <=20% only warns through the card badge; the number turns error-colored at <=5%.
    val numberColor = if (pctInt <= 5) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val numericSpan = SpanStyle(
        fontFamily = OotMonoFamily,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp,
    )
    val labelNumericSpan = SpanStyle(
        fontFamily = OotMonoFamily,
        letterSpacing = (-0.2).sp,
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = withNumericSpans(label, labelNumericSpan),
                modifier = Modifier.alignByBaseline(),
                style = MaterialTheme.typography.bodyMedium.copy(lineBreak = WordWrapFriendly),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = withNumericSpans("${pctInt}% 남음", numericSpan),
                modifier = Modifier.alignByBaseline(),
                style = MaterialTheme.typography.bodyMedium.copy(lineBreak = WordWrapFriendly),
                color = numberColor,
            )
        }

        OotProgressBar(
            progress = (pctInt / 100f).coerceIn(0f, 1f),
            level = level,
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(OotShapeTokens.pill),
        )

        if (resetText != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Text(
                    text = withNumericSpans(resetText, labelNumericSpan),
                    style = MaterialTheme.typography.bodySmall.copy(lineBreak = WordWrapFriendly),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun BalanceRow(
    amountLeft: Double,
    subtitle: String?,
    modifier: Modifier = Modifier,
    currency: String = "USD",
) {
    val formattedAmount = NotificationContent.formatBalance(amountLeft, currency)
    val numericSpan = SpanStyle(
        fontFamily = OotMonoFamily,
        letterSpacing = (-0.3).sp,
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = formattedAmount,
                modifier = Modifier.alignByBaseline(),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = OotMonoFamily,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = (-0.5).sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "남음",
                modifier = Modifier.alignByBaseline(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (!subtitle.isNullOrBlank()) {
            Text(
                text = withNumericSpans(subtitle, numericSpan),
                style = MaterialTheme.typography.bodySmall.copy(lineBreak = WordWrapFriendly),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// OpenRouter key limit row: '키 한도 $20.00 중 $7.60 남음' with a bar (20%/5% levels).
// The limit is parsed back from the window label produced by OpenRouterCodec.
private val keyLimitAmountRegex = Regex("""\d+(\.\d+)?""")

fun keyLimitFromLabel(label: String): Double? =
    keyLimitAmountRegex.find(label)?.value?.toDoubleOrNull()

@Composable
fun KeyLimitRow(
    label: String,
    remaining: Double,
    currency: String,
    modifier: Modifier = Modifier,
) {
    val limit = keyLimitFromLabel(label)
    val pctInt = limit?.takeIf { it > 0 }
        ?.let { ((remaining / it) * 100).roundToInt().coerceIn(0, 100) }
    val numberColor = when {
        pctInt == null -> MaterialTheme.colorScheme.onSurfaceVariant
        pctInt <= 5 -> MaterialTheme.colorScheme.error
        pctInt <= 20 -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val numericSpan = SpanStyle(
        fontFamily = OotMonoFamily,
        letterSpacing = (-0.2).sp,
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = withNumericSpans(
                "$label 중 ${NotificationContent.formatBalance(remaining, currency)} 남음",
                numericSpan,
            ),
            style = MaterialTheme.typography.bodyMedium.copy(lineBreak = WordWrapFriendly),
            color = numberColor,
        )

        if (pctInt != null) {
            OotProgressBar(
                progress = (pctInt / 100f).coerceIn(0f, 1f),
                level = progressLevel(pctInt),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(OotShapeTokens.pill),
            )
        }
    }
}

@Composable
fun ProblemBox(
    error: VendorError,
    modifier: Modifier = Modifier,
) {
    val numericSpan = SpanStyle(
        fontFamily = OotMonoFamily,
        letterSpacing = (-0.2).sp,
    )
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = ButtonCorner,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = OotSpacing.sm, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = withNumericSpans("확인 필요 · ${vendorErrorShortText(error)}", numericSpan),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    lineBreak = WordWrapFriendly,
                ),
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
fun UnsupportedLegacyCard(
    account: ProviderAccount,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardCorner)
            .clickable(onClick = onClick),
        shape = CardCorner,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(OotSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                VendorMark(provider = account.provider)
                Text(
                    text = account.name.ifBlank { account.provider.displayName },
                    style = MaterialTheme.typography.titleMedium.copy(lineBreak = WordWrapFriendly),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = "지원 안 함",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    lineBreak = WordWrapFriendly,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun VendorMark(
    provider: ProviderType,
    modifier: Modifier = Modifier,
) {
    val bg = MaterialTheme.colorScheme.surfaceContainerHigh
    val border = MaterialTheme.colorScheme.outlineVariant
    val fg = MaterialTheme.colorScheme.onSurface
    val shape = RoundedCornerShape(8.dp)
    val logo = VendorLogo.of(provider)
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    Box(
        modifier = modifier
            .size(32.dp)
            .clip(shape)
            .background(bg)
            .border(1.dp, border, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (logo != null) {
            val logoMod = if (logo.wide) {
                Modifier.height(14.dp)
            } else {
                Modifier.size(20.dp)
            }
            Image(
                painter = painterResource(
                    if (logo.tintWithOnSurface) logo.mono else logo.colorFor(dark),
                ),
                contentDescription = null,
                modifier = logoMod,
                contentScale = ContentScale.Fit,
                colorFilter = if (logo.tintWithOnSurface) {
                    ColorFilter.tint(fg)
                } else {
                    null
                },
            )
        } else {
            val monogram = when (provider) {
                ProviderType.CLAUDE -> "C"
                ProviderType.ZAI -> "G"
                ProviderType.XAI -> "X"
                ProviderType.OPENROUTER -> "R"
                ProviderType.OPENAI -> "O"
                ProviderType.ANTIGRAVITY -> "A"
                ProviderType.DEEPSEEK -> "D"
                ProviderType.OLLAMA_CLOUD -> "OL"
                ProviderType.OPENCODE_GO -> "OG"
            }
            Text(
                text = monogram,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = OotMonoFamily,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = fg,
            )
        }
    }
}

@Composable
private fun SeverityBadge(
    text: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
) {
    Box(
        modifier = Modifier
            .clip(OotShapeTokens.pill)
            .background(containerColor)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
        )
    }
}

@Composable
private fun VendorKindChip(text: String) {
    Box(
        modifier = Modifier
            .clip(OotShapeTokens.pill)
            .border(1.dp, MaterialTheme.extendedColors.hairlineStrong, OotShapeTokens.pill)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun AddAccountRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strokeColor = MaterialTheme.extendedColors.hairlineStrong
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardCorner)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            drawRoundRect(
                color = strokeColor,
                cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
                style = Stroke(
                    width = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()), 0f),
                ),
            )
        }
        Row(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = OotSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "프로바이더 추가",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun EmptyAccountsBanner(
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = OotSpacing.md),
        shape = CardCorner,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(OotSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(OotSpacing.sm),
        ) {
            Text(
                text = "등록된 프로바이더가 없습니다",
                style = MaterialTheme.typography.titleMedium.copy(lineBreak = WordWrapFriendly),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Claude, GLM, xAI, OpenRouter, OpenAI 프로바이더를 추가해 남은 한도와 잔액을 확인하세요.",
                style = MaterialTheme.typography.bodyMedium.copy(lineBreak = WordWrapFriendly),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(OotSpacing.xxs))
            androidx.compose.material3.Button(
                onClick = onAddClick,
                shape = ButtonCorner,
                colors = ootInkButtonColors(),
                elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("프로바이더 추가")
            }
        }
    }
}

@Composable
private fun VendorDetailDialog(
    usage: VendorUsage,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = DialogCorner,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                VendorMark(provider = usage.providerType)
                Text(
                    text = usage.displayName.ifBlank { usage.shortVendorName() },
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = usage.providerType.description,
                    style = MaterialTheme.typography.bodySmall.copy(lineBreak = WordWrapFriendly),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (usage.error != null) {
                    ProblemBox(error = usage.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDelete,
                colors = ootTextButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text("프로바이더 삭제")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ootTextButtonColors(),
            ) {
                Text("닫기")
            }
        },
    )
}

private fun VendorUsage.shortVendorName(): String = when (providerType) {
    ProviderType.CLAUDE -> "Claude"
    ProviderType.ZAI -> "GLM"
    ProviderType.XAI -> "xAI"
    ProviderType.OPENROUTER -> "OpenRouter"
    ProviderType.OPENAI -> "OpenAI"
    ProviderType.ANTIGRAVITY -> "Antigravity"
    ProviderType.DEEPSEEK -> "DeepSeek"
    ProviderType.OLLAMA_CLOUD -> "Ollama Cloud"
    ProviderType.OPENCODE_GO -> "OpenCode Go"
}

private fun VendorUsage.kindChipLabel(): String {
    if (!providerType.isSelectable) return "지원 안 함"
    if (windows.any { it.remaining is Remaining.Balance } || providerType == ProviderType.OPENROUTER) {
        return "API 크레딧"
    }
    return "구독"
}

private fun UsageWindow.displayLabel(): String = when {
    label.isBlank() || label == "primary" -> when (id) {
        "five_hour" -> "5시간"
        "seven_day", "weekly" -> "주간"
        "monthly" -> "월간"
        else -> "주간"
    }
    else -> label
}

private fun UsageWindow.balanceSubtitle(): String? {
    if (label.isBlank() || label == "primary" || label == "credit") return null
    if (label.startsWith("잔여 크레딧")) return null
    return label
}

fun vendorErrorShortText(error: VendorError): String = when (error) {
    VendorError.Unauthorized -> "다시 로그인 필요"
    VendorError.ScopeMissing -> "권한 범위 부족"
    VendorError.RateLimited -> "요청 제한 (429)"
    VendorError.Network -> "네트워크 오류"
    VendorError.Parse -> "응답 해석 실패"
    is VendorError.Http -> "HTTP ${error.code}"
    is VendorError.Unknown -> error.message?.takeIf { it.isNotBlank() } ?: "연결 확인 필요"
}

fun formatTimeToReset(resetsAt: Instant?, now: Instant): String? {
    if (resetsAt == null) return null
    val diffSeconds = resetsAt.epochSecond - now.epochSecond
    if (diffSeconds <= 0) return "곧 리셋"
    val totalMinutes = (diffSeconds + 30) / 60
    val days = totalMinutes / (60 * 24)
    val hours = (totalMinutes % (60 * 24)) / 60
    val minutes = totalMinutes % 60

    return when {
        days >= 1 -> "${days}일 후"
        hours >= 1 && minutes > 0 -> "${hours}시간 ${minutes}분 후"
        hours >= 1 -> "${hours}시간 후"
        minutes >= 1 -> "${minutes}분 후"
        else -> "곧 리셋"
    }
}
