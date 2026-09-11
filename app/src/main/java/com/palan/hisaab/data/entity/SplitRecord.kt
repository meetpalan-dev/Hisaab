package com.palan.hisaab.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One past "Split Expense", for the Split History screen. The actual money-owed effect (if any) was already posted as normal transactions on the relevant accounts when the split was made — this table exists purely so the full picture (total, who paid, everyone's share) stays visible afterward, since a single account's ledger only ever shows its own slice of a split. */
@Entity(tableName = "split_records")
data class SplitRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val description: String,
    val totalMinor: Long,
    val payerName: String,
    val date: Long,
    val createdAt: Long = System.currentTimeMillis(),
    /** When the payer is "Me", the id of the single Spent transaction recording the FULL amount fronted (not just my own share) — its live "remaining" is computed by [com.palan.hisaab.data.HisaabRepository.computeSplitTotalOverrides] from how much each other participant has settled on their own account, so it shrinks toward just my own share as they pay me back. Null when the payer isn't me (there's no such combined entry to track). */
    val meTransactionId: Long? = null
)
