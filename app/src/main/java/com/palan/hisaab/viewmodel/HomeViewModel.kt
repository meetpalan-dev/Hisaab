package com.palan.hisaab.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palan.hisaab.data.AccountSummary
import com.palan.hisaab.data.HisaabRepository
import com.palan.hisaab.util.Money
import com.palan.hisaab.util.ParsedHisab
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(private val repository: HisaabRepository) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    val query: StateFlow<String> = searchQuery

    fun onQueryChange(q: String) { searchQuery.value = q }

    private val accountsFlow = searchQuery.flatMapLatest { q ->
        if (q.isBlank()) repository.observeAccounts() else repository.searchAccounts(q)
    }

    /** Emits, per visible account, a live-updating balance summary. */
    val accountSummaries: StateFlow<List<AccountSummary>> = accountsFlow
        .flatMapLatest { accounts ->
            if (accounts.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(accounts.map { repository.observeAccountSummary(it) }) { it.toList() }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createAccount(name: String, startingBalanceRupees: String, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            // Falls back to 0 for anything unparsable — the dialog already validates
            // before calling this, but never crash here regardless of caller.
            val minor = Money.tryParseRupeesToMinor(startingBalanceRupees) ?: 0L
            val id = repository.createAccount(name, minor)
            onCreated(id)
        }
    }

    fun importHisab(parsed: ParsedHisab, onImported: (Long) -> Unit) {
        viewModelScope.launch {
            val id = repository.importParsedHisab(parsed)
            onImported(id)
        }
    }
}
