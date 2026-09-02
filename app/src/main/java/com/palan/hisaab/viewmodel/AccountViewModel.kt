package com.palan.hisaab.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palan.hisaab.data.HisaabRepository
import com.palan.hisaab.data.RepaymentAllocationInput
import com.palan.hisaab.data.RepaymentResult
import com.palan.hisaab.data.dao.OutstandingHisaab
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
    val allocatedByTransactionId: Map<Long, Long> = emptyMap()
) {
    val balance: Long get() = initialBalance + received - spent + loanGiven - loanTaken
    val hasLoans: Boolean get() = loanGiven != 0L || loanTaken != 0L

    /** How much of a loan transaction is still unpaid — 0 for non-loan transactions and for fully-settled ones. */
    fun remainingFor(t: Transaction): Long {
        if (t.type != TransactionType.LOAN_GIVEN && t.type != TransactionType.LOAN_TAKEN) return 0L
        if (t.settled) return 0L
        val allocated = allocatedByTransactionId[t.id] ?: 0L
        return (t.amountMinor - allocated).coerceAtLeast(0L)
    }

    /** True once some (but not all) of a loan transaction has been repaid. */
    fun isPartiallyPaid(t: Transaction): Boolean {
        if (t.type != TransactionType.LOAN_GIVEN && t.type != TransactionType.LOAN_TAKEN) return false
        val allocated = allocatedByTransactionId[t.id] ?: 0L
        return !t.settled && allocated > 0L && allocated < t.amountMinor
    }

    /** Active = not a fully-cleared loan. Cleared = settled loan. Everything else (Received/Spent/Repayments/Initial Balance) counts as active either way. */
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
        repository.observeAllocatedSums(accountId)
    ) { transactions, summary, allocated ->
        AccountUiState(
            accountName = summary.account.name,
            phoneNumber = summary.account.phoneNumber,
            initialBalance = summary.initialBalance,
            received = summary.received,
            spent = summary.spent,
            loanGiven = summary.loanGiven,
            loanTaken = summary.loanTaken,
            transactions = transactions.filter { it.type != TransactionType.INITIAL_BALANCE },
            allocatedByTransactionId = allocated
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

    fun toggleLoanSettled(transaction: Transaction) {
        viewModelScope.launch { repository.toggleLoanSettled(transaction) }
    }

    /** Loads the outstanding hisaabs eligible to be covered by a repayment of [repaymentType] (SPENT -> outstanding Loan Taken, RECEIVED -> outstanding Loan Given), then invokes [onLoaded]. */
    fun loadOutstandingHisaabs(repaymentType: TransactionType, onLoaded: (List<OutstandingHisaab>) -> Unit) {
        viewModelScope.launch {
            onLoaded(repository.getOutstandingHisaabs(accountId, repaymentType))
        }
    }

    fun submitRepayment(
        type: TransactionType,
        amountMinor: Long,
        description: String,
        date: Long?,
        allocations: List<RepaymentAllocationInput>,
        onDone: (RepaymentResult) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.applyRepayment(accountId, type, amountMinor, description, date, allocations)
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
