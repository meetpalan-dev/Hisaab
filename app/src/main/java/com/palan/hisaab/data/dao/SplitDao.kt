package com.palan.hisaab.data.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import com.palan.hisaab.data.entity.SplitParticipantRecord
import com.palan.hisaab.data.entity.SplitRecord
import kotlinx.coroutines.flow.Flow

data class SplitRecordWithParticipants(
    @Embedded val record: SplitRecord,
    @Relation(parentColumn = "id", entityColumn = "splitId")
    val participants: List<SplitParticipantRecord>
)

@Dao
interface SplitDao {

    @Insert
    suspend fun insertRecord(record: SplitRecord): Long

    @Insert
    suspend fun insertParticipants(participants: List<SplitParticipantRecord>)

    @Transaction
    @Query("SELECT * FROM split_records ORDER BY date DESC, id DESC")
    fun observeAll(): Flow<List<SplitRecordWithParticipants>>

    /** Looks up the split (with its full participant breakdown) that a "Me" total-outlay transaction belongs to, if any — used to recompute that transaction's live remaining amount. */
    @Transaction
    @Query("SELECT * FROM split_records WHERE meTransactionId = :transactionId LIMIT 1")
    suspend fun findByMeTransactionId(transactionId: Long): SplitRecordWithParticipants?

    /** Reactive version of a single split lookup, for the Split Details screen — updates live as settlements happen. */
    @Transaction
    @Query("SELECT * FROM split_records WHERE id = :splitId LIMIT 1")
    fun observeById(splitId: Long): Flow<SplitRecordWithParticipants?>

    /** Looks up the split that a participant's own linked share transaction belongs to, if any — used so tapping any Split-related transaction (payer's total or a participant's share) opens Split Details instead of the generic Edit Transaction dialog. */
    @Transaction
    @Query(
        """
        SELECT sr.* FROM split_records sr
        INNER JOIN split_participants sp ON sp.splitId = sr.id
        WHERE sp.transactionId = :transactionId
        LIMIT 1
        """
    )
    suspend fun findByParticipantTransactionId(transactionId: Long): SplitRecordWithParticipants?

    @Query("SELECT * FROM split_records WHERE id = :splitId LIMIT 1")
    suspend fun getRecordById(splitId: Long): SplitRecord?

    @Query("SELECT * FROM split_participants WHERE splitId = :splitId")
    suspend fun getParticipantsForSplit(splitId: Long): List<SplitParticipantRecord>

    @androidx.room.Update
    suspend fun updateRecord(record: SplitRecord)
}
