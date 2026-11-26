package com.palan.hisaab.util

import com.palan.hisaab.data.entity.Transaction
import com.palan.hisaab.data.entity.TransactionType
import com.palan.hisaab.viewmodel.AccountUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * The spreadsheet layout for one account, built on [XlsxCodec]:
 *
 *   Row 1      "Hisaab account export"
 *   Rows 2-7   Account / Phone / Initial Balance / Received Total / Spent Total / Current Balance
 *   Row 8      Transactions Included ("5 of 12" when only a selection was exported)
 *   Row 10     header: Date | Description | Type | Amount | Category | Loan | Repayment | Status | Remaining
 *   Row 11+    one row per transaction — Amount is always positive and Type says Received/Spent.
 *
 * [parse] reads that layout back into the same [ParsedHisab] the text importer produces, so an
 * Excel import goes through exactly the same duplicate-account / merge flow as a text import.
 * A spreadsheet carries the ledger lines only — for settlement links and split relationships,
 * use the JSON export, which is the lossless format.
 */
object HisabExcel {

    private val HEADER = listOf("Date", "Description", "Type", "Amount", "Category", "Loan", "Repayment", "Status", "Remaining")

    fun buildRows(state: AccountUiState, transactions: List<Transaction>): List<List<Any?>> {
        val rows = mutableListOf<List<Any?>>()
        rows.add(listOf("Hisaab account export"))
        rows.add(listOf("Account", state.accountName))
        rows.add(listOf("Phone", state.phoneNumber ?: ""))
        rows.add(listOf("Initial Balance", Money.minorToRupeeBigDecimal(state.initialBalance)))
        rows.add(listOf("Received Total", Money.minorToRupeeBigDecimal(state.received)))
        rows.add(listOf("Spent Total", Money.minorToRupeeBigDecimal(state.spent)))
        rows.add(listOf("Current Balance", Money.minorToRupeeBigDecimal(state.balance)))
        rows.add(listOf("Transactions Included", "${transactions.size} of ${state.transactions.size}"))
        rows.add(emptyList())
        rows.add(HEADER)
        transactions.sortedBy { it.date ?: 0L }.forEach { t ->
            val remaining = state.remainingFor(t)
            rows.add(
                listOf(
                    t.date?.let { Date(it).toDisplayString() } ?: "No date",
                    t.description,
                    if (t.type == TransactionType.RECEIVED) "Received" else "Spent",
                    Money.minorToRupeeBigDecimal(t.amountMinor),
                    t.category ?: "",
                    if (t.isLoan) "Yes" else "",
                    if (t.isRepayment) "Yes" else "",
                    if (t.settled) "Cleared" else "Active",
                    if (remaining > 0L && !t.settled) Money.minorToRupeeBigDecimal(remaining) else ""
                )
            )
        }
        return rows
    }

    fun toXlsxBytes(state: AccountUiState, transactions: List<Transaction>): ByteArray =
        XlsxCodec.write("Hisaab", buildRows(state, transactions))

    /** Null when the sheet isn't a recognizable Hisaab export (no account name or no transaction header row). */
    fun parse(rows: List<List<String>>): ParsedHisab? {
        fun cell(row: List<String>, i: Int) = if (i < 0) "" else row.getOrNull(i)?.trim().orEmpty()
        fun valueOfLabel(label: String): String? =
            rows.firstOrNull { cell(it, 0).equals(label, ignoreCase = true) }?.let { cell(it, 1) }

        val accountName = valueOfLabel("Account")?.takeIf { it.isNotBlank() } ?: return null
        val initialMinor = valueOfLabel("Initial Balance")?.let { parseAmount(it) } ?: 0L

        val headerIndex = rows.indexOfFirst {
            cell(it, 0).equals("Date", ignoreCase = true) && cell(it, 1).equals("Description", ignoreCase = true)
        }
        if (headerIndex < 0) return null
        val header = rows[headerIndex].map { it.trim().lowercase() }
        val dateCol = header.indexOf("date"); val descCol = header.indexOf("description")
        val typeCol = header.indexOf("type"); val amountCol = header.indexOf("amount")
        val loanCol = header.indexOf("loan"); val statusCol = header.indexOf("status")
        if (typeCol < 0 || amountCol < 0) return null

        val transactions = rows.drop(headerIndex + 1).mapNotNull { row ->
            val amountMinor = parseAmount(cell(row, amountCol)) ?: return@mapNotNull null
            if (amountMinor <= 0L) return@mapNotNull null
            val typeText = cell(row, typeCol)
            val isSpent = when {
                typeText.equals("Spent", ignoreCase = true) -> true
                typeText.equals("Received", ignoreCase = true) -> false
                else -> return@mapNotNull null // a row we can't classify is skipped, not guessed at
            }
            ParsedTransaction(
                dateMillis = parseDate(cell(row, dateCol)),
                description = cell(row, descCol).ifBlank { "Imported" },
                amountMinor = amountMinor,
                isSpent = isSpent,
                isLoan = cell(row, loanCol).let { it.equals("Yes", true) || it.equals("true", true) },
                isSettled = cell(row, statusCol).equals("Cleared", ignoreCase = true)
            )
        }
        return ParsedHisab(accountName, initialMinor, transactions)
    }

    private fun parseAmount(text: String): Long? =
        runCatching { Money.rupeeStringToMinor(text.replace("₹", "").trim()) }.getOrNull()

    /** "No date"/blank -> null; "14 Sep 2026" and "2026-09-14" as written by the app or typed by hand; an Excel date serial if Excel converted the cell to a real date. */
    private fun parseDate(text: String): Long? {
        val t = text.trim()
        if (t.isBlank() || t.equals("No date", ignoreCase = true)) return null
        runCatching { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).parse(t)?.time }.getOrNull()?.let { return it }
        runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(t)?.time }.getOrNull()?.let { return it }
        t.toDoubleOrNull()?.takeIf { it > 20000 }?.let { serial ->
            // Excel counts days from 1899-12-30; shift to local midnight so the calendar date is stable across time zones.
            val utcMillis = ((serial - 25569.0) * 86_400_000.0).toLong()
            return utcMillis - TimeZone.getDefault().getOffset(utcMillis)
        }
        return null
    }
}
