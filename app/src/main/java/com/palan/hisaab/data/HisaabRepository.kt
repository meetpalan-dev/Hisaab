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

    suspend fun createAccount(name: String, startingBalanceMinor: Long, phoneNumber: String? = null): Long {
        val id = accountDao.insert(Account(name = name.trim(), phoneNumber = phoneNumber?.trim()?.ifBlank { null }))
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

    /** Marks a Loan Given/Loan Taken transaction paid or unpaid. Never deletes it — it stays visible in history, just drops out of the outstanding-loan totals once settled. */
    suspend fun toggleLoanSettled(transaction: Transaction) {
        transactionDao.update(transaction.copy(settled = !transaction.settled, updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteAccount(account: Account) {
        accountDao.delete(account) // CASCADE removes its transactions
    }

    suspend fun renameAccount(account: Account, newName: String) {
        accountDao.update(account.copy(name = newName.trim()))
    }

    suspend fun updateAccountDetails(account: Account, newName: String, phoneNumber: String?) {
        accountDao.update(account.copy(name = newName.trim(), phoneNumber = phoneNumber?.trim()?.ifBlank { null }))
    }

    suspend fun getAllAccountsOnce(): List<Account> = accountDao.getAllOnce()

    /**
     * Applies a split expense: for each participant (excluding "you"), logs their
     * share as a Loan Given transaction in their account — you paid the total,
     * so each of them owes you their share back. Participants matched to an
     * existing account (by id) post directly to it; anyone typed as a new name
     * gets a fresh account created for them first.
     */
    /**
     * Applies a split expense: your own share (isSelf) posts as a plain Spent
     * transaction into a persistent "Me" account — reused across splits, created
     * once if it doesn't exist yet. Everyone else's share posts as Loan Given
     * (you paid the total, so each of them owes you their share back).
     */
    suspend fun applySplit(description: String, shares: List<SplitShare>, date: Long): List<Long> {
        val resultIds = mutableListOf<Long>()
        var meAccountId: Long? = null
        for (share in shares) {
            if (share.amountMinor <= 0L) continue
            val accountId = when {
                share.isSelf -> meAccountId ?: getOrCreateMeAccount().also { meAccountId = it }
                else -> share.existingAccountId ?: accountDao.insert(Account(name = share.name))
            }
            transactionDao.insert(
                Transaction(
                    accountId = accountId,
                    type = if (share.isSelf) TransactionType.SPENT else TransactionType.LOAN_GIVEN,
                    amountMinor = share.amountMinor,
                    description = description.ifBlank { "Split" },
                    date = date
                )
            )
            resultIds.add(accountId)
        }
        return resultIds
    }

    private suspend fun getOrCreateMeAccount(): Long {
        val existing = accountDao.getAllOnce().firstOrNull { it.name.equals("Me", ignoreCase = true) }
        return existing?.id ?: accountDao.insert(Account(name = "Me"))
    }

    /** Full backup of every account and transaction as JSON (used by Export/Import Backup). */
    suspend fun exportAllToJson(): String {
        val accounts = accountDao.getAllOnce()
        val accountsArr = org.json.JSONArray()
        for (acc in accounts) {
            val accObj = org.json.JSONObject()
            accObj.put("name", acc.name)
            accObj.put("phoneNumber", acc.phoneNumber ?: org.json.JSONObject.NULL)
            val txnArr = org.json.JSONArray()
            for (t in transactionDao.getForAccountOnce(acc.id)) {
                val tObj = org.json.JSONObject()
                tObj.put("type", t.type.name)
                tObj.put("amountMinor", t.amountMinor)
                tObj.put("description", t.description)
                tObj.put("date", t.date ?: org.json.JSONObject.NULL)
                tObj.put("category", t.category ?: org.json.JSONObject.NULL)
                tObj.put("settled", t.settled)
                txnArr.put(tObj)
            }
            accObj.put("transactions", txnArr)
            accountsArr.put(accObj)
        }
        val root = org.json.JSONObject()
        root.put("accounts", accountsArr)
        root.put("exportedAt", System.currentTimeMillis())
        return root.toString(2)
    }

    /** Restores accounts + transactions from a backup produced by [exportAllToJson]. Always creates new accounts (never merges into existing ones), so re-importing is safe. Returns the number of accounts restored. */
    suspend fun importFromJson(json: String): Int {
        val root = org.json.JSONObject(json)
        val accountsArr = root.optJSONArray("accounts") ?: return 0
        var count = 0
        for (i in 0 until accountsArr.length()) {
            val accObj = accountsArr.getJSONObject(i)
            val name = accObj.optString("name")
            if (name.isBlank()) continue
            val phone = accObj.optString("phoneNumber").takeIf { it.isNotBlank() && it != "null" }
            val accountId = accountDao.insert(Account(name = name, phoneNumber = phone))
            val txnArr = accObj.optJSONArray("transactions") ?: org.json.JSONArray()
            for (j in 0 until txnArr.length()) {
                val tObj = txnArr.getJSONObject(j)
                val type = runCatching { TransactionType.valueOf(tObj.getString("type")) }.getOrNull() ?: continue
                transactionDao.insert(
                    Transaction(
                        accountId = accountId,
                        type = type,
                        amountMinor = tObj.optLong("amountMinor", 0L),
                        description = tObj.optString("description", ""),
                        date = if (tObj.isNull("date")) null else tObj.optLong("date"),
                        category = if (tObj.isNull("category")) null else tObj.optString("category"),
                        settled = tObj.optBoolean("settled", false)
                    )
                )
            }
            count++
        }
        return count
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
                    date = txn.dateMillis,
                    settled = txn.isSettled
                )
            )
        }
        return accountId
    }
}

data class SplitShare(
    val name: String,
    val amountMinor: Long,
    val existingAccountId: Long? = null,
    val isSelf: Boolean = false
)
