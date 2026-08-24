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

    @Query("SELECT * FROM transactions WHERE accountId = :accountId AND type = :type LIMIT 1")
    suspend fun getInitialBalance(accountId: Long, type: TransactionType = TransactionType.INITIAL_BALANCE): Transaction?

    @Query(
        """
        SELECT COALESCE(SUM(amountMinor), 0) FROM transactions
        WHERE accountId = :accountId AND type = :type
        """
    )
    fun observeSumByType(accountId: Long, type: TransactionType): Flow<Long>

    @Query(
        """
        SELECT
          (SELECT COALESCE(SUM(amountMinor), 0) FROM transactions WHERE accountId = :accountId AND type = 'INITIAL_BALANCE')
        + (SELECT COALESCE(SUM(amountMinor), 0) FROM transactions WHERE accountId = :accountId AND type = 'RECEIVED')
        - (SELECT COALESCE(SUM(amountMinor), 0) FROM transactions WHERE accountId = :accountId AND type = 'SPENT')
        """
    )
    fun observeBalance(accountId: Long): Flow<Long>

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
