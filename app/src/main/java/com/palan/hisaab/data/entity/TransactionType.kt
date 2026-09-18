package com.palan.hisaab.data.entity

enum class TransactionType {
    RECEIVED,
    SPENT,
    INITIAL_BALANCE
    // LOAN_GIVEN and LOAN_TAKEN used to be separate values here. They're gone now -- a loan is a
    // plain RECEIVED or SPENT transaction with Transaction.isLoan = true instead (see that field's
    // doc comment for the sign rules). MIGRATION_6_7 rewrites every existing LOAN_GIVEN row to
    // SPENT+isLoan and every LOAN_TAKEN row to RECEIVED+isLoan, so by the time this enum is ever
    // read those two string values no longer exist in the database.
}
