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
    /** True for a RECEIVED/SPENT transaction created via "Settle Hisaab" (automatically applied against the account's outstanding hisaab), or generated automatically when marking a transaction Settled by hand (see HisaabRepository.setSettled) — either way, its amount is counted normally in the received/spent totals, except for whatever portion was allocated to an isLoan target, which is excluded there since it's already reflected by that loan's own outstanding total shrinking instead. */
    val isRepayment: Boolean = false,
    /**
     * Marks this RECEIVED or SPENT transaction as a loan rather than an ordinary one — replaces
     * the old separate LOAN_GIVEN/LOAN_TAKEN transaction types. A SPENT+isLoan transaction behaves
     * like the old "Loan Given" (a receivable — it ADDS to the account balance, opposite the sign
     * a plain Spent normally has) and a RECEIVED+isLoan transaction behaves like the old
     * "Loan Taken" (a liability — it SUBTRACTS from the balance, opposite a plain Received). Unlike
     * a plain transaction, an isLoan transaction's contribution to the balance is netted down by
     * however much of it has actually been settled, rather than always counting at face value —
     * see HisaabRepository.observeAccountSummary.
     */
    val isLoan: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
