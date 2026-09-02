package com.palan.hisaab.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row = "this repayment transaction covered ₹X of that outstanding loan transaction."
 * A single repayment can have several of these (one per hisaab it clears/part-clears);
 * a single loan transaction can be covered by several of these over time (partial repayments).
 * Deleting either side cascades — deleting the repayment un-does the settlement,
 * deleting the loan transaction just drops the now-meaningless allocation row.
 */
@Entity(
    tableName = "repayment_allocations",
    foreignKeys = [
        ForeignKey(
            entity = Transaction::class,
            parentColumns = ["id"],
            childColumns = ["repaymentTransactionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Transaction::class,
            parentColumns = ["id"],
            childColumns = ["targetTransactionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("repaymentTransactionId"), Index("targetTransactionId")]
)
data class RepaymentAllocation(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val repaymentTransactionId: Long,
    val targetTransactionId: Long,
    val allocatedAmountMinor: Long,
    val createdAt: Long = System.currentTimeMillis()
)
