package com.palan.hisaab.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * amountMinor stores the amount in "paise" (1/100 rupee) as a Long.
 * This avoids floating point rounding errors entirely — all arithmetic
 * (totals, balances) is done in integer paise and only converted to a
 * BigDecimal/rupee display string at the UI layer.
 */
@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = Account::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("accountId")]
)
data class Transaction(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val accountId: Long,
    val type: TransactionType,
    val amountMinor: Long,
    val description: String,
    /** Null when the user left the date unset and auto-fill-today is disabled in Settings. */
    val date: Long?,
    val category: String? = null,
    /** Active/Cleared status — false = Active (still outstanding), true = Cleared/settled. Applies to every transaction (via swipe gesture, the Edit screen's Mark as Settled button, or automatically once a repayment allocation fully covers it). Never deletes the transaction; it stays visible in the All tab and in history either way. */
    val settled: Boolean = false,
    /** True for a RECEIVED/SPENT transaction explicitly marked "Repayment / Settle Hisaab". Its own amount is excluded from the normal received/spent totals — the balance effect instead comes entirely from the RepaymentAllocation rows it created, which reduce the outstanding amount on one or more loan transactions. */
    val isRepayment: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
