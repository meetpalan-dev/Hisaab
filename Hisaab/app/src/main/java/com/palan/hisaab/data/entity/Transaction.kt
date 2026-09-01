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
    /** Only meaningful for LOAN_GIVEN/LOAN_TAKEN — marks a loan as repaid without deleting it, so it drops out of the outstanding-loan totals but stays visible in history. */
    val settled: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
