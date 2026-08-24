package com.palan.hisaab.util

import java.text.SimpleDateFormat
import java.util.Locale

data class ParsedTransaction(
    val description: String,
    val amountMinor: Long,
    val isSpent: Boolean,
    val isLoan: Boolean,
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

    // "24 Aug 2026 - Loan - - ₹200"  or  "No date - Loan - + ₹200"
    private val lineRegex = Regex(
        """^(\d{1,2}\s+\w+\s+\d{4}|No date)\s*-\s*(.+?)\s*-\s*([+-])\s*₹?\s*([\d,]+(?:\.\d{1,2})?)\s*$""",
        RegexOption.IGNORE_CASE
    )

    /**
     * True if this text is a whole "Hisaab Full Backup" export (multiple
     * accounts) rather than a single account's Share/Export text — callers
     * that only handle one account at a time should check this first and
     * point the user at the full-backup import instead of misparsing it.
     */
    fun isFullBackup(text: String): Boolean = text.trimStart().startsWith("Hisaab Full Backup")

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
                    line.startsWith("Spent Total") || line.startsWith("Loan Given Total") ||
                    line.startsWith("Loan Taken Total") || line.startsWith("Current Balance")
                ) break

                val match = lineRegex.find(line) ?: continue
                val (dateStr, descRaw, sign, amountStr) = match.destructured
                val date = if (dateStr.equals("No date", ignoreCase = true)) {
                    null
                } else {
                    runCatching { dateFormat.parse(dateStr)?.time }.getOrNull()
                }
                val minor = amountToMinor(amountStr)
                val isLoan = descRaw.trimEnd().endsWith("(Loan given)") || descRaw.trimEnd().endsWith("(Loan taken)")
                val desc = descRaw.trimEnd()
                    .removeSuffix("(Loan given)")
                    .removeSuffix("(Loan taken)")
                    .trim()
                transactions.add(
                    ParsedTransaction(
                        description = desc,
                        amountMinor = minor,
                        isSpent = sign == "-",
                        isLoan = isLoan,
                        dateMillis = date
                    )
                )
            }
        }

        return ParsedHisab(accountName, initialBalanceMinor, transactions)
    }

    /**
     * Splits a "Hisaab Full Backup" export (multiple accounts concatenated by
     * [com.palan.hisaab.util.HisabTextExporter.ACCOUNT_SEPARATOR]) back into one
     * [ParsedHisab] per account. Each block is only kept if it actually contains
     * a "Transactions:" section, which filters out the backup's own header block
     * — everything else reuses [parse] as-is, so a backup file is really just
     * several normal single-account exports pasted one after another.
     */
    fun parseBackup(text: String): List<ParsedHisab> =
        text.split(Regex("""\n=+\n"""))
            .filter { it.contains("Transactions:") }
            .mapNotNull { parse(it) }
            .filter { it.accountName.isNotBlank() }

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
