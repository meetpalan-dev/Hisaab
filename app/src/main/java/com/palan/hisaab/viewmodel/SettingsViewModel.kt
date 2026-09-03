package com.palan.hisaab.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palan.hisaab.data.AccentColor
import com.palan.hisaab.data.HisaabSettings
import com.palan.hisaab.data.SettingsRepository
import com.palan.hisaab.data.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {

    val settings: StateFlow<HisaabSettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HisaabSettings())

    fun setUseMaterialYou(enabled: Boolean) {
        viewModelScope.launch { repository.setUseMaterialYou(enabled) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }

    fun setAccentColor(color: AccentColor) {
        viewModelScope.launch { repository.setAccentColor(color) }
    }

    fun setGroupByMonth(enabled: Boolean) {
        viewModelScope.launch { repository.setGroupByMonth(enabled) }
    }

    fun setShowCategories(enabled: Boolean) {
        viewModelScope.launch { repository.setShowCategories(enabled) }
    }

    fun setSortNewestFirst(enabled: Boolean) {
        viewModelScope.launch { repository.setSortNewestFirst(enabled) }
    }

    fun setSwipeToSettleEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setSwipeToSettleEnabled(enabled) }
    }

    fun setConfirmBeforeSettle(enabled: Boolean) {
        viewModelScope.launch { repository.setConfirmBeforeSettle(enabled) }
    }

    fun setUndoEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setUndoEnabled(enabled) }
    }

    fun setHideZeroBalanceAccounts(enabled: Boolean) {
        viewModelScope.launch { repository.setHideZeroBalanceAccounts(enabled) }
    }

    fun setAutoFillTodayDate(enabled: Boolean) {
        viewModelScope.launch { repository.setAutoFillTodayDate(enabled) }
    }
}
