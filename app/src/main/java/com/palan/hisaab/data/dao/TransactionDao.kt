package com.palan.hisaab.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.palan.hisaab.data.entity.Transaction
import com.palan.hisaab.data.entity.TransactionType
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Insert
    suspend fun insert(transaction: Transaction): Long

    @Update
    suspend fun update(transaction: Transaction)

    @Delete
    suspend fun delete(transaction: Transaction)

    @Query("SELECT * FROM transactions WHERE accountId = :accountId ORDER BY date DESC, id DESC")
    fun observeForAccount(accountId: Long): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE accountId = :accountId ORDER BY date ASC, id ASC")
    suspend fun getForAccountOnce(accountId: Long): List<Transaction>

    @Query("SELECT * FROM transactions WHERE accountId = :accountId AND type = :type LIMIT 1")
    suspend fun getInitialBalance(accountId: Long, type: TransactionType = TransactionType.INITIAL_BALANCE): Transaction?

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): Transaction?

    /** Used for INITIAL_BALANCE. For RECEIVED/SPENT, use [observeIncomeExpenseSum] instead (excludes repayments). For LOAN_GIVEN/LOAN_TAKEN, use [observeOutstandingLoanSum] instead (nets out allocations). */
    @Query(
        """
        SELECT COALESCE(SUM(amountMinor), 0) FROM transactions
        WHERE accountId = :accountId AND type = :type AND settled = 0
        """
    )
    fun observeSumByType(accountId: Long, type: TransactionType): Flow<Long>

    /** RECEIVED/SPENT total, excluding transactions marked as a repayment — those settle a loan instead, and are counted through [observeOutstandingLoanSum] so they aren't double-counted. */
    @Query(
        """
        SELECT COALESCE(SUM(amountMinor), 0) FROM transactions
        WHERE accountId = :accountId AND type = :type AND isRepayment = 0
        """
    )
    fun observeIncomeExpenseSum(accountId: Long, type: TransactionType): Flow<Long>

    /** Outstanding LOAN_GIVEN/LOAN_TAKEN total for this account: each loan transaction's amount minus whatever repayment allocations have already covered, floored at 0, and 0 outright once manually marked settled. */
    @Query(
        """
        SELECT COALESCE(SUM(
            CASE WHEN t.settled = 1 THEN 0
                 ELSE MAX(0, t.amountMinor - COALESCE(alloc.allocated, 0))
            END
        ), 0)
        FROM transactions t
        LEFT JOIN (
            SELECT targetTransactionId, SUM(allocatedAmountMinor) AS allocated
            FROM repayment_allocations
            GROUP BY targetTransactionId
        ) alloc ON alloc.targetTransactionId = t.id
        WHERE t.accountId = :accountId AND t.type = :type
        """
    )
    fun observeOutstandingLoanSum(accountId: Long, type: TransactionType): Flow<Long>

    @Query(
        """
        SELECT t.* FROM transactions t
        INNER JOIN accounts a ON a.id = t.accountId
        WHERE t.description LIKE '%' || :query || '%'
           OR t.category LIKE '%' || :query || '%'
           OR a.name LIKE '%' || :query || '%'
        ORDER BY t.date DESC
        """
    )
    fun search(query: String): Flow<List<Transaction>>

    @Query("DELETE FROM transactions WHERE accountId = :accountId")
    suspend fun deleteAllForAccount(accountId: Long)
}
