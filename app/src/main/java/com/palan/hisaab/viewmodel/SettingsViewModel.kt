package com.palan.hisaab.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palan.hisaab.data.HisaabRepository
import com.palan.hisaab.data.HisaabSettings
import com.palan.hisaab.data.SettingsRepository
import com.palan.hisaab.util.HisabTextImporter
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val hisaabRepository: HisaabRepository
) : ViewModel() {

    val settings: StateFlow<HisaabSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HisaabSettings())

    fun setUseMaterialYou(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setUseMaterialYou(enabled) }
    }

    fun setAutoFillTodayDate(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoFillTodayDate(enabled) }
    }

    /** Builds the full multi-account backup text, off the main thread. */
    fun exportAll(onResult: (String) -> Unit) {
        viewModelScope.launch {
            onResult(hisaabRepository.exportAllAccountsText())
        }
    }

    /**
     * Parses a full-backup text file and imports every account it contains.
     * Reports back either the number of accounts created, or a human-readable
     * failure reason — never throws into the caller.
     */
    fun importAll(text: String, onResult: (Result<Int>) -> Unit) {
        val parsed = HisabTextImporter.parseBackup(text)
        if (parsed.isEmpty()) {
            onResult(Result.failure(IllegalArgumentException("Couldn't find any accounts in that file.")))
            return
        }
        viewModelScope.launch {
            val count = hisaabRepository.importBackup(parsed)
            onResult(Result.success(count))
        }
    }
}
