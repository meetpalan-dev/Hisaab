package com.palan.hisaab.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "hisaab_settings")

data class HisaabSettings(
    val useMaterialYou: Boolean = false,
    val autoFillTodayDate: Boolean = true
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val MATERIAL_YOU = booleanPreferencesKey("use_material_you")
        val AUTO_FILL_DATE = booleanPreferencesKey("auto_fill_today_date")
    }

    val settings: Flow<HisaabSettings> = context.settingsDataStore.data.map { prefs ->
        HisaabSettings(
            useMaterialYou = prefs[Keys.MATERIAL_YOU] ?: false,
            autoFillTodayDate = prefs[Keys.AUTO_FILL_DATE] ?: true
        )
    }

    suspend fun setUseMaterialYou(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.MATERIAL_YOU] = enabled }
    }

    suspend fun setAutoFillTodayDate(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.AUTO_FILL_DATE] = enabled }
    }
}
