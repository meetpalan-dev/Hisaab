package com.palan.hisaab.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.palan.hisaab.data.HisaabRepository
import com.palan.hisaab.util.SplitBill
import kotlinx.coroutines.launch

/** Names treated as "the current user" when deciding which side of a split bill to record. */
private val SELF_NAMES = setOf("me", "myself", "self", "i")

class SplitBillViewModel(private val repository: HisaabRepository) : ViewModel() {

    /**
     * Records this split into the existing per-person ledger:
     * - If you paid, everyone else's share becomes a LOAN_GIVEN against their account (they owe you).
     * - If someone else paid and "Me" is a participant, your own share becomes a LOAN_TAKEN against the payer's account (you owe them).
     * - If neither side is "Me", there's nothing for this app to track — it only ever records debts relative to you.
     * Returns how many ledger entries were created, or a failure with a message to show.
     */
    fun addToHisaab(bill: SplitBill, onResult: (Result<Int>) -> Unit) {
        val iAmPayer = bill.paidBy.trim().lowercase() in SELF_NAMES
        viewModelScope.launch {
            if (iAmPayer) {
                val owed = bill.owedShares()
                owed.forEach { p ->
                    repository.recordSplitBillShare(
                        name = p.name,
                        amountMinor = p.amountMinor,
                        isOwedToMe = true,
                        description = "Split: ${bill.title}"
                    )
                }
                onResult(Result.success(owed.size))
            } else {
                val myShare = bill.participants.firstOrNull { it.name.trim().lowercase() in SELF_NAMES }
                if (myShare == null) {
                    onResult(Result.failure(IllegalStateException("Add \"Me\" as a participant to record your own share")))
                } else {
                    repository.recordSplitBillShare(
                        name = bill.paidBy,
                        amountMinor = myShare.amountMinor,
                        isOwedToMe = false,
                        description = "Split: ${bill.title}"
                    )
                    onResult(Result.success(1))
                }
            }
        }
    }
}
