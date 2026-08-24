package com.palan.hisaab.data.entity

enum class TransactionType {
    RECEIVED,
    SPENT,
    INITIAL_BALANCE,
    /** You gave money/goods to them — they owe you. Increases balance (a receivable). */
    LOAN_GIVEN,
    /** You received money/goods from them — you owe them. Decreases balance (a liability). */
    LOAN_TAKEN
}
