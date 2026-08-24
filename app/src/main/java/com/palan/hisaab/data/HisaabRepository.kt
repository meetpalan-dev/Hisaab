package com.palan.hisaab.data

import com.palan.hisaab.data.dao.AccountDao
import com.palan.hisaab.data.dao.TransactionDao
import com.palan.hisaab.data.entity.Account
import com.palan.hisaab.data.entity.Transaction
import com.palan.hisaab.data.entity.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class AccountSummary(
    val account: Account,
    val initialBalance: Long,
    val received: Long,
    val spent: Long
) {
    val balance: Long get() = initialBalance + received - spent
}

class HisaabRepository(
    private val accountDao: AccountDao,
    private val transactionDao: TransactionDao
) {
    fun observeAccounts(): Flow<List<Account>> = accountDao.observeAll()

    fun searchAccounts(query: String): Flow<List<Account>> = accountDao.searchByName(query)

    fun searchTransactions(query: String): Flow<List<Transaction>> = transactionDao.search(query)

    /** Combines the three type sums into one summary for a single account (used on Home). */
    fun observeAccountSummary(account: Account): Flow<AccountSummary> =
        combine(
            transactionDao.observeSumByType(account.id, TransactionType.INITIAL_BALANCE),
            transactionDao.observeSumByType(account.id, TransactionType.RECEIVED),
            transactionDao.observeSumByType(account.id, TransactionType.SPENT)
        ) { initial, received, spent ->
            AccountSummary(account, initial, received, spent)
        }

    fun observeTransactions(accountId: Long): Flow<List<Transaction>> =
        transactionDao.observeForAccount(accountId)

    /** Live summary for one account looked up by id (for the Account/Hisab page). */
    fun observeAccountSummaryById(accountId: Long): Flow<AccountSummary> =
        accountDao.observeById(accountId).combine(
            combine(
                transactionDao.observeSumByType(accountId, TransactionType.INITIAL_BALANCE),
                transactionDao.observeSumByType(accountId, TransactionType.RECEIVED),
                transactionDao.observeSumByType(accountId, TransactionType.SPENT)
            ) { initial, received, spent -> Triple(initial, received, spent) }
        ) { account, sums ->
            AccountSummary(
                account = account ?: Account(id = accountId, name = ""),
                initialBalance = sums.first,
                received = sums.second,
                spent = sums.third
            )
        }

    suspend fun createAccount(name: String, startingBalanceMinor: Long): Long {
        val id = accountDao.insert(Account(name = name.trim()))
        if (startingBalanceMinor != 0L) {
            transactionDao.insert(
                Transaction(
                    accountId = id,
                    type = TransactionType.INITIAL_BALANCE,
                    amountMinor = startingBalanceMinor,
                    description = "Initial Balance",
                    date = System.currentTimeMillis()
                )
            )
        }
        return id
    }

    suspend fun getInitialBalanceTransaction(accountId: Long): Transaction? =
        transactionDao.getInitialBalance(accountId)

    /** Sets or updates the single Initial Balance transaction for an account. */
    suspend fun setInitialBalance(accountId: Long, amountMinor: Long, date: Long) {
        val existing = transactionDao.getInitialBalance(accountId)
        if (existing != null) {
            transactionDao.update(
                existing.copy(amountMinor = amountMinor, date = date, updatedAt = System.currentTimeMillis())
            )
        } else {
            transactionDao.insert(
                Transaction(
                    accountId = accountId,
                    type = TransactionType.INITIAL_BALANCE,
                    amountMinor = amountMinor,
                    description = "Initial Balance",
                    date = date
                )
            )
        }
    }

    suspend fun addTransaction(transaction: Transaction) {
        transactionDao.insert(transaction)
    }

    suspend fun updateTransaction(transaction: Transaction) {
        transactionDao.update(transaction.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteTransaction(transaction: Transaction) {
        transactionDao.delete(transaction)
    }

    suspend fun deleteAccount(account: Account) {
        accountDao.delete(account) // CASCADE removes its transactions
    }

    suspend fun renameAccount(account: Account, newName: String) {
        accountDao.update(account.copy(name = newName.trim()))
    }
}
