package com.palan.hisaab.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palan.hisaab.data.AccountSummary
import com.palan.hisaab.data.HisaabRepository
import com.palan.hisaab.data.entity.Account
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

    fun createAccount(name: String, startingBalanceRupees: String, phoneNumber: String = "", onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val minor = Money.rupeeStringToMinor(startingBalanceRupees)
            val id = repository.createAccount(name, minor, phoneNumber)
            onCreated(id)
        }
    }

    /**
     * Checks whether an account already exists with the same name as [parsed] before importing
     * it. If so, [onDuplicate] is invoked with the existing account so the UI can offer
     * Merge / Create Separate / Cancel instead of silently creating a duplicate; otherwise the
     * import proceeds immediately as a new account.
     */
    fun importHisab(parsed: ParsedHisab, onDuplicate: (Account) -> Unit, onImported: (Long) -> Unit) {
        viewModelScope.launch {
            val existing = repository.findAccountByName(parsed.accountName)
            if (existing != null) {
                onDuplicate(existing)
            } else {
                onImported(repository.importParsedHisab(parsed))
            }
        }
    }

    /** "Create Separate Account" — imports as a brand-new account even though one with the same name already exists. */
    fun importHisabAsNewAccount(parsed: ParsedHisab, onImported: (Long) -> Unit) {
        viewModelScope.launch { onImported(repository.importParsedHisab(parsed)) }
    }

    /** "Merge with Existing Account" — adds the imported transactions into [accountId], skipping exact duplicates. */
    fun mergeHisab(accountId: Long, parsed: ParsedHisab, onMerged: (addedCount: Int) -> Unit) {
        viewModelScope.launch { onMerged(repository.mergeParsedHisab(accountId, parsed)) }
    }
}
