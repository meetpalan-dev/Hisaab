package com.palan.hisaab.data

import androidx.room.withTransaction
import com.palan.hisaab.data.dao.OutstandingHisaab
import com.palan.hisaab.data.dao.TargetAllocatedSum
import com.palan.hisaab.data.entity.Account
import com.palan.hisaab.data.entity.RepaymentAllocation
import com.palan.hisaab.data.entity.SplitParticipantRecord
import com.palan.hisaab.data.entity.SplitRecord
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

/** One allocation applied while settling a hisaab: "cover ₹X of this outstanding transaction." Computed automatically by [HisaabRepository.settleHisaab] — no longer something the user picks by hand. */
data class RepaymentAllocationInput(val targetTransactionId: Long, val amountMinor: Long)

/** What a confirmed "Settle Hisaab" settlement applied, for the confirmation summary. */
data class RepaymentResult(
    val repaymentTransactionId: Long,
    val repaymentAmountMinor: Long,
    val totalAllocatedMinor: Long,
    val cleared: List<Transaction>,
    val partiallyPaid: List<Pair<Transaction, Long>> // transaction to remaining-after
) {
    /** Any amount left over once every eligible outstanding hisaab was already fully covered — never force-assigned, just part of the recorded transaction's own amount. */
    val unallocatedMinor: Long get() = (repaymentAmountMinor - totalAllocatedMinor).coerceAtLeast(0L)
}

class HisaabRepository(private val db: AppDatabase) {

    private val accountDao = db.accountDao()
    private val transactionDao = db.transactionDao()
    private val repaymentAllocationDao = db.repaymentAllocationDao()
    private val splitDao = db.splitDao()

    fun observeAccounts(): Flow<List<Account>> = accountDao.observeAll()

    fun searchAccounts(query: String): Flow<List<Account>> = accountDao.searchByName(query)

    fun searchTransactions(query: String): Flow<List<Transaction>> = transactionDao.search(query)

    /** Combines the type sums into one summary for a single account (used on Home). Received/Spent exclude repayment transactions; Loan Given (SPENT+isLoan) and Loan Taken (RECEIVED+isLoan) are net of any repayment allocations, so a repayment's balance effect flows entirely through the loan side — never double-counted. Spent is further reduced by [observeSplitRecoveredTotal] for any Split-linked "Me" total this account holds, since that recovery happens as a real transaction on a *different* account and would otherwise never show up here. */
    fun observeAccountSummary(account: Account): Flow<AccountSummary> =
        combine(
            transactionDao.observeSumByType(account.id, TransactionType.INITIAL_BALANCE),
            transactionDao.observeIncomeExpenseSum(account.id, TransactionType.RECEIVED),
            transactionDao.observeIncomeExpenseSum(account.id, TransactionType.SPENT),
            transactionDao.observeOutstandingLoanSum(account.id, TransactionType.SPENT),
            transactionDao.observeOutstandingLoanSum(account.id, TransactionType.RECEIVED)
        ) { initial, received, spent, loanGiven, loanTaken ->
            LoanSums(initial, received, spent, loanGiven, loanTaken)
        }.combine(observeSplitRecoveredTotal(account.id)) { sums, splitRecovered ->
            AccountSummary(account, sums.initial, sums.received, sums.spent - splitRecovered, sums.loanGiven, sums.loanTaken)
        }

    fun observeTransactions(accountId: Long): Flow<List<Transaction>> =
        transactionDao.observeForAccount(accountId)

    /** How much of each loan transaction in this account has been covered by repayment allocations so far, keyed by transaction id. Used to show "Repaid ₹X / Remaining ₹Y" on partially-paid rows. */
    fun observeAllocatedSums(accountId: Long): Flow<Map<Long, Long>> =
        repaymentAllocationDao.observeAllocatedSumsForAccount(accountId).map { rows: List<TargetAllocatedSum> ->
            rows.associate { it.targetTransactionId to it.allocatedMinor }
        }

    /** Live summary for one account looked up by id (for the Account/Hisab page). Applies the same Split-recovery adjustment to Spent as [observeAccountSummary], so the two screens can never disagree. */
    fun observeAccountSummaryById(accountId: Long): Flow<AccountSummary> =
        accountDao.observeById(accountId).combine(
            combine(
                transactionDao.observeSumByType(accountId, TransactionType.INITIAL_BALANCE),
                transactionDao.observeIncomeExpenseSum(accountId, TransactionType.RECEIVED),
                transactionDao.observeIncomeExpenseSum(accountId, TransactionType.SPENT),
                transactionDao.observeOutstandingLoanSum(accountId, TransactionType.SPENT),
                transactionDao.observeOutstandingLoanSum(accountId, TransactionType.RECEIVED)
            ) { initial, received, spent, loanGiven, loanTaken ->
                LoanSums(initial, received, spent, loanGiven, loanTaken)
            }.combine(observeSplitRecoveredTotal(accountId)) { sums, splitRecovered ->
                sums.copy(spent = sums.spent - splitRecovered)
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

    /**
     * Marks any transaction Settled/Cleared or restores it to Active by hand — this is
     * the "quick manual settlement" swipe action, and also the button behind it on the
     * Edit Transaction screen. Never deletes it — it stays visible in history and in
     * the All tab either way, it just moves between the Active and Cleared tabs, and
     * (for a loan) drops out of the outstanding-loan totals once settled.
     */
    suspend fun toggleSettled(transaction: Transaction) = setSettled(transaction, !transaction.settled)

    /**
     * Sets a transaction's Active/Cleared status explicitly — used by the swipe gesture and its
     * Undo, the Edit Transaction "Mark as Settled" button, and Split Details. A no-op if it's
     * already in the requested state.
     *
     * Marking a transaction Settled is no longer just a status flag — it generates the real
     * opposite-direction transaction for whatever's still outstanding, tagged "Repayment of X"
     * and linked via a full allocation, exactly as if Settle Hisaab had been used for that exact
     * amount. That's what actually moves the account's balance; a boolean alone never should
     * (marking something Cleared without that money genuinely having moved would be lying about
     * the balance). Restoring undoes it: the synthesized repayment (and its allocation) is
     * removed so the amount becomes owed again — unless that repayment also helped cover some
     * other transaction (e.g. it was later swept up by a broader Settle Hisaab), in which case
     * only this allocation is removed and the repayment itself is left alone.
     */
    suspend fun setSettled(transaction: Transaction, settled: Boolean): Unit = db.withTransaction {
        if (transaction.settled == settled) return@withTransaction
        if (settled) {
            val remaining = remainingOf(transaction)
            if (remaining > 0L && !transaction.isRepayment && transaction.type != TransactionType.INITIAL_BALANCE) {
                val repaymentType = if (transaction.type == TransactionType.RECEIVED) TransactionType.SPENT else TransactionType.RECEIVED
                val repaymentId = transactionDao.insert(
                    Transaction(
                        accountId = transaction.accountId,
                        type = repaymentType,
                        amountMinor = remaining,
                        description = "Repayment of ${transaction.description}",
                        date = System.currentTimeMillis(),
                        isRepayment = true
                    )
                )
                repaymentAllocationDao.insert(
                    RepaymentAllocation(repaymentTransactionId = repaymentId, targetTransactionId = transaction.id, allocatedAmountMinor = remaining)
                )
            }
            transactionDao.update(transaction.copy(settled = true, updatedAt = System.currentTimeMillis()))
        } else {
            for (alloc in repaymentAllocationDao.getForTarget(transaction.id)) {
                repaymentAllocationDao.delete(alloc)
                val repayment = transactionDao.getById(alloc.repaymentTransactionId) ?: continue
                val stillUsed = repaymentAllocationDao.getForRepayment(repayment.id).isNotEmpty()
                if (!stillUsed) transactionDao.delete(repayment)
            }
            transactionDao.update(transaction.copy(settled = false, updatedAt = System.currentTimeMillis()))
        }
    }

    /**
     * Which outstanding transaction type a repayment of [repaymentType] is eligible to cover,
     * based on direction — a SPENT repayment (you're paying money out) settles outstanding
     * RECEIVED transactions (money that came in and is still owed back, whether or not it's
     * flagged as a loan); a RECEIVED repayment (money is coming back to you) settles outstanding
     * SPENT transactions (money that went out and is still owed to you). The isLoan flag only
     * changes how a transaction counts toward the account's balance — it never changes who's
     * eligible to settle it.
     */
    private fun eligibleTargetTypes(repaymentType: TransactionType): List<TransactionType> =
        if (repaymentType == TransactionType.SPENT) listOf(TransactionType.RECEIVED) else listOf(TransactionType.SPENT)

    suspend fun getOutstandingHisaabs(accountId: Long, repaymentType: TransactionType): List<OutstandingHisaab> =
        repaymentAllocationDao.getOutstanding(accountId, eligibleTargetTypes(repaymentType))

    /**
     * "Settle Hisaab": records a real Received/Spent transaction for the actual amount paid or
     * received, then automatically applies it against this account's outstanding hisaab — the
     * user never has to pick which historical transaction it belongs to. Outstanding transactions
     * are covered oldest-first: each one is covered in full (and marked Cleared) until the amount
     * runs out, the one it runs out on becomes Partially Settled with its own remaining balance
     * tracked, and anything after that stays untouched. If the amount paid is more than the total
     * outstanding, the extra is simply left as part of the recorded transaction — never force-fit
     * against an already-settled hisaab. The whole thing (transaction + every allocation + every
     * status flip) is one all-or-nothing operation.
     */
    suspend fun settleHisaab(
        accountId: Long,
        type: TransactionType,
        amountMinor: Long,
        description: String,
        date: Long?
    ): RepaymentResult = db.withTransaction {
        val outstanding = repaymentAllocationDao.getOutstanding(accountId, eligibleTargetTypes(type))
        var pool = amountMinor
        val allocations = mutableListOf<RepaymentAllocationInput>()
        for (hisaab in outstanding) {
            if (pool <= 0L) break
            val take = minOf(pool, hisaab.remainingMinor)
            if (take > 0L) {
                allocations.add(RepaymentAllocationInput(hisaab.transaction.id, take))
                pool -= take
            }
        }
        applyRepayment(accountId, type, amountMinor, description, date, allocations)
    }

    /**
     * Records a repayment: inserts the RECEIVED/SPENT transaction (flagged isRepayment) plus one
     * RepaymentAllocation per hisaab [allocations] covers, and marks each fully-covered target
     * settled — used internally by [settleHisaab]'s automatic allocation. All-or-nothing — if
     * anything fails, nothing is written, so a partial settlement can never leave the ledger with
     * an allocation but no transaction (or vice versa).
     */
    private suspend fun applyRepayment(
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
        var totalAllocated = 0L
        for (alloc in allocations) {
            if (alloc.amountMinor <= 0L) continue
            repaymentAllocationDao.insert(
                RepaymentAllocation(
                    repaymentTransactionId = repaymentId,
                    targetTransactionId = alloc.targetTransactionId,
                    allocatedAmountMinor = alloc.amountMinor
                )
            )
            totalAllocated += alloc.amountMinor
            val target = transactionDao.getById(alloc.targetTransactionId) ?: continue
            val allocatedForTarget = repaymentAllocationDao.sumForTarget(alloc.targetTransactionId)
            val remaining = target.amountMinor - allocatedForTarget
            if (remaining <= 0L) {
                transactionDao.update(target.copy(settled = true, updatedAt = System.currentTimeMillis()))
                cleared.add(target)
            } else {
                partial.add(target to remaining)
            }
        }
        RepaymentResult(
            repaymentTransactionId = repaymentId,
            repaymentAmountMinor = amountMinor,
            totalAllocatedMinor = totalAllocated,
            cleared = cleared,
            partiallyPaid = partial
        )
    }

    suspend fun deleteAccount(account: Account) {
        accountDao.delete(account) // CASCADE removes its transactions
    }

    /** Deletes several accounts at once — the protected "Me" account is skipped even if it's somehow included, as a second line of defense beyond the UI already excluding it from selection. All-or-nothing: either every eligible account (and its transactions, via cascade) is removed, or none are. */
    suspend fun deleteAccounts(accountIds: List<Long>) = db.withTransaction {
        for (id in accountIds) {
            val account = accountDao.getById(id) ?: continue
            if (account.name.equals("Me", ignoreCase = true)) continue
            accountDao.delete(account)
        }
    }

    suspend fun renameAccount(account: Account, newName: String) {
        accountDao.update(account.copy(name = newName.trim()))
    }

    suspend fun updateAccountDetails(account: Account, newName: String, phoneNumber: String?) {
        accountDao.update(account.copy(name = newName.trim(), phoneNumber = phoneNumber?.trim()?.ifBlank { null }))
    }

    suspend fun getAllAccountsOnce(): List<Account> = accountDao.getAllOnce()

    /**
     * Applies a split expense, based on who actually paid the bill ([payerName], matched against
     * [shares] by name):
     *  - If I'm the payer, ONE Spent transaction is posted on the persistent "Me" account for the
     *    FULL amount I fronted — not just my own share — so my own tab tracks "how much am I still
     *    owed back from this," the way a running tab would. Its live remaining amount is
     *    recomputed by [computeSplitTotalOverrides] from how much everyone else has actually
     *    settled on their own account, so it shrinks automatically toward just my own share as
     *    they pay me back — nothing is ever edited on this transaction directly to make that
     *    happen; my own share simply never counts as "recovered."
     *  - Everyone else's share becomes a debt *to the payer*, recorded as a plain Spent/Received
     *    entry (never as Loan Given/Taken — Split deliberately avoids that framing, see
     *    computeSplitStatus). If the payer is Me, each other participant's share posts as Spent on
     *    their account: I spent that amount on their behalf, so it shows as -₹X there, exactly the
     *    transaction that occurred. If I'm one of those people instead (someone else paid), my
     *    share posts as Received on the payer's account: I received that value from them and still
     *    owe it back, later settled by a Spent repayment.
     *  - A participant who is neither the payer nor "Me" owes the payer, not me — this app's
     *    accounts each track a Me-vs-that-person ledger, so there's no honest place to post a
     *    debt between two other people. That share is left unrecorded (flagged in the result and
     *    in Split History) rather than silently invented on some account's balance.
     *
     * The full breakdown — every participant, their share, and whether it was actually recorded
     * to a ledger — is always saved to Split History regardless, so nothing about the split is
     * ever lost even when only part of it could be posted as real transactions.
     */
    suspend fun applySplit(description: String, shares: List<SplitShare>, payerName: String, date: Long): SplitApplyResult = db.withTransaction {
        val postedAccountIds = mutableListOf<Long>()
        val unrecordedParticipants = mutableListOf<String>()
        val participantRecords = mutableListOf<SplitParticipantRecord>()
        val totalMinor = shares.sumOf { it.amountMinor }
        val label = description.ifBlank { "Split" }
        val payerIsSelf = shares.any { it.isSelf && it.name.equals(payerName, ignoreCase = true) }

        // Resolve (and create if needed) the payer's own account up front, since a non-self payer's
        // account may not exist yet and multiple other shares might need to post debts to it.
        var payerAccountId: Long? = null
        if (!payerIsSelf) {
            val payerShare = shares.firstOrNull { it.name.equals(payerName, ignoreCase = true) }
            payerAccountId = payerShare?.existingAccountId ?: accountDao.insert(Account(name = payerName))
        }

        // If I'm the payer, record the FULL amount up front as one combined entry.
        var meTransactionId: Long? = null
        if (payerIsSelf && totalMinor > 0L) {
            val meId = getOrCreateMeAccount()
            meTransactionId = transactionDao.insert(
                Transaction(accountId = meId, type = TransactionType.SPENT, amountMinor = totalMinor, description = label, date = date)
            )
            postedAccountIds.add(meId)
        }

        for (share in shares) {
            val isPayer = share.name.equals(payerName, ignoreCase = true)
            var recorded = false
            var linkedTransactionId: Long? = null

            if (share.amountMinor > 0L) {
                when {
                    isPayer && share.isSelf -> {
                        // Already covered by the combined Me total above.
                        recorded = true
                        linkedTransactionId = meTransactionId
                    }
                    isPayer -> {
                        // Someone else paid the whole bill; nothing to record for their own share.
                    }
                    share.isSelf -> {
                        // I owe the payer my share -- they covered it for me, so from a plain
                        // transaction-direction view I "received" that value and still need to
                        // pay it back (settled later by a Spent repayment). payerAccountId is
                        // always set here since this branch only runs when the payer isn't me.
                        val payTo = payerAccountId!!
                        linkedTransactionId = transactionDao.insert(
                            Transaction(accountId = payTo, type = TransactionType.RECEIVED, amountMinor = share.amountMinor, description = label, date = date)
                        )
                        postedAccountIds.add(payTo)
                        recorded = true
                    }
                    payerIsSelf -> {
                        // I paid, they owe me -- I spent this amount on their behalf, so it's a
                        // plain Spent entry on their account (settled later by a Received repayment).
                        val accountId = share.existingAccountId ?: accountDao.insert(Account(name = share.name))
                        linkedTransactionId = transactionDao.insert(
                            Transaction(accountId = accountId, type = TransactionType.SPENT, amountMinor = share.amountMinor, description = label, date = date)
                        )
                        postedAccountIds.add(accountId)
                        recorded = true
                    }
                    else -> {
                        // Neither the payer nor me — a debt between two other people this app can't track.
                        unrecordedParticipants.add(share.name)
                    }
                }
            }

            participantRecords.add(
                SplitParticipantRecord(splitId = 0, name = share.name, amountMinor = share.amountMinor, isPayer = isPayer, recorded = recorded, transactionId = linkedTransactionId)
            )
        }

        val splitId = splitDao.insertRecord(
            SplitRecord(description = label, totalMinor = totalMinor, payerName = payerName, date = date, meTransactionId = meTransactionId)
        )
        splitDao.insertParticipants(participantRecords.map { it.copy(splitId = splitId) })

        SplitApplyResult(postedToAccountIds = postedAccountIds, unrecordedParticipants = unrecordedParticipants)
    }

    fun observeSplitHistory(): Flow<List<com.palan.hisaab.data.dao.SplitRecordWithParticipants>> = splitDao.observeAll()

    /** Live version of [observeSplitHistory] that also computes each participant's recovered/remaining amount and the split's overall status — used by Split History. */
    fun observeSplitHistoryWithStatus(): Flow<List<SplitStatusSummary>> =
        splitDao.observeAll().map { list -> list.map { computeSplitStatus(it) } }

    /** How much of a transaction is still outstanding right now: 0 once settled, otherwise its amount minus whatever's been allocated against it so far. Shared by the split-total override and split-status calculations so they can never disagree with each other. */
    private suspend fun remainingOf(transaction: Transaction): Long =
        if (transaction.settled) 0L
        else (transaction.amountMinor - repaymentAllocationDao.sumForTarget(transaction.id)).coerceAtLeast(0L)

    /**
     * If [txn] is a Split's combined "Me" total (see [applySplit]), computes its live remaining
     * amount: my own share stays baked in forever, plus whatever every other participant still
     * hasn't cleared on their own account. Returns null if [txn] isn't one. This is the single
     * shared calculation behind [computeSplitTotalOverrides] (per-row display),
     * [observeSplitRecoveredTotal] (account-level balance adjustment), and [computeSplitStatus]
     * (Split History) — so all three can never drift out of sync with each other.
     */
    private suspend fun splitLinkedRemaining(txn: Transaction): Long? {
        if (txn.type != TransactionType.SPENT) return null
        val record = splitDao.findByMeTransactionId(txn.id) ?: return null
        if (txn.settled) return 0L
        var remaining = 0L
        for (p in record.participants) {
            if (p.isPayer) {
                remaining += p.amountMinor // my own share is never "recovered"
                continue
            }
            val linkedId = p.transactionId ?: continue
            val linked = transactionDao.getById(linkedId) ?: continue
            remaining += remainingOf(linked)
        }
        return remaining.coerceAtMost(txn.amountMinor)
    }

    /**
     * For every transaction in [accountId] that's a split's combined "Me" total (see
     * [applySplit]), its live remaining amount. This is a read-time view, not a stored balance —
     * each participant's own settlement is still the only place that's actually written to, so it
     * can never drift out of sync with what really happened on their account. Built directly off
     * [TransactionDao.observeForAccount] (a genuine Room query Flow, not a one-shot snapshot) so it
     * re-emits the moment any transaction anywhere changes — including a settlement recorded on a
     * completely different account — the same way [observeSplitRecoveredTotal] already does for
     * the account-level balance. Without this, the balance figure would update live while this
     * per-row figure sat stale until the screen was reopened.
     */
    fun observeSplitTotalOverrides(accountId: Long): Flow<Map<Long, Long>> =
        transactionDao.observeForAccount(accountId).map { txns ->
            txns.mapNotNull { txn -> splitLinkedRemaining(txn)?.let { remaining -> txn.id to remaining } }.toMap()
        }

    /** One-shot suspend snapshot of [observeSplitTotalOverrides] — used where a Flow isn't convenient (e.g. building a confirmation dialog). */
    suspend fun computeSplitTotalOverrides(accountId: Long): Map<Long, Long> {
        val overrides = mutableMapOf<Long, Long>()
        for (txn in transactionDao.getForAccountOnce(accountId)) {
            val remaining = splitLinkedRemaining(txn) ?: continue
            overrides[txn.id] = remaining
        }
        return overrides
    }

    /**
     * Total amount recovered so far via Split-linked "Me" totals in this account — i.e. how much
     * of the original outlay other participants have actually paid back on their own accounts.
     * This is what [observeAccountSummary]/[observeAccountSummaryById] subtract from the raw
     * Spent sum, since that recovery has no transaction of its own in *this* account to naturally
     * offset it (unlike a normal same-account settlement, where the real repayment transaction
     * already counts on its own and doesn't need this adjustment).
     */
    private fun observeSplitRecoveredTotal(accountId: Long): Flow<Long> =
        transactionDao.observeForAccount(accountId).map { txns ->
            txns.sumOf { txn -> splitLinkedRemaining(txn)?.let { remaining -> txn.amountMinor - remaining } ?: 0L }
        }

    /**
     * Computes each participant's recovered/remaining amount (by looking up their linked
     * transaction's live settlement state, never a stored value) and the split's overall status —
     * ACTIVE (nothing recovered yet), PARTIALLY_SETTLED, or FULLY_SETTLED once every recoverable
     * share is in.
     */
    suspend fun computeSplitStatus(split: com.palan.hisaab.data.dao.SplitRecordWithParticipants): SplitStatusSummary {
        var totalRecovered = 0L
        var totalRemaining = 0L
        val statuses = split.participants.map { p ->
            when {
                p.isPayer -> SplitParticipantStatus(p.name, p.amountMinor, isPayer = true, recorded = p.recorded, recoveredMinor = 0L, remainingMinor = 0L, settled = true, transactionId = p.transactionId)
                !p.recorded || p.transactionId == null -> SplitParticipantStatus(p.name, p.amountMinor, isPayer = false, recorded = false, recoveredMinor = 0L, remainingMinor = 0L, settled = false, transactionId = null)
                else -> {
                    val linked = transactionDao.getById(p.transactionId)
                    if (linked == null) {
                        SplitParticipantStatus(p.name, p.amountMinor, isPayer = false, recorded = true, recoveredMinor = 0L, remainingMinor = p.amountMinor, settled = false, transactionId = p.transactionId)
                    } else {
                        val remaining = remainingOf(linked)
                        val recovered = (p.amountMinor - remaining).coerceAtLeast(0L)
                        totalRecovered += recovered
                        totalRemaining += remaining
                        SplitParticipantStatus(p.name, p.amountMinor, isPayer = false, recorded = true, recoveredMinor = recovered, remainingMinor = remaining, settled = remaining <= 0L, transactionId = p.transactionId)
                    }
                }
            }
        }
        val overall = when {
            totalRemaining <= 0L && statuses.any { !it.isPayer && it.recorded } -> SplitOverallStatus.FULLY_SETTLED
            totalRecovered > 0L -> SplitOverallStatus.PARTIALLY_SETTLED
            else -> SplitOverallStatus.ACTIVE
        }
        return SplitStatusSummary(split.record, statuses, totalRecovered, totalRemaining, overall)
    }

    /**
     * If [transactionId] belongs to a Split — either the payer's combined total or a single
     * participant's own linked share — returns that split's full record. Used so tapping any
     * Split-related transaction opens Split Details instead of the generic Edit Transaction
     * dialog; null means it's just an ordinary transaction.
     */
    suspend fun findSplitForTransaction(transactionId: Long): com.palan.hisaab.data.dao.SplitRecordWithParticipants? =
        splitDao.findByMeTransactionId(transactionId) ?: splitDao.findByParticipantTransactionId(transactionId)

    /** Live, always-current status for one split — Split Details reads from this so it updates the moment a linked settlement happens anywhere, not just when this split's own rows change. */
    fun observeSplitStatus(splitId: Long): Flow<SplitStatusSummary?> =
        splitDao.observeById(splitId).combine(transactionDao.observeChangeMarker()) { record, _ ->
            record?.let { computeSplitStatus(it) }
        }

    /** Every repayment applied against [targetTransactionId], oldest first — the "Settlement History" list on Split Details. */
    suspend fun getSettlementHistory(targetTransactionId: Long): List<Pair<Transaction, Long>> =
        repaymentAllocationDao.getForTarget(targetTransactionId)
            .mapNotNull { alloc -> transactionDao.getById(alloc.repaymentTransactionId)?.let { it to alloc.allocatedAmountMinor } }
            .sortedBy { (t, _) -> t.date ?: 0L }

    /**
     * Renames a Split — updates the description on the SplitRecord and every transaction linked
     * to it (the payer's combined total and each participant's own share), so the name stays
     * consistent everywhere it's shown. Amounts, participants, and the payer are intentionally
     * not editable here: changing those after settlements have already happened against the
     * original shares would require re-deriving every allocation, which risks silently
     * misrepresenting money that's already moved.
     */
    suspend fun renameSplit(splitId: Long, newDescription: String): Unit = db.withTransaction {
        val record = splitDao.getRecordById(splitId) ?: return@withTransaction
        val label = newDescription.trim().ifBlank { record.description }
        splitDao.updateRecord(record.copy(description = label))
        record.meTransactionId?.let { id -> transactionDao.getById(id)?.let { transactionDao.update(it.copy(description = label)) } }
        for (p in splitDao.getParticipantsForSplit(splitId)) {
            val txnId = p.transactionId ?: continue
            transactionDao.getById(txnId)?.let { transactionDao.update(it.copy(description = label)) }
        }
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
                tObj.put("isLoan", t.isLoan)
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

    /** Restores accounts + transactions from a backup produced by [exportAllToJson]. Always creates new accounts (never merges into existing ones), so re-importing is safe. Older backups (pre-repayment feature, or pre-isLoan) simply lack those fields, which default to false — a backup taken before the Loan Given/Loan Taken types were folded into isLoan restores as plain, non-loan transactions. Repayment allocation links themselves aren't part of the backup, so a restored repayment transaction comes back as a plain flagged transaction with its settlements already baked into each loan's "settled" state at export time — nothing is lost balance-wise. Returns the number of accounts restored. */
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
                val rawType = tObj.getString("type")
                // Old backups (pre-isLoan) may still say "LOAN_GIVEN"/"LOAN_TAKEN" — those values
                // no longer exist on TransactionType, so map them the same way MIGRATION_6_7 does
                // for the database itself: SPENT/RECEIVED with isLoan implied true.
                val legacyIsLoan = rawType == "LOAN_GIVEN" || rawType == "LOAN_TAKEN"
                val type = when (rawType) {
                    "LOAN_GIVEN" -> TransactionType.SPENT
                    "LOAN_TAKEN" -> TransactionType.RECEIVED
                    else -> runCatching { TransactionType.valueOf(rawType) }.getOrNull() ?: continue
                }
                transactionDao.insert(
                    Transaction(
                        accountId = accountId,
                        type = type,
                        amountMinor = tObj.optLong("amountMinor", 0L),
                        description = tObj.optString("description", ""),
                        date = if (tObj.isNull("date")) null else tObj.optLong("date"),
                        category = if (tObj.isNull("category")) null else tObj.optString("category"),
                        settled = tObj.optBoolean("settled", false),
                        isRepayment = tObj.optBoolean("isRepayment", false),
                        isLoan = tObj.optBoolean("isLoan", legacyIsLoan)
                    )
                )
            }
            count++
        }
        return count
    }

    /** Looks up an existing account by exact (case-insensitive) name — used before an import creates a new account, so the user can be asked to merge instead of silently duplicating it. */
    suspend fun findAccountByName(name: String): Account? = accountDao.findByExactName(name.trim())

    /** Recreates an account + its transactions from a parsed "Share as Text" export. Always creates a new account (never merges into an existing one) — used for "Create Separate Account" once the caller has already checked [findAccountByName] and asked the user. */
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
            transactionDao.insert(
                Transaction(
                    accountId = accountId,
                    type = if (txn.isSpent) TransactionType.SPENT else TransactionType.RECEIVED,
                    amountMinor = txn.amountMinor,
                    description = txn.description,
                    date = txn.dateMillis,
                    settled = txn.isSettled,
                    isLoan = txn.isLoan
                )
            )
        }
        return accountId
    }

    /**
     * Merges a parsed "Share as Text" import into an already-existing [accountId] instead of
     * creating a duplicate account — used for "Merge with Existing Account". Balances recalculate
     * automatically since they're always derived live from the transactions table; nothing needs
     * to be rewritten on the existing rows.
     *
     * Before inserting each imported transaction, it's compared against every transaction already
     * in the account by date + amount + description + type — an exact match on all four is treated
     * as the same transaction re-appearing (e.g. importing the same export twice, or two exports
     * with overlapping date ranges) and is skipped rather than added again. Returns the count of
     * transactions actually added.
     */
    suspend fun mergeParsedHisab(accountId: Long, parsed: com.palan.hisaab.util.ParsedHisab): Int {
        val existing = transactionDao.getForAccountOnce(accountId)
        var added = 0
        parsed.transactions.forEach { txn ->
            val type = if (txn.isSpent) TransactionType.SPENT else TransactionType.RECEIVED
            val isDuplicate = existing.any {
                it.date == txn.dateMillis &&
                    it.amountMinor == txn.amountMinor &&
                    it.description.equals(txn.description, ignoreCase = true) &&
                    it.type == type &&
                    it.isLoan == txn.isLoan
            }
            if (!isDuplicate) {
                transactionDao.insert(
                    Transaction(
                        accountId = accountId,
                        type = type,
                        amountMinor = txn.amountMinor,
                        description = txn.description,
                        date = txn.dateMillis,
                        settled = txn.isSettled,
                        isLoan = txn.isLoan
                    )
                )
                added++
            }
        }
        return added
    }
}

data class SplitShare(
    val name: String,
    val amountMinor: Long,
    val existingAccountId: Long? = null,
    val isSelf: Boolean = false
)

/** What [HisaabRepository.applySplit] actually did: which accounts got a real transaction posted, and which participants' shares couldn't be (a debt between two people neither of whom is "Me"). */
data class SplitApplyResult(
    val postedToAccountIds: List<Long>,
    val unrecordedParticipants: List<String>
)

enum class SplitOverallStatus { ACTIVE, PARTIALLY_SETTLED, FULLY_SETTLED }

/** One participant's live settlement state within a split, for Split History. */
data class SplitParticipantStatus(
    val name: String,
    val amountMinor: Long,
    val isPayer: Boolean,
    val recorded: Boolean,
    val recoveredMinor: Long,
    val remainingMinor: Long,
    val settled: Boolean,
    /** The transaction id this participant's share actually posted to (their own account's Spent/Received entry, or the payer's combined total for the payer's own row) — null when [recorded] is false. Used to look up settlement history for this specific participant. */
    val transactionId: Long? = null
)

/** A split's full live status, for Split History — see [HisaabRepository.computeSplitStatus]. */
data class SplitStatusSummary(
    val record: SplitRecord,
    val participants: List<SplitParticipantStatus>,
    val totalRecoveredMinor: Long,
    val totalRemainingMinor: Long,
    val overallStatus: SplitOverallStatus
)
