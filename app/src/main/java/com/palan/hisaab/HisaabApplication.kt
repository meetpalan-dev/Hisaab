package com.palan.hisaab

import android.app.Application
import com.palan.hisaab.data.AppDatabase
import com.palan.hisaab.data.HisaabRepository

class HisaabApplication : Application() {
    val repository: HisaabRepository by lazy {
        val db = AppDatabase.getInstance(this)
        HisaabRepository(db.accountDao(), db.transactionDao())
    }
}
