package com.palan.hisaab.data.entity

enum class TransactionType {
    RECEIVED,
    SPENT,
    INITIAL_BALANCE,
    /** You gave money/goods to them — they owe you. Increases balance (a receivable). */
    LOAN_GIVEN,
    /**
     * Legacy type, no longer offered when adding a new transaction — kept only so
     * existing data (and old text-export imports) keep working. A "received money
     * that's a loan" is now just a normal RECEIVED transaction, optionally tagged
     * with the "Loan" category. You owed them; decreases balance (a liability).
     */
    LOAN_TAKEN
}
