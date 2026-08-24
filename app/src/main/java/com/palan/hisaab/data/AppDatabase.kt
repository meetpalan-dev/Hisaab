package com.palan.hisaab.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.palan.hisaab.data.dao.AccountDao
import com.palan.hisaab.data.dao.TransactionDao
import com.palan.hisaab.data.entity.Account
import com.palan.hisaab.data.entity.Transaction

@Database(
    entities = [Account::class, Transaction::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun accountDao(): AccountDao
    abstract fun transactionDao(): TransactionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "hisaab.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
