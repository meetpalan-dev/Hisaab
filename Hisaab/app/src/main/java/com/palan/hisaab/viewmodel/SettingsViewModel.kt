package com.palan.hisaab.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palan.hisaab.data.HisaabSettings
import com.palan.hisaab.data.SettingsRepository
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

    fun setAutoFillTodayDate(enabled: Boolean) {
        viewModelScope.launch { repository.setAutoFillTodayDate(enabled) }
    }
}
