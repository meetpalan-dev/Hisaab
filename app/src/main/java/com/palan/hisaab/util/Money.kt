package com.palan.hisaab.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.util.Locale

/**
 * All money is stored/summed as Long "minor units" (paise). These helpers
 * convert to/from a user-entered rupee string (e.g. "1144.33") without ever
 * touching Double/Float, so totals can never drift from rounding errors.
 */
object Money {

    fun rupeeStringToMinor(input: String): Long {
        val cleaned = input.trim().replace(",", "")
        if (cleaned.isEmpty()) return 0L
        val bd = BigDecimal(cleaned).setScale(2, RoundingMode.HALF_UP)
        return bd.movePointRight(2).toLong()
    }

    fun minorToRupeeBigDecimal(minor: Long): BigDecimal =
        BigDecimal(minor).movePointLeft(2)

    private val displayFormat = DecimalFormat("#,##,##0.00")

    /** e.g. 114433 -> "₹1,144.33". Drops trailing ".00" when amount is whole. */
    fun format(minor: Long, withSymbol: Boolean = true): String {
        val bd = minorToRupeeBigDecimal(minor)
        val isWhole = bd.stripTrailingZeros().scale() <= 0
        val formatted = if (isWhole) {
            DecimalFormat("#,##,##0").format(bd)
        } else {
            displayFormat.format(bd)
        }
        return if (withSymbol) "₹$formatted" else formatted
    }

    fun formatSigned(minor: Long, type: com.palan.hisaab.data.entity.TransactionType): String {
        val prefix = when (type) {
            com.palan.hisaab.data.entity.TransactionType.RECEIVED -> "+ "
            com.palan.hisaab.data.entity.TransactionType.SPENT -> "- "
            com.palan.hisaab.data.entity.TransactionType.LOAN_GIVEN -> "+ "
            com.palan.hisaab.data.entity.TransactionType.LOAN_TAKEN -> "- "
            com.palan.hisaab.data.entity.TransactionType.INITIAL_BALANCE -> ""
        }
        return prefix + format(minor)
    }
}

fun java.util.Date.toDisplayString(): String =
    java.text.SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(this)
