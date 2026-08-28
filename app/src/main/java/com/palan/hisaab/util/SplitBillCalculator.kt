package com.palan.hisaab.util

/** One person's slice of a split bill. amountMinor is their share, in paise. */
data class SplitParticipant(
    val name: String,
    val amountMinor: Long
)

data class SplitBill(
    val title: String,
    val paidBy: String,
    val participants: List<SplitParticipant>
) {
    val totalMinor: Long get() = participants.sumOf { it.amountMinor }

    /** Everyone except whoever paid — each of these owes the payer their share. */
    fun owedShares(): List<SplitParticipant> =
        participants.filterNot { it.name.equals(paidBy, ignoreCase = true) }
}

object SplitBillCalculator {

    /**
     * Divides [totalMinor] as evenly as possible across [names]. Since paise
     * are integers, the remainder (if totalMinor isn't exactly divisible) is
     * handed one-by-one to the first few participants so every share is
     * within 1 paise of exact and the shares always sum back to the total.
     */
    fun splitEqually(totalMinor: Long, names: List<String>): List<SplitParticipant> {
        if (names.isEmpty()) return emptyList()
        val base = totalMinor / names.size
        val remainder = (totalMinor % names.size).toInt()
        return names.mapIndexed { index, name ->
            SplitParticipant(name, base + if (index < remainder) 1 else 0)
        }
    }
}

object SplitBillTextFormat {

    private const val HEADER_PREFIX = "Split Bill:"

    fun build(bill: SplitBill): String {
        val sb = StringBuilder()
        sb.appendLine("$HEADER_PREFIX ${bill.title}")
        sb.appendLine("Total: ${Money.format(bill.totalMinor)}")
        sb.appendLine("Paid by: ${bill.paidBy}")
        sb.appendLine()
        sb.appendLine("Participants:")
        bill.participants.forEach { p ->
            val paidTag = if (p.name.equals(bill.paidBy, ignoreCase = true)) " (paid)" else ""
            sb.appendLine("${p.name} - ${Money.format(p.amountMinor)}$paidTag")
        }
        val owed = bill.owedShares()
        if (owed.isNotEmpty()) {
            sb.appendLine()
            owed.forEach { p ->
                sb.appendLine("${p.name} owes ${bill.paidBy}: ${Money.format(p.amountMinor)}")
            }
        }
        return sb.toString()
    }

    /** True if this text looks like a Split Bill export rather than an account Hisab export. */
    fun isSplitBill(text: String): Boolean = text.trimStart().startsWith(HEADER_PREFIX)

    private val participantLineRegex = Regex(
        """^(.+?)\s*-\s*₹?\s*([\d,]+(?:\.\d{1,2})?)\s*(?:\(paid\))?\s*$""",
        RegexOption.IGNORE_CASE
    )

    fun parse(text: String): SplitBill? {
        val lines = text.lines().map { it.trim() }
        val headerLine = lines.firstOrNull { it.startsWith(HEADER_PREFIX) } ?: return null
        val title = headerLine.removePrefix(HEADER_PREFIX).trim().ifBlank { "Split Bill" }
        val paidBy = lines.firstOrNull { it.startsWith("Paid by:") }
            ?.substringAfter("Paid by:")?.trim()?.ifBlank { null } ?: "Me"

        val participantsStart = lines.indexOfFirst { it == "Participants:" }
        if (participantsStart == -1) return null

        val participants = mutableListOf<SplitParticipant>()
        for (i in (participantsStart + 1) until lines.size) {
            val line = lines[i]
            if (line.isEmpty()) break // blank line ends the participant list (owed-summary section follows)
            val match = participantLineRegex.find(line) ?: continue
            val (name, amountStr) = match.destructured
            val amount = Money.tryParseRupeesToMinor(amountStr) ?: continue
            participants.add(SplitParticipant(name.trim(), amount))
        }
        if (participants.isEmpty()) return null

        return SplitBill(title = title, paidBy = paidBy, participants = participants)
    }
}
