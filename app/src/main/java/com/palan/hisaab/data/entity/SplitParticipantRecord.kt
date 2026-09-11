package com.palan.hisaab.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One participant's share within a [SplitRecord]. [recorded] is false for a non-payer,
 * non-"Me" participant's share when the payer also isn't "Me" — this app's accounts each
 * track a Me-vs-that-person ledger, so a debt between two other people has nowhere honest
 * to post to; it's kept here (in the split's history) so it's not silently lost, but no
 * transaction was created for it on any account.
 */
@Entity(
    tableName = "split_participants",
    foreignKeys = [
        ForeignKey(
            entity = SplitRecord::class,
            parentColumns = ["id"],
            childColumns = ["splitId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("splitId")]
)
data class SplitParticipantRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val splitId: Long,
    val name: String,
    val amountMinor: Long,
    val isPayer: Boolean,
    val recorded: Boolean,
    /** The id of the transaction actually posted for this participant's share (null when [recorded] is false, or for the payer's own share when the payer is "Me" — that share is folded into [SplitRecord.meTransactionId] instead of its own row). */
    val transactionId: Long? = null
)
