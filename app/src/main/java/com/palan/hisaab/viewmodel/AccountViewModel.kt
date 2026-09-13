package com.palan.hisaab.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palan.hisaab.data.HisaabRepository
import com.palan.hisaab.data.RepaymentResult
import com.palan.hisaab.data.entity.Transaction
import com.palan.hisaab.data.entity.TransactionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AccountUiState(
    val accountName: String = "",
    val phoneNumber: String? = null,
    val initialBalance: Long = 0,
    val received: Long = 0,
    val spent: Long = 0,
    val loanGiven: Long = 0,
    val loanTaken: Long = 0,
    val transactions: List<Transaction> = emptyList(),
    /** Sum of repayment allocations applied against each loan transaction id so far — used to show partial-repayment progress and to compute each row's remaining amount. */
    val allocatedByTransactionId: Map<Long, Long> = emptyMap(),
    /** For a Split's combined "Me" total transaction (see HisaabRepository.applySplit), its live recomputed remaining amount — overrides the normal allocation-based calculation for that one transaction id. Updates live, including when the recovery happens on a different account entirely. */
    val splitTotalOverrides: Map<Long, Long> = emptyMap()
) {
    val balance: Long get() = initialBalance + received - spent + loanGiven - loanTaken
    val hasLoans: Boolean get() = loanGiven != 0L || loanTaken != 0L

    /**
     * How much of a transaction is still unpaid via detailed repayment allocations — 0 for
     * a repayment transaction itself and for fully-settled ones. Applies to any Received,
     * Spent, Loan Given, or (legacy) Loan Taken transaction, since any of them can now be
     * the target of a "Repayment / Settle Hisaab" allocation, not just loans. For a Split's
     * combined "Me" total, uses the live cross-account [splitTotalOverrides] value instead.
     */
    fun remainingFor(t: Transaction): Long {
        if (t.isRepayment || t.type == TransactionType.INITIAL_BALANCE) return 0L
        if (t.settled) return 0L
        splitTotalOverrides[t.id]?.let { return it }
        val allocated = allocatedByTransactionId[t.id] ?: 0L
        return (t.amountMinor - allocated).coerceAtLeast(0L)
    }

    /** True once some (but not all) of a transaction's amount has been repaid via allocations (or, for a Split total, settled elsewhere). */
    fun isPartiallyPaid(t: Transaction): Boolean {
        if (t.isRepayment || t.type == TransactionType.INITIAL_BALANCE) return false
        if (t.settled) return false
        splitTotalOverrides[t.id]?.let { remaining -> return remaining in 1 until t.amountMinor }
        val allocated = allocatedByTransactionId[t.id] ?: 0L
        return allocated > 0L && allocated < t.amountMinor
    }

    /** Active = outstanding, not yet cleared. Cleared = manually or fully settled. Applies uniformly to every transaction type. */
    val activeTransactions: List<Transaction> get() = transactions.filter { !it.settled }
    val clearedTransactions: List<Transaction> get() = transactions.filter { it.settled }
}

class AccountViewModel(
    private val repository: HisaabRepository,
    private val accountId: Long
) : ViewModel() {

    val uiState: StateFlow<AccountUiState> = combine(
        repository.observeTransactions(accountId),
        repository.observeAccountSummaryById(accountId),
        repository.observeAllocatedSums(accountId),
        repository.observeSplitTotalOverrides(accountId)
    ) { transactions, summary, allocated, splitOverrides ->
        AccountUiState(
            accountName = summary.account.name,
            phoneNumber = summary.account.phoneNumber,
            initialBalance = summary.initialBalance,
            received = summary.received,
            spent = summary.spent,
            loanGiven = summary.loanGiven,
            loanTaken = summary.loanTaken,
            transactions = transactions.filter { it.type != TransactionType.INITIAL_BALANCE },
            allocatedByTransactionId = allocated,
            splitTotalOverrides = splitOverrides
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AccountUiState())

    fun addTransaction(type: TransactionType, amountMinor: Long, description: String, date: Long?, category: String?) {
        viewModelScope.launch {
            repository.addTransaction(
                Transaction(
                    accountId = accountId,
                    type = type,
                    amountMinor = amountMinor,
                    description = description,
                    date = date,
                    category = category
                )
            )
        }
    }

    fun updateTransaction(transaction: Transaction) {
        viewModelScope.launch { repository.updateTransaction(transaction) }
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch { repository.deleteTransaction(transaction) }
    }

    /** Toggles any transaction between Active and Cleared — used by the Edit Transaction button. */
    fun toggleSettled(transaction: Transaction) {
        viewModelScope.launch { repository.toggleSettled(transaction) }
    }

    /** Sets a transaction's Active/Cleared status explicitly — used by the swipe gesture and its Undo. */
    fun setSettled(transaction: Transaction, settled: Boolean) {
        viewModelScope.launch { repository.setSettled(transaction, settled) }
    }

    /**
     * "Settle Hisaab": records the real amount paid/received and automatically applies it
     * against this account's outstanding hisaab, oldest-first — no manual picking required.
     */
    fun settleHisaab(
        type: TransactionType,
        amountMinor: Long,
        description: String,
        date: Long?,
        onDone: (RepaymentResult) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.settleHisaab(accountId, type, amountMinor, description, date)
            onDone(result)
        }
    }

    fun setInitialBalance(amountMinor: Long, date: Long) {
        viewModelScope.launch { repository.setInitialBalance(accountId, amountMinor, date) }
    }

    fun updateDetails(newName: String, phoneNumber: String?) {
        viewModelScope.launch {
            val acc = com.palan.hisaab.data.entity.Account(id = accountId, name = uiState.value.accountName)
            repository.updateAccountDetails(acc, newName, phoneNumber)
        }
    }

    fun deleteAccount(onDone: () -> Unit) {
        viewModelScope.launch {
            val acc = com.palan.hisaab.data.entity.Account(id = accountId, name = uiState.value.accountName)
            repository.deleteAccount(acc)
            onDone()
        }
    }
}
