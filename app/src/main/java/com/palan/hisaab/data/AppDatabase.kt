package com.palan.hisaab.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.palan.hisaab.data.dao.AccountDao
import com.palan.hisaab.data.dao.RepaymentAllocationDao
import com.palan.hisaab.data.dao.SplitDao
import com.palan.hisaab.data.dao.TransactionDao
import com.palan.hisaab.data.entity.Account
import com.palan.hisaab.data.entity.RepaymentAllocation
import com.palan.hisaab.data.entity.SplitParticipantRecord
import com.palan.hisaab.data.entity.SplitRecord
import com.palan.hisaab.data.entity.Transaction

@Database(
    entities = [Account::class, Transaction::class, RepaymentAllocation::class, SplitRecord::class, SplitParticipantRecord::class],
    version = 6,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun accountDao(): AccountDao
    abstract fun transactionDao(): TransactionDao
    abstract fun repaymentAllocationDao(): RepaymentAllocationDao
    abstract fun splitDao(): SplitDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE accounts ADD COLUMN phoneNumber TEXT")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN settled INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN isRepayment INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS repayment_allocations (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        repaymentTransactionId INTEGER NOT NULL,
                        targetTransactionId INTEGER NOT NULL,
                        allocatedAmountMinor INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY(repaymentTransactionId) REFERENCES transactions(id) ON DELETE CASCADE,
                        FOREIGN KEY(targetTransactionId) REFERENCES transactions(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_repayment_allocations_repaymentTransactionId ON repayment_allocations(repaymentTransactionId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_repayment_allocations_targetTransactionId ON repayment_allocations(targetTransactionId)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS split_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        description TEXT NOT NULL,
                        totalMinor INTEGER NOT NULL,
                        payerName TEXT NOT NULL,
                        date INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS split_participants (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        splitId INTEGER NOT NULL,
                        name TEXT NOT NULL,
                        amountMinor INTEGER NOT NULL,
                        isPayer INTEGER NOT NULL,
                        recorded INTEGER NOT NULL,
                        FOREIGN KEY(splitId) REFERENCES split_records(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_split_participants_splitId ON split_participants(splitId)")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE split_records ADD COLUMN meTransactionId INTEGER")
                db.execSQL("ALTER TABLE split_participants ADD COLUMN transactionId INTEGER")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "hisaab.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).build().also { INSTANCE = it }
            }
        }
    }
}
