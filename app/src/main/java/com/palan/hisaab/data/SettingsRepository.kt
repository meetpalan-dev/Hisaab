package com.palan.hisaab.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "hisaab_settings")

enum class ThemeMode { LIGHT, DARK, SYSTEM }

enum class AccentColor { GOLD, BLUE, GREEN, ROSE }

data class HisaabSettings(
    // Appearance
    val useMaterialYou: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val accentColor: AccentColor = AccentColor.GOLD,
    // Transaction display
    val groupByMonth: Boolean = true,
    val showCategories: Boolean = true,
    val sortNewestFirst: Boolean = true,
    // Settlement
    val swipeToSettleEnabled: Boolean = true,
    val confirmBeforeSettle: Boolean = false,
    val undoEnabled: Boolean = true,
    // Account list
    val hideZeroBalanceAccounts: Boolean = false,
    // Misc (existing)
    val autoFillTodayDate: Boolean = true
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val MATERIAL_YOU = booleanPreferencesKey("use_material_you")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ACCENT_COLOR = stringPreferencesKey("accent_color")
        val GROUP_BY_MONTH = booleanPreferencesKey("group_by_month")
        val SHOW_CATEGORIES = booleanPreferencesKey("show_categories")
        val SORT_NEWEST_FIRST = booleanPreferencesKey("sort_newest_first")
        val SWIPE_TO_SETTLE = booleanPreferencesKey("swipe_to_settle_enabled")
        val CONFIRM_BEFORE_SETTLE = booleanPreferencesKey("confirm_before_settle")
        val UNDO_ENABLED = booleanPreferencesKey("undo_enabled")
        val HIDE_ZERO_BALANCE = booleanPreferencesKey("hide_zero_balance_accounts")
        val AUTO_FILL_DATE = booleanPreferencesKey("auto_fill_today_date")
    }

    val settings: Flow<HisaabSettings> = context.settingsDataStore.data.map { prefs ->
        HisaabSettings(
            useMaterialYou = prefs[Keys.MATERIAL_YOU] ?: false,
            themeMode = prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.DARK,
            accentColor = prefs[Keys.ACCENT_COLOR]?.let { runCatching { AccentColor.valueOf(it) }.getOrNull() } ?: AccentColor.GOLD,
            groupByMonth = prefs[Keys.GROUP_BY_MONTH] ?: true,
            showCategories = prefs[Keys.SHOW_CATEGORIES] ?: true,
            sortNewestFirst = prefs[Keys.SORT_NEWEST_FIRST] ?: true,
            swipeToSettleEnabled = prefs[Keys.SWIPE_TO_SETTLE] ?: true,
            confirmBeforeSettle = prefs[Keys.CONFIRM_BEFORE_SETTLE] ?: false,
            undoEnabled = prefs[Keys.UNDO_ENABLED] ?: true,
            hideZeroBalanceAccounts = prefs[Keys.HIDE_ZERO_BALANCE] ?: false,
            autoFillTodayDate = prefs[Keys.AUTO_FILL_DATE] ?: true
        )
    }

    suspend fun setUseMaterialYou(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.MATERIAL_YOU] = enabled }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setAccentColor(color: AccentColor) {
        context.settingsDataStore.edit { it[Keys.ACCENT_COLOR] = color.name }
    }

    suspend fun setGroupByMonth(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.GROUP_BY_MONTH] = enabled }
    }

    suspend fun setShowCategories(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.SHOW_CATEGORIES] = enabled }
    }

    suspend fun setSortNewestFirst(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.SORT_NEWEST_FIRST] = enabled }
    }

    suspend fun setSwipeToSettleEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.SWIPE_TO_SETTLE] = enabled }
    }

    suspend fun setConfirmBeforeSettle(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.CONFIRM_BEFORE_SETTLE] = enabled }
    }

    suspend fun setUndoEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.UNDO_ENABLED] = enabled }
    }

    suspend fun setHideZeroBalanceAccounts(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.HIDE_ZERO_BALANCE] = enabled }
    }

    suspend fun setAutoFillTodayDate(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.AUTO_FILL_DATE] = enabled }
    }
}
