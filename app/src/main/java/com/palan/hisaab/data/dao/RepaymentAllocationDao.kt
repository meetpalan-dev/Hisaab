package com.palan.hisaab.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import com.palan.hisaab.data.entity.RepaymentAllocation
import com.palan.hisaab.data.entity.Transaction
import kotlinx.coroutines.flow.Flow

/** An outstanding loan transaction plus how much of it is still unpaid. */
data class OutstandingHisaab(
    @Embedded val transaction: Transaction,
    val remainingMinor: Long
)

data class TargetAllocatedSum(
    val targetTransactionId: Long,
    val allocatedMinor: Long
)

@Dao
interface RepaymentAllocationDao {

    @Insert
    suspend fun insert(allocation: RepaymentAllocation): Long

    @Insert
    suspend fun insertAll(allocations: List<RepaymentAllocation>)

    @Delete
    suspend fun delete(allocation: RepaymentAllocation)

    @Query("SELECT * FROM repayment_allocations WHERE repaymentTransactionId = :repaymentTransactionId")
    suspend fun getForRepayment(repaymentTransactionId: Long): List<RepaymentAllocation>

    @Query("SELECT * FROM repayment_allocations WHERE targetTransactionId = :targetTransactionId")
    suspend fun getForTarget(targetTransactionId: Long): List<RepaymentAllocation>

    @Query("SELECT COALESCE(SUM(allocatedAmountMinor), 0) FROM repayment_allocations WHERE targetTransactionId = :targetTransactionId")
    suspend fun sumForTarget(targetTransactionId: Long): Long

    /**
     * Every transaction of any type in [types] in this account that still has money
     * outstanding — i.e. not manually settled/cleared and not fully covered by
     * repayment allocations yet. Used to build the repayment-allocation picker.
     * [types] lets a repayment match transactions in *either* direction it's eligible
     * for (e.g. a Spent-repayment can settle an outstanding Received transaction or a
     * legacy Loan Taken one) — a single fixed type here was the bug that made the
     * allocation screen show "nothing outstanding" even when eligible hisaabs existed.
     */
    @Query(
        """
        SELECT t.*, (t.amountMinor - COALESCE(alloc.allocated, 0)) AS remainingMinor
        FROM transactions t
        LEFT JOIN (
            SELECT targetTransactionId, SUM(allocatedAmountMinor) AS allocated
            FROM repayment_allocations
            GROUP BY targetTransactionId
        ) alloc ON alloc.targetTransactionId = t.id
        WHERE t.accountId = :accountId AND t.type IN (:types) AND t.settled = 0 AND t.isRepayment = 0
          AND (t.amountMinor - COALESCE(alloc.allocated, 0)) > 0
        ORDER BY t.date ASC, t.id ASC
        """
    )
    suspend fun getOutstanding(accountId: Long, types: List<com.palan.hisaab.data.entity.TransactionType>): List<OutstandingHisaab>

    /** Live map (as a list of rows) of how much of each loan transaction in this account has been allocated against so far — used to render "Repaid ₹X / Remaining ₹Y" on partially-paid rows. */
    @Query(
        """
        SELECT ra.targetTransactionId AS targetTransactionId, SUM(ra.allocatedAmountMinor) AS allocatedMinor
        FROM repayment_allocations ra
        INNER JOIN transactions t ON t.id = ra.targetTransactionId
        WHERE t.accountId = :accountId
        GROUP BY ra.targetTransactionId
        """
    )
    fun observeAllocatedSumsForAccount(accountId: Long): Flow<List<TargetAllocatedSum>>
}
