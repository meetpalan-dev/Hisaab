package com.palan.hisaab.data

import androidx.room.withTransaction
import com.palan.hisaab.data.dao.OutstandingHisaab
import com.palan.hisaab.data.dao.TargetAllocatedSum
import com.palan.hisaab.data.entity.Account
import com.palan.hisaab.data.entity.RepaymentAllocation
import com.palan.hisaab.data.entity.Transaction
import com.palan.hisaab.data.entity.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

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

/** One allocation the user has chosen while building a repayment: "cover ₹X of this outstanding hisaab." */
data class RepaymentAllocationInput(val targetTransactionId: Long, val amountMinor: Long)

/** What a confirmed repayment settled, for the confirmation summary. */
data class RepaymentResult(
    val repaymentTransactionId: Long,
    val cleared: List<Transaction>,
    val partiallyPaid: List<Pair<Transaction, Long>> // transaction to remaining-after
)

class HisaabRepository(private val db: AppDatabase) {

    private val accountDao = db.accountDao()
    private val transactionDao = db.transactionDao()
    private val repaymentAllocationDao = db.repaymentAllocationDao()

    fun observeAccounts(): Flow<List<Account>> = accountDao.observeAll()

    fun searchAccounts(query: String): Flow<List<Account>> = accountDao.searchByName(query)

    fun searchTransactions(query: String): Flow<List<Transaction>> = transactionDao.search(query)

    /** Combines the type sums into one summary for a single account (used on Home). Received/Spent exclude repayment transactions; Loan Given/Taken are net of any repayment allocations, so a repayment's balance effect flows entirely through the loan side — never double-counted. */
    fun observeAccountSummary(account: Account): Flow<AccountSummary> =
        combine(
            transactionDao.observeSumByType(account.id, TransactionType.INITIAL_BALANCE),
            transactionDao.observeIncomeExpenseSum(account.id, TransactionType.RECEIVED),
            transactionDao.observeIncomeExpenseSum(account.id, TransactionType.SPENT),
            transactionDao.observeOutstandingLoanSum(account.id, TransactionType.LOAN_GIVEN),
            transactionDao.observeOutstandingLoanSum(account.id, TransactionType.LOAN_TAKEN)
        ) { initial, received, spent, loanGiven, loanTaken ->
            AccountSummary(account, initial, received, spent, loanGiven, loanTaken)
        }

    fun observeTransactions(accountId: Long): Flow<List<Transaction>> =
        transactionDao.observeForAccount(accountId)

    /** How much of each loan transaction in this account has been covered by repayment allocations so far, keyed by transaction id. Used to show "Repaid ₹X / Remaining ₹Y" on partially-paid rows. */
    fun observeAllocatedSums(accountId: Long): Flow<Map<Long, Long>> =
        repaymentAllocationDao.observeAllocatedSumsForAccount(accountId).map { rows: List<TargetAllocatedSum> ->
            rows.associate { it.targetTransactionId to it.allocatedMinor }
        }

    /** Live summary for one account looked up by id (for the Account/Hisab page). */
    fun observeAccountSummaryById(accountId: Long): Flow<AccountSummary> =
        accountDao.observeById(accountId).combine(
            combine(
                transactionDao.observeSumByType(accountId, TransactionType.INITIAL_BALANCE),
                transactionDao.observeIncomeExpenseSum(accountId, TransactionType.RECEIVED),
                transactionDao.observeIncomeExpenseSum(accountId, TransactionType.SPENT),
                transactionDao.observeOutstandingLoanSum(accountId, TransactionType.LOAN_GIVEN),
                transactionDao.observeOutstandingLoanSum(accountId, TransactionType.LOAN_TAKEN)
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

    /** Deletes a transaction. If it was a repayment, its allocations cascade-delete with it (DB foreign key), so we recompute settled/remaining on whatever loans it had covered — otherwise a fully-cleared loan could be left silently marked "Paid" for a repayment that no longer exists. If it was itself a loan transaction, its allocations cascade-delete too (harmless, the loan is gone). */
    suspend fun deleteTransaction(transaction: Transaction) {
        db.withTransaction {
            val affectedTargets = if (transaction.isRepayment) {
                repaymentAllocationDao.getForRepayment(transaction.id).map { it.targetTransactionId }
            } else emptyList()
            transactionDao.delete(transaction)
            affectedTargets.forEach { targetId ->
                val target = transactionDao.getById(targetId) ?: return@forEach
                val remaining = target.amountMinor - repaymentAllocationDao.sumForTarget(targetId)
                if (target.settled && remaining > 0) {
                    transactionDao.update(target.copy(settled = false, updatedAt = System.currentTimeMillis()))
                }
            }
        }
    }

    /** Marks a Loan Given/Loan Taken transaction paid or unpaid by hand (no repayment record). Never deletes it — it stays visible in history, just drops out of the outstanding-loan totals once settled. */
    suspend fun toggleLoanSettled(transaction: Transaction) {
        transactionDao.update(transaction.copy(settled = !transaction.settled, updatedAt = System.currentTimeMillis()))
    }

    /** Outstanding loan transactions eligible to receive a repayment in [accountId]: LOAN_TAKEN if you're paying them (repayment is a SPENT), LOAN_GIVEN if they're paying you back (repayment is a RECEIVED). */
    suspend fun getOutstandingHisaabs(accountId: Long, repaymentType: TransactionType): List<OutstandingHisaab> {
        val targetLoanType = if (repaymentType == TransactionType.SPENT) TransactionType.LOAN_TAKEN else TransactionType.LOAN_GIVEN
        return repaymentAllocationDao.getOutstanding(accountId, targetLoanType)
    }

    /**
     * Records a repayment: inserts the RECEIVED/SPENT transaction (flagged isRepayment so it's
     * excluded from normal income/expense totals) plus one RepaymentAllocation per hisaab the
     * user chose to cover, and marks each fully-covered loan transaction settled. All-or-nothing —
     * if anything fails, nothing is written, so a partial repayment can never leave the ledger
     * with an allocation but no transaction (or vice versa).
     */
    suspend fun applyRepayment(
        accountId: Long,
        type: TransactionType,
        amountMinor: Long,
        description: String,
        date: Long?,
        allocations: List<RepaymentAllocationInput>
    ): RepaymentResult = db.withTransaction {
        val repaymentId = transactionDao.insert(
            Transaction(
                accountId = accountId,
                type = type,
                amountMinor = amountMinor,
                description = description,
                date = date,
                isRepayment = true
            )
        )
        val cleared = mutableListOf<Transaction>()
        val partial = mutableListOf<Pair<Transaction, Long>>()
        for (alloc in allocations) {
            if (alloc.amountMinor <= 0L) continue
            repaymentAllocationDao.insert(
                RepaymentAllocation(
                    repaymentTransactionId = repaymentId,
                    targetTransactionId = alloc.targetTransactionId,
                    allocatedAmountMinor = alloc.amountMinor
                )
            )
            val target = transactionDao.getById(alloc.targetTransactionId) ?: continue
            val totalAllocated = repaymentAllocationDao.sumForTarget(alloc.targetTransactionId)
            val remaining = target.amountMinor - totalAllocated
            if (remaining <= 0L) {
                transactionDao.update(target.copy(settled = true, updatedAt = System.currentTimeMillis()))
                cleared.add(target)
            } else {
                partial.add(target to remaining)
            }
        }
        RepaymentResult(repaymentTransactionId = repaymentId, cleared = cleared, partiallyPaid = partial)
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
     * Applies a split expense: your own share (isSelf) posts as a plain Spent
     * transaction into a persistent "Me" account — reused across splits, created
     * once if it doesn't exist yet. Everyone else's share posts as Loan Given
     * (you paid the total, so each of them owes you their share back).
     * A new account for anyone typed as a new name is created here, at the moment
     * of confirmation — not while they're just sitting in the participant list —
     * and the whole split is one DB transaction, so a failure partway through
     * can't leave a new account with no transaction, or vice versa.
     */
    suspend fun applySplit(description: String, shares: List<SplitShare>, date: Long): List<Long> = db.withTransaction {
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
        resultIds
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
                tObj.put("isRepayment", t.isRepayment)
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

    /** Restores accounts + transactions from a backup produced by [exportAllToJson]. Always creates new accounts (never merges into existing ones), so re-importing is safe. Older backups (pre-repayment feature) simply have no "isRepayment" field, which defaults to false. Repayment allocation links themselves aren't part of the backup, so a restored repayment transaction comes back as a plain flagged transaction with its settlements already baked into each loan's "settled" state at export time — nothing is lost balance-wise. Returns the number of accounts restored. */
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
                        settled = tObj.optBoolean("settled", false),
                        isRepayment = tObj.optBoolean("isRepayment", false)
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
