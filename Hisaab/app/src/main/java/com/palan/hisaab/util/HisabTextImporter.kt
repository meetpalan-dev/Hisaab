package com.palan.hisaab.util

import java.text.SimpleDateFormat
import java.util.Locale

data class ParsedTransaction(
    val description: String,
    val amountMinor: Long,
    val isSpent: Boolean,
    val isLoan: Boolean,
    val isSettled: Boolean,
    val dateMillis: Long?
)

data class ParsedHisab(
    val accountName: String,
    val initialBalanceMinor: Long,
    val transactions: List<ParsedTransaction>
)

/**
 * Parses the exact text layout produced by "Share as Text" on the Account screen:
 *
 *   <Account Name>
 *
 *   Initial Balance: ₹0
 *
 *   Transactions:
 *   24 Aug 2026 - Loan - - ₹200
 *   24 Aug 2026 - gave - - ₹200
 *
 *   Received Total: ₹0
 *   Spent Total: ₹400
 *   Current Balance: ₹-400
 *
 * The two total lines and the Current Balance line are ignored on import — they're
 * always recomputed live from the transactions, per the app's own "never let balance
 * go manually inconsistent" rule.
 */
object HisabTextImporter {

    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    // "24 Aug 2026 - Loan - - ₹200"  or  "24 Aug 2026 - Loan - + ₹200"
    private val lineRegex = Regex(
        """^(\d{1,2}\s+\w+\s+\d{4})\s*-\s*(.+?)\s*-\s*([+-])\s*₹?\s*([\d,]+(?:\.\d{1,2})?)\s*$"""
    )

    fun parse(text: String): ParsedHisab? {
        val lines = text.lines().map { it.trimEnd() }
        if (lines.isEmpty()) return null

        val accountName = lines.firstOrNull { it.isNotBlank() }?.trim() ?: return null

        val initialBalanceLine = lines.firstOrNull { it.trim().startsWith("Initial Balance:") }
        val initialBalanceMinor = initialBalanceLine
            ?.substringAfter("Initial Balance:")
            ?.let { amountToMinor(it) } ?: 0L

        val txnStart = lines.indexOfFirst { it.trim() == "Transactions:" }
        val transactions = mutableListOf<ParsedTransaction>()

        if (txnStart != -1) {
            for (i in (txnStart + 1) until lines.size) {
                val line = lines[i].trim()
                if (line.isEmpty()) continue
                if (line.endsWith("Total:") || line.startsWith("Received Total") ||
                    line.startsWith("Spent Total") || line.startsWith("Current Balance")
                ) break

                val match = lineRegex.find(line) ?: continue
                val (dateStr, descRaw, sign, amountStr) = match.destructured
                val date = runCatching { dateFormat.parse(dateStr)?.time }.getOrNull()
                val minor = amountToMinor(amountStr)
                val trimmedDesc = descRaw.trimEnd()
                val isLoan = trimmedDesc.endsWith("(Loan given)") || trimmedDesc.endsWith("(Loan taken)") ||
                    trimmedDesc.endsWith("(Loan given, Paid)") || trimmedDesc.endsWith("(Loan taken, Paid)")
                val isSettled = trimmedDesc.endsWith("Paid)")
                val desc = trimmedDesc
                    .removeSuffix("(Loan given, Paid)")
                    .removeSuffix("(Loan taken, Paid)")
                    .removeSuffix("(Loan given)")
                    .removeSuffix("(Loan taken)")
                    .trim()
                transactions.add(
                    ParsedTransaction(
                        description = desc,
                        amountMinor = minor,
                        isSpent = sign == "-",
                        isLoan = isLoan,
                        isSettled = isSettled,
                        dateMillis = date
                    )
                )
            }
        }

        return ParsedHisab(accountName, initialBalanceMinor, transactions)
    }

    private fun amountToMinor(raw: String): Long {
        val cleaned = raw.trim().removePrefix("₹").replace(",", "").trim()
        if (cleaned.isEmpty() || cleaned == "0") return 0L
        return try {
            Money.rupeeStringToMinor(cleaned.removePrefix("-"))
        } catch (e: Exception) {
            0L
        }
    }
}
