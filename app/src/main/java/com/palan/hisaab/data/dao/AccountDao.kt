package com.palan.hisaab.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.palan.hisaab.data.entity.Account
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    @Insert
    suspend fun insert(account: Account): Long

    @Update
    suspend fun update(account: Account)

    @Delete
    suspend fun delete(account: Account)

    @Query("SELECT * FROM accounts ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<Account>>

    @Query("SELECT * FROM accounts ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAllOnce(): List<Account>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: Long): Account?

    @Query("SELECT * FROM accounts WHERE id = :id")
    fun observeById(id: Long): Flow<Account?>

    @Query("SELECT * FROM accounts WHERE name LIKE '%' || :query || '%' OR phoneNumber LIKE '%' || :query || '%' ORDER BY name COLLATE NOCASE ASC")
    fun searchByName(query: String): Flow<List<Account>>

    /** Case-insensitive exact-name lookup, used to detect a likely duplicate account before an import creates a new one. */
    @Query("SELECT * FROM accounts WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByExactName(name: String): Account?
}
