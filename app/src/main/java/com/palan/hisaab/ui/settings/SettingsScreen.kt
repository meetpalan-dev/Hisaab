package com.palan.hisaab.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.palan.hisaab.data.AccentColor
import com.palan.hisaab.data.SettingsRepository
import com.palan.hisaab.data.ThemeMode
import com.palan.hisaab.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository,
    onBack: () -> Unit
) {
    val factory = remember { viewModelFactory { initializer { SettingsViewModel(settingsRepository) } } }
    val viewModel: SettingsViewModel = viewModel(factory = factory)
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current
    val versionName = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "—"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxWidth().padding(padding)) {
            item { SectionHeader("Appearance") }
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Text("Theme", style = MaterialTheme.typography.titleMedium)
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 6.dp))
                    Row {
                        listOf(ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark", ThemeMode.SYSTEM to "Follow system").forEach { (mode, label) ->
                            FilterChip(
                                selected = settings.themeMode == mode && !settings.useMaterialYou,
                                onClick = { viewModel.setThemeMode(mode); if (settings.useMaterialYou) viewModel.setUseMaterialYou(false) },
                                label = { Text(label) },
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                    }
                }
            }
            item {
                SettingRow(
                    title = "Material You",
                    subtitle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                        "Match app colors to your wallpaper (Android 12+) — overrides the theme choice above"
                    else
                        "Requires Android 12 or newer — not available on this device",
                    checked = settings.useMaterialYou,
                    enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                    onCheckedChange = viewModel::setUseMaterialYou
                )
            }
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Text("Accent color", style = MaterialTheme.typography.titleMedium)
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 6.dp))
                    Row {
                        listOf(AccentColor.GOLD to "Gold", AccentColor.BLUE to "Blue", AccentColor.GREEN to "Green", AccentColor.ROSE to "Rose").forEach { (color, label) ->
                            FilterChip(
                                selected = settings.accentColor == color,
                                onClick = { viewModel.setAccentColor(color) },
                                label = { Text(label) },
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                    }
                }
            }

            item { HorizontalDivider() }
            item { SectionHeader("Transaction Display") }
            item {
                SettingRow(
                    title = "Group by month",
                    subtitle = "Show Active/All/Cleared transaction lists grouped under month headers",
                    checked = settings.groupByMonth,
                    onCheckedChange = viewModel::setGroupByMonth
                )
            }
            item {
                SettingRow(
                    title = "Show categories",
                    subtitle = "Show each transaction's category tag in the list",
                    checked = settings.showCategories,
                    onCheckedChange = viewModel::setShowCategories
                )
            }
            item {
                SettingRow(
                    title = "Newest first",
                    subtitle = if (settings.sortNewestFirst) "Transactions are sorted newest to oldest" else "Transactions are sorted oldest to newest",
                    checked = settings.sortNewestFirst,
                    onCheckedChange = viewModel::setSortNewestFirst
                )
            }

            item { HorizontalDivider() }
            item { SectionHeader("Settlement") }
            item {
                SettingRow(
                    title = "Swipe to settle",
                    subtitle = "Swipe right in Active to settle, swipe left in Cleared to restore",
                    checked = settings.swipeToSettleEnabled,
                    onCheckedChange = viewModel::setSwipeToSettleEnabled
                )
            }
            item {
                SettingRow(
                    title = "Confirm before settling",
                    subtitle = "Ask before marking a transaction as settled",
                    checked = settings.confirmBeforeSettle,
                    enabled = settings.swipeToSettleEnabled,
                    onCheckedChange = viewModel::setConfirmBeforeSettle
                )
            }
            item {
                SettingRow(
                    title = "Enable Undo",
                    subtitle = "Show a brief Undo option after settling or restoring a transaction",
                    checked = settings.undoEnabled,
                    enabled = settings.swipeToSettleEnabled,
                    onCheckedChange = viewModel::setUndoEnabled
                )
            }

            item { HorizontalDivider() }
            item { SectionHeader("Account List") }
            item {
                SettingRow(
                    title = "Hide zero-balance accounts",
                    subtitle = "Don't show accounts on the home screen whose balance is exactly ₹0",
                    checked = settings.hideZeroBalanceAccounts,
                    onCheckedChange = viewModel::setHideZeroBalanceAccounts
                )
            }
            item {
                SettingRow(
                    title = "Auto-fill today's date",
                    subtitle = "When off, a transaction saved without picking a date is stored with no date instead of defaulting to today",
                    checked = settings.autoFillTodayDate,
                    onCheckedChange = viewModel::setAutoFillTodayDate
                )
            }

            item { HorizontalDivider() }
            item { SectionHeader("Backup and Data") }
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text(
                        "Import Hisab, Export Backup, and Import Backup are on the home screen's ⋮ menu.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item { HorizontalDivider() }
            item { SectionHeader("About") }
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Text("हिसाब (Hisaab)", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Version $versionName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item { androidx.compose.foundation.layout.Spacer(Modifier.padding(24.dp)) }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
