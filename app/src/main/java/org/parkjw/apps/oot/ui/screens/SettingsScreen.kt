package org.parkjw.apps.oot.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import org.parkjw.apps.oot.R
import org.parkjw.apps.oot.data.model.ProviderAccount
import org.parkjw.apps.oot.i18n.AppLanguage
import org.parkjw.apps.oot.ui.theme.CardCorner
import org.parkjw.apps.oot.ui.theme.OotNumeric
import org.parkjw.apps.oot.ui.theme.OotSpacing
import org.parkjw.apps.oot.ui.theme.extendedColors
import org.parkjw.apps.oot.ui.theme.ootIconButtonColors
import org.parkjw.apps.oot.ui.theme.withNumericSpans
import org.parkjw.apps.oot.update.UpdateAction
import org.parkjw.apps.oot.update.UpdateCheckBlock
import org.parkjw.apps.oot.update.UpdateUiState
import org.parkjw.apps.oot.update.displayVersion

internal const val SOURCE_CODE_URL = "https://github.com/parkjangwon/oot"

private val REFRESH_INTERVALS = listOf(1, 5, 15, 30, 60)
private val SHORT_REFRESH_INTERVALS = setOf(1, 5)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    isServiceEnabled: Boolean,
    refreshIntervalMinutes: Int,
    accounts: List<ProviderAccount>,
    notificationHiddenIds: Set<String>,
    autoUpdateCheckEnabled: Boolean,
    keepScreenOnEnabled: Boolean,
    language: AppLanguage,
    versionName: String,
    updateState: UpdateUiState,
    onBack: () -> Unit,
    onToggleService: (Boolean) -> Unit,
    onSetRefreshInterval: (Int) -> Unit,
    onNotificationShownChange: (accountId: String, shown: Boolean) -> Unit,
    onAutoUpdateCheckChange: (Boolean) -> Unit,
    onKeepScreenOnChange: (Boolean) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onUpdateAction: (UpdateAction) -> Unit,
    onOpenSourceCode: () -> Unit,
    onOpenErrorLog: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack, colors = ootIconButtonColors()) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back),
                        )
                    }
                },
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = OotSpacing.lg),
        ) {
            // First, so it can be found without reading the current language.
            SectionHeader(stringResource(R.string.settings_section_language))
            SettingsGroup {
                LanguageSegment(selected = language, onSelect = onLanguageChange)
            }

            SectionHeader(stringResource(R.string.settings_section_notifications))
            SettingsGroup {
                SwitchRow(
                    title = stringResource(R.string.settings_persistent_notification),
                    supporting = stringResource(R.string.settings_persistent_notification_desc),
                    checked = isServiceEnabled,
                    onCheckedChange = onToggleService,
                )
                GroupDivider()
                IntervalSegment(
                    selectedMinutes = refreshIntervalMinutes,
                    onSelect = onSetRefreshInterval,
                )
            }

            SectionHeader(stringResource(R.string.settings_section_display))
            SettingsGroup {
                SwitchRow(
                    title = stringResource(R.string.settings_keep_screen_on),
                    supporting = stringResource(R.string.settings_keep_screen_on_desc),
                    checked = keepScreenOnEnabled,
                    onCheckedChange = onKeepScreenOnChange,
                )
            }

            if (accounts.isNotEmpty()) {
                SectionHeader(stringResource(R.string.settings_section_notification_visibility))
                SettingsGroup {
                    accounts.forEachIndexed { index, account ->
                        if (index > 0) GroupDivider()
                        SwitchRow(
                            title = account.name.ifBlank { account.provider.displayName },
                            checked = account.id !in notificationHiddenIds,
                            onCheckedChange = { onNotificationShownChange(account.id, it) },
                            leading = { VendorMark(provider = account.provider, modifier = Modifier.clearAndSetSemantics {}) },
                        )
                    }
                }
            }

            SectionHeader(stringResource(R.string.settings_section_update))
            SettingsGroup {
                UpdateCheckBlock(
                    state = updateState,
                    currentVersion = versionName,
                    onAction = onUpdateAction,
                )
                GroupDivider()
                SwitchRow(
                    title = stringResource(R.string.update_auto),
                    supporting = stringResource(R.string.update_auto_desc),
                    checked = autoUpdateCheckEnabled,
                    onCheckedChange = onAutoUpdateCheckChange,
                )
            }

            SectionHeader(stringResource(R.string.settings_section_info))
            SettingsGroup {
                ListItem(
                    headlineContent = {
                        Text(
                            text = stringResource(R.string.settings_error_log),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    supportingContent = {
                        Text(
                            text = stringResource(R.string.settings_error_log_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenErrorLog),
                )
                GroupDivider()
                ListItem(
                    headlineContent = {
                        Text(
                            text = stringResource(R.string.settings_version),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    trailingContent = {
                        Text(
                            text = withNumericSpans(displayVersion(versionName), OotNumeric.toSpanStyle()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.fillMaxWidth(),
                )
                GroupDivider()
                ListItem(
                    headlineContent = {
                        Text(
                            text = stringResource(R.string.settings_source_code),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    trailingContent = {
                        Icon(
                            painter = painterResource(R.drawable.ic_open_external),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenSourceCode),
                )
            }
        }
    }
}

@Composable
private fun IntervalSegment(
    selectedMinutes: Int,
    onSelect: (Int) -> Unit,
) {
    val ink = MaterialTheme.extendedColors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = OotSpacing.xs),
    ) {
        Text(
            text = stringResource(R.string.settings_refresh_interval),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(
                horizontal = OotSpacing.md,
                vertical = OotSpacing.xs,
            ),
        )
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = OotSpacing.md, vertical = OotSpacing.xs),
        ) {
            REFRESH_INTERVALS.forEachIndexed { index, minutes ->
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index, REFRESH_INTERVALS.size),
                    onClick = { onSelect(minutes) },
                    selected = selectedMinutes == minutes,
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = ink.inkButton,
                        activeContentColor = ink.onInkButton,
                        inactiveContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        inactiveContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    border = SegmentedButtonDefaults.borderStroke(ink.hairlineStrong),
                    label = {
                        Text(
                            text = stringResource(R.string.settings_refresh_interval_minutes, minutes),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    },
                )
            }
        }
        if (selectedMinutes in SHORT_REFRESH_INTERVALS) {
            Text(
                text = stringResource(R.string.settings_refresh_interval_battery_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    horizontal = OotSpacing.md,
                    vertical = OotSpacing.xs,
                ),
            )
        }
    }
}

@Composable
private fun LanguageSegment(
    selected: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
) {
    val ink = MaterialTheme.extendedColors
    val options = listOf(
        AppLanguage.KOREAN to stringResource(R.string.settings_language_korean),
        AppLanguage.ENGLISH to stringResource(R.string.settings_language_english),
    )
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = OotSpacing.md, vertical = OotSpacing.sm)
            .testTag("settings_language"),
    ) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                onClick = { onSelect(value) },
                selected = selected == value,
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = ink.inkButton,
                    activeContentColor = ink.onInkButton,
                    inactiveContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    inactiveContentColor = MaterialTheme.colorScheme.onSurface,
                ),
                border = SegmentedButtonDefaults.borderStroke(ink.hairlineStrong),
                label = {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(
                start = OotSpacing.screenHorizontal,
                end = OotSpacing.screenHorizontal,
                top = OotSpacing.lg,
                bottom = OotSpacing.xs,
            )
            .semantics { heading() },
    )
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = CardCorner,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.extendedColors.hairline),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = OotSpacing.screenHorizontal),
    ) {
        Column(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
private fun GroupDivider() {
    HorizontalDivider(color = MaterialTheme.extendedColors.hairline)
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    supporting: String? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    ListItem(
        headlineContent = {
            Text(title, style = MaterialTheme.typography.bodyLarge)
        },
        leadingContent = leading,
        supportingContent = supporting?.let {
            {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall.copy(
                        lineBreak = LineBreak.Paragraph.copy(wordBreak = LineBreak.WordBreak.Phrase),
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    uncheckedBorderColor = MaterialTheme.extendedColors.hairlineStrong,
                ),
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                onValueChange = onCheckedChange,
                role = Role.Switch,
            ),
    )
}
