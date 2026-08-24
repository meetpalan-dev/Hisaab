package com.palan.hisaab.util

import com.palan.hisaab.data.entity.Transaction
import com.palan.hisaab.data.entity.TransactionType
import java.util.Date

/**
 * Builds the plain-text "Hisab" layout used everywhere the app exports data —
 * the per-account Share/Export button and the Settings full-backup export both
 * call [buildAccountText], so there is exactly one text format to keep in sync
 * with [HisabTextImporter].
 */
object HisabTextExporter {

    /** Line placed between accounts in a full-backup export. [HisabTextImporter.parseBackup] splits on this. */
    const val ACCOUNT_SEPARATOR = "\n========================================\n"

    fun buildAccountText(
        accountName: String,
        initialBalanceMinor: Long,
        transactions: List<Transaction>
    ): String {
        val sb = StringBuilder()
        sb.appendLine(accountName)
        sb.appendLine()
        sb.appendLine("Initial Balance: ${Money.format(initialBalanceMinor)}")
        sb.appendLine()
        sb.appendLine("Transactions:")

        var received = 0L
        var spent = 0L
        var loanGiven = 0L
        var loanTaken = 0L

        transactions.sortedBy { it.date ?: 0L }.forEach { txn ->
            when (txn.type) {
                TransactionType.RECEIVED -> received += txn.amountMinor
                TransactionType.SPENT -> spent += txn.amountMinor
                TransactionType.LOAN_GIVEN -> loanGiven += txn.amountMinor
                TransactionType.LOAN_TAKEN -> loanTaken += txn.amountMinor
                TransactionType.INITIAL_BALANCE -> { /* excluded from the list already; ignore just in case */ }
            }
            val sign = if (txn.type == TransactionType.RECEIVED || txn.type == TransactionType.LOAN_GIVEN) "+" else "-"
            val dateText = txn.date?.let { Date(it).toDisplayString() } ?: "No date"
            val typeSuffix = when (txn.type) {
                TransactionType.LOAN_GIVEN -> " (Loan given)"
                TransactionType.LOAN_TAKEN -> " (Loan taken)"
                else -> ""
            }
            sb.appendLine("$dateText - ${txn.description}$typeSuffix - $sign ${Money.format(txn.amountMinor, withSymbol = true)}")
        }

        sb.appendLine()
        sb.appendLine("Received Total: ${Money.format(received)}")
        sb.appendLine("Spent Total: ${Money.format(spent)}")
        if (loanGiven != 0L || loanTaken != 0L) {
            sb.appendLine("Loan Given Total: ${Money.format(loanGiven)}")
            sb.appendLine("Loan Taken Total: ${Money.format(loanTaken)}")
        }
        sb.appendLine("Current Balance: ${Money.format(initialBalanceMinor + received - spent + loanGiven - loanTaken)}")
        return sb.toString()
    }

    /**
     * One account per (name, initialBalanceMinor, transactions) triple.
     * Produces a single text file: a short header, then every account's
     * normal export block back to back, separated by [ACCOUNT_SEPARATOR].
     */
    fun buildBackupText(accounts: List<Triple<String, Long, List<Transaction>>>): String {
        val header = buildString {
            appendLine("Hisaab Full Backup")
            appendLine("${accounts.size} account(s) — exported ${Date().toDisplayString()}")
        }
        if (accounts.isEmpty()) return header
        val blocks = accounts.map { (name, initial, txns) -> buildAccountText(name, initial, txns) }
        return header + ACCOUNT_SEPARATOR + blocks.joinToString(ACCOUNT_SEPARATOR)
    }
}
