package com.palan.hisaab

import android.app.Application
import com.palan.hisaab.data.AppDatabase
import com.palan.hisaab.data.HisaabRepository
import com.palan.hisaab.data.SettingsRepository

class HisaabApplication : Application() {
    val repository: HisaabRepository by lazy {
        val db = AppDatabase.getInstance(this)
        HisaabRepository(db.accountDao(), db.transactionDao())
    }
    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(this)
    }
}
