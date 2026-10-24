package org.parkjw.apps.oot.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch
import org.parkjw.apps.oot.R
import org.parkjw.apps.oot.diagnostics.CrashKind
import org.parkjw.apps.oot.diagnostics.CrashRecord
import org.parkjw.apps.oot.diagnostics.ErrorReport
import org.parkjw.apps.oot.diagnostics.IssuePreview
import org.parkjw.apps.oot.diagnostics.Redactor
import org.parkjw.apps.oot.ui.theme.ButtonCorner
import org.parkjw.apps.oot.ui.theme.CardCorner
import org.parkjw.apps.oot.ui.theme.DialogCorner
import org.parkjw.apps.oot.ui.theme.OotNumeric
import org.parkjw.apps.oot.ui.theme.OotSpacing
import org.parkjw.apps.oot.ui.theme.extendedColors
import org.parkjw.apps.oot.ui.theme.ootIconButtonColors
import org.parkjw.apps.oot.ui.theme.ootInkButtonColors
import org.parkjw.apps.oot.ui.theme.ootOutlinedButtonColors
import org.parkjw.apps.oot.ui.theme.ootTextButtonColors
import org.parkjw.apps.oot.ui.theme.withNumericSpans

private val recordTimeFormat = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm")

fun formatRecordTime(timestamp: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    recordTimeFormat.format(Instant.ofEpochMilli(timestamp).atZone(zone))

fun CrashRecord.kindLabelRes(): Int = when (kind) {
    CrashKind.CRASH -> R.string.error_log_kind_crash
    CrashKind.ANR -> R.string.error_log_kind_anr
    CrashKind.NATIVE_CRASH -> R.string.error_log_kind_native_crash
    CrashKind.EXCESSIVE_RESOURCE_USAGE -> R.string.error_log_kind_excessive_resource_usage
    CrashKind.SIGNALED -> R.string.error_log_kind_signaled
    CrashKind.LOW_MEMORY -> R.string.error_log_kind_low_memory
    CrashKind.INITIALIZATION_FAILURE -> R.string.error_log_kind_initialization_failure
    else -> R.string.error_log_kind_other
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ErrorLogScreen(
    records: List<CrashRecord>,
    onBack: () -> Unit,
    onExport: (List<CrashRecord>) -> Boolean,
    onReport: (CrashRecord) -> Boolean,
    onDeleteRecord: (CrashRecord) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<CrashRecord?>(null) }
    var reportRecord by remember { mutableStateOf<CrashRecord?>(null) }
    val ordered = remember(records) { records.sortedByDescending { it.timestamp } }
    val selected = ordered.firstOrNull { it.id == selectedId }

    BackHandler {
        if (selected != null) selectedId = null else onBack()
    }

    fun export(targets: List<CrashRecord>) {
        if (!onExport(targets)) {
            scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.error_log_export_failed)) }
        }
    }

    fun report(record: CrashRecord) {
        if (!onReport(record)) {
            scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.error_log_report_failed)) }
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("error_log_root"),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = { if (selected != null) selectedId = null else onBack() },
                        colors = ootIconButtonColors(),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.error_log_back),
                        )
                    }
                },
                title = {
                    Text(
                        text = stringResource(
                            if (selected != null) R.string.error_log_detail_title else R.string.error_log_title,
                        ),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
            )
        },
    ) { padding ->
        if (selected != null) {
            RecordDetail(
                record = selected,
                onExport = { export(listOf(selected)) },
                onReport = { reportRecord = selected },
                onDelete = { pendingDelete = selected },
                modifier = Modifier.padding(padding),
            )
        } else if (ordered.isEmpty()) {
            EmptyState(modifier = Modifier.padding(padding))
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = OotSpacing.screenHorizontal,
                        vertical = OotSpacing.sm,
                    ),
                    verticalArrangement = Arrangement.spacedBy(OotSpacing.xs),
                ) {
                    items(ordered, key = { it.id.ifBlank { "${it.timestamp}-${it.kind}" } }) { record ->
                        RecordRow(record = record, onClick = { selectedId = record.id })
                    }
                }
                Actions(
                    onExport = { export(ordered) },
                    onDelete = { showClearDialog = true },
                    deleteLabel = R.string.error_log_clear,
                    modifier = Modifier.padding(
                        start = OotSpacing.screenHorizontal,
                        end = OotSpacing.screenHorizontal,
                        bottom = OotSpacing.lg,
                        top = OotSpacing.xs,
                    ),
                )
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            shape = DialogCorner,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            title = {
                Text(
                    text = stringResource(R.string.error_log_clear_title),
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.error_log_clear_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        selectedId = null
                        onClear()
                        scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.error_log_cleared)) }
                    },
                    colors = ootTextButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(R.string.error_log_clear_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }, colors = ootTextButtonColors()) {
                    Text(stringResource(R.string.error_log_clear_cancel))
                }
            },
        )
    }

    pendingDelete?.let { record ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            shape = DialogCorner,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            title = {
                Text(
                    text = stringResource(R.string.error_log_delete_title),
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.error_log_delete_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        selectedId = null
                        onDeleteRecord(record)
                        scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.error_log_deleted)) }
                    },
                    colors = ootTextButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(R.string.error_log_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }, colors = ootTextButtonColors()) {
                    Text(stringResource(R.string.error_log_clear_cancel))
                }
            },
        )
    }

    reportRecord?.let { record ->
        IssuePreviewDialog(
            preview = remember(record) { ErrorReport.issuePreview(record) },
            onDismiss = { reportRecord = null },
            onOpen = {
                reportRecord = null
                report(record)
            },
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(OotSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_info_line),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp),
        )
        Spacer(modifier = Modifier.height(OotSpacing.md))
        Text(
            text = stringResource(R.string.error_log_empty_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(OotSpacing.xs))
        Text(
            text = stringResource(R.string.error_log_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RecordRow(record: CrashRecord, onClick: () -> Unit) {
    Surface(
        shape = CardCorner,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.extendedColors.hairline),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("error_log_row_${record.id}")
            .clickable(
                onClickLabel = stringResource(R.string.error_log_open_record),
                role = Role.Button,
                onClick = onClick,
            ),
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = withNumericSpans(formatRecordTime(record.timestamp), OotNumeric.toSpanStyle()),
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            supportingContent = {
                Text(
                    text = reasonLine(record),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            trailingContent = {
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

@Composable
private fun reasonLine(record: CrashRecord): String {
    val label = stringResource(record.kindLabelRes())
    val reason = Redactor.redact(record.summary).replace('\n', ' ').trim()
    return if (reason.isBlank()) label else "$label · $reason"
}

@Composable
private fun RecordDetail(
    record: CrashRecord,
    onExport: () -> Unit,
    onReport: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("error_log_detail")
            .verticalScroll(rememberScrollState())
            .padding(horizontal = OotSpacing.screenHorizontal, vertical = OotSpacing.sm),
    ) {
        Surface(
            shape = CardCorner,
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.extendedColors.hairline),
            shadowElevation = 0.dp,
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(OotSpacing.md)) {
                DetailRow(
                    label = stringResource(R.string.error_log_time),
                    value = formatRecordTime(record.timestamp),
                    numeric = true,
                )
                GroupDivider()
                DetailRow(
                    label = stringResource(R.string.error_log_kind),
                    value = stringResource(record.kindLabelRes()),
                )
                GroupDivider()
                DetailRow(
                    label = stringResource(R.string.error_log_app_version),
                    value = "${record.appVersion} (${record.appVersionCode})",
                    numeric = true,
                )
                GroupDivider()
                DetailRow(
                    label = stringResource(R.string.error_log_android_version),
                    value = record.androidVersion,
                )
                GroupDivider()
                DetailRow(
                    label = stringResource(R.string.error_log_device),
                    value = record.device,
                )
                if (!record.thread.isNullOrBlank()) {
                    GroupDivider()
                    DetailRow(
                        label = stringResource(R.string.error_log_thread),
                        value = record.thread,
                    )
                }
            }
        }

        Text(
            text = stringResource(R.string.error_log_detail),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = OotSpacing.lg, bottom = OotSpacing.xs),
        )
        SelectionContainer {
            Text(
                text = Redactor.redact(record.detail),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(modifier = Modifier.height(OotSpacing.lg))
        Actions(
            onExport = onExport,
            onReport = onReport,
            onDelete = onDelete,
            deleteLabel = R.string.error_log_delete_record,
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String, numeric: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = OotSpacing.xs),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(110.dp),
        )
        Text(
            text = if (numeric) withNumericSpans(value, OotNumeric.toSpanStyle()) else AnnotatedString(value),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun GroupDivider() {
    HorizontalDivider(color = MaterialTheme.extendedColors.hairline)
}

@Composable
private fun Actions(
    onExport: () -> Unit,
    onDelete: () -> Unit,
    deleteLabel: Int,
    modifier: Modifier = Modifier,
    onReport: (() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(OotSpacing.xs)) {
        if (onReport != null) {
            Button(
                onClick = onReport,
                shape = ButtonCorner,
                colors = ootInkButtonColors(),
                elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("error_log_report_action"),
            ) {
                Icon(Icons.Outlined.BugReport, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.error_log_report))
            }
        }
        OutlinedButton(
            onClick = onExport,
            shape = ButtonCorner,
            colors = ootOutlinedButtonColors(),
            border = BorderStroke(1.dp, MaterialTheme.extendedColors.hairlineStrong),
            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.error_log_export))
        }
        TextButton(
            onClick = onDelete,
            colors = ootTextButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("error_log_delete_action"),
        ) {
            Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(deleteLabel))
        }
    }
}

@Composable
private fun IssuePreviewDialog(
    preview: IssuePreview,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = DialogCorner,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = {
            Text(
                text = stringResource(R.string.error_log_report),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.error_log_report_notice),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(OotSpacing.md))
                Surface(
                    shape = CardCorner,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.extendedColors.hairline),
                    shadowElevation = 0.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .testTag("error_log_report_preview"),
                ) {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(OotSpacing.sm),
                    ) {
                        Text(
                            text = stringResource(R.string.error_log_report_title_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = preview.title,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.testTag("error_log_report_title"),
                        )
                        Spacer(modifier = Modifier.height(OotSpacing.sm))
                        Text(
                            text = stringResource(R.string.error_log_report_body_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = preview.body,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.testTag("error_log_report_body"),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onOpen, colors = ootTextButtonColors()) {
                Text(stringResource(R.string.error_log_report_open))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, colors = ootTextButtonColors()) {
                Text(stringResource(R.string.error_log_report_cancel))
            }
        },
    )
}
