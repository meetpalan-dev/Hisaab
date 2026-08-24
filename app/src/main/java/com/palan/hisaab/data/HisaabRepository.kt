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
    val spent: Long,
    val loanGiven: Long = 0,
    val loanTaken: Long = 0
) {
    val balance: Long get() = initialBalance + received - spent + loanGiven - loanTaken
    val hasLoans: Boolean get() = loanGiven != 0L || loanTaken != 0L
}

class HisaabRepository(
    private val accountDao: AccountDao,
    private val transactionDao: TransactionDao
) {
    fun observeAccounts(): Flow<List<Account>> = accountDao.observeAll()

    fun searchAccounts(query: String): Flow<List<Account>> = accountDao.searchByName(query)

    fun searchTransactions(query: String): Flow<List<Transaction>> = transactionDao.search(query)

    /** Combines the five type sums into one summary for a single account (used on Home). */
    fun observeAccountSummary(account: Account): Flow<AccountSummary> =
        combine(
            transactionDao.observeSumByType(account.id, TransactionType.INITIAL_BALANCE),
            transactionDao.observeSumByType(account.id, TransactionType.RECEIVED),
            transactionDao.observeSumByType(account.id, TransactionType.SPENT),
            transactionDao.observeSumByType(account.id, TransactionType.LOAN_GIVEN),
            transactionDao.observeSumByType(account.id, TransactionType.LOAN_TAKEN)
        ) { initial, received, spent, loanGiven, loanTaken ->
            AccountSummary(account, initial, received, spent, loanGiven, loanTaken)
        }

    fun observeTransactions(accountId: Long): Flow<List<Transaction>> =
        transactionDao.observeForAccount(accountId)

    /** Live summary for one account looked up by id (for the Account/Hisab page). */
    fun observeAccountSummaryById(accountId: Long): Flow<AccountSummary> =
        accountDao.observeById(accountId).combine(
            combine(
                transactionDao.observeSumByType(accountId, TransactionType.INITIAL_BALANCE),
                transactionDao.observeSumByType(accountId, TransactionType.RECEIVED),
                transactionDao.observeSumByType(accountId, TransactionType.SPENT),
                transactionDao.observeSumByType(accountId, TransactionType.LOAN_GIVEN),
                transactionDao.observeSumByType(accountId, TransactionType.LOAN_TAKEN)
            ) { initial, received, spent, loanGiven, loanTaken ->
                LoanSums(initial, received, spent, loanGiven, loanTaken)
            }
        ) { account, sums ->
            AccountSummary(
                account = account ?: Account(id = accountId, name = ""),
                initialBalance = sums.initial,
                received = sums.received,
                spent = sums.spent,
                loanGiven = sums.loanGiven,
                loanTaken = sums.loanTaken
            )
        }

    private data class LoanSums(
        val initial: Long,
        val received: Long,
        val spent: Long,
        val loanGiven: Long,
        val loanTaken: Long
    )

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

    /** Recreates an account + its transactions from a parsed "Share as Text" export. Always creates a new account (never merges into an existing one), so re-importing is safe. */
    suspend fun importParsedHisab(parsed: com.palan.hisaab.util.ParsedHisab): Long {
        val accountId = accountDao.insert(Account(name = parsed.accountName))
        if (parsed.initialBalanceMinor != 0L) {
            transactionDao.insert(
                Transaction(
                    accountId = accountId,
                    type = TransactionType.INITIAL_BALANCE,
                    amountMinor = parsed.initialBalanceMinor,
                    description = "Initial Balance",
                    date = System.currentTimeMillis()
                )
            )
        }
        parsed.transactions.forEach { txn ->
            val type = when {
                txn.isLoan && txn.isSpent -> TransactionType.LOAN_TAKEN   // "-" sign -> you owe them
                txn.isLoan && !txn.isSpent -> TransactionType.LOAN_GIVEN  // "+" sign -> they owe you
                txn.isSpent -> TransactionType.SPENT
                else -> TransactionType.RECEIVED
            }
            transactionDao.insert(
                Transaction(
                    accountId = accountId,
                    type = type,
                    amountMinor = txn.amountMinor,
                    description = txn.description,
                    date = txn.dateMillis
                )
            )
        }
        return accountId
    }

    /** Builds one text file containing every account, for Settings > Export all. */
    suspend fun exportAllAccountsText(): String {
        val accounts = accountDao.getAllOnce()
        val data = accounts.map { account ->
            val txns = transactionDao.getForAccountOnce(account.id)
            val initialBalance = txns.firstOrNull { it.type == TransactionType.INITIAL_BALANCE }?.amountMinor ?: 0L
            val rest = txns.filter { it.type != TransactionType.INITIAL_BALANCE }
            Triple(account.name, initialBalance, rest)
        }
        return com.palan.hisaab.util.HisabTextExporter.buildBackupText(data)
    }

    /** Imports every account found in a Settings > Export all backup file. Each becomes a brand-new account, same as a single-account import. Returns how many were created. */
    suspend fun importBackup(accounts: List<com.palan.hisaab.util.ParsedHisab>): Int {
        accounts.forEach { importParsedHisab(it) }
        return accounts.size
    }
}
