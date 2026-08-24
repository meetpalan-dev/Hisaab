package com.palan.hisaab.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palan.hisaab.data.HisaabRepository
import com.palan.hisaab.data.entity.Transaction
import com.palan.hisaab.data.entity.TransactionType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AccountUiState(
    val accountName: String = "",
    val initialBalance: Long = 0,
    val received: Long = 0,
    val spent: Long = 0,
    val loanGiven: Long = 0,
    val loanTaken: Long = 0,
    val transactions: List<Transaction> = emptyList()
) {
    val balance: Long get() = initialBalance + received - spent + loanGiven - loanTaken
    val hasLoans: Boolean get() = loanGiven != 0L || loanTaken != 0L
}

class AccountViewModel(
    private val repository: HisaabRepository,
    private val accountId: Long
) : ViewModel() {

    val uiState: StateFlow<AccountUiState> = combine(
        repository.observeTransactions(accountId),
        repository.observeAccountSummaryById(accountId)
    ) { transactions, summary ->
        AccountUiState(
            accountName = summary.account.name,
            initialBalance = summary.initialBalance,
            received = summary.received,
            spent = summary.spent,
            loanGiven = summary.loanGiven,
            loanTaken = summary.loanTaken,
            transactions = transactions.filter { it.type != TransactionType.INITIAL_BALANCE }
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

    fun setInitialBalance(amountMinor: Long, date: Long) {
        viewModelScope.launch { repository.setInitialBalance(accountId, amountMinor, date) }
    }

    fun deleteAccount(onDone: () -> Unit) {
        viewModelScope.launch {
            val acc = com.palan.hisaab.data.entity.Account(id = accountId, name = uiState.value.accountName)
            repository.deleteAccount(acc)
            onDone()
        }
    }

    fun renameAccount(newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            val acc = com.palan.hisaab.data.entity.Account(id = accountId, name = uiState.value.accountName)
            repository.renameAccount(acc, newName)
        }
    }
}
