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
}
