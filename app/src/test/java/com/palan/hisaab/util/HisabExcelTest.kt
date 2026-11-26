package com.palan.hisaab.util

import com.palan.hisaab.data.entity.Transaction
import com.palan.hisaab.data.entity.TransactionType
import com.palan.hisaab.viewmodel.AccountUiState
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-JVM tests for the Excel export layout and its reader — no Android runtime needed. */
class HisabExcelTest {

    private fun day(millis: Long) = SimpleDateFormat("yyyy-MM-dd").format(Date(millis))

    @Test
    fun exportedSheet_readsBackIntoTheSameTransactions() {
        val txns = listOf(
            Transaction(id = 1, accountId = 1, type = TransactionType.SPENT, amountMinor = 135050L, description = "Driving & Licence <fees>", date = 1_700_000_000_000L),
            Transaction(id = 2, accountId = 1, type = TransactionType.RECEIVED, amountMinor = 50000L, description = "Refund", date = null, isLoan = true, settled = true)
        )
        val state = AccountUiState(accountName = "Manish Kaka", received = 50000L, spent = 135050L, transactions = txns)

        val parsed = HisabExcel.parse(XlsxCodec.read(HisabExcel.toXlsxBytes(state, txns)))

        assertNotNull(parsed)
        assertEquals("Manish Kaka", parsed!!.accountName)
        assertEquals(2, parsed.transactions.size)
        val a = parsed.transactions.first { it.description.startsWith("Driving") }
        assertEquals("Driving & Licence <fees>", a.description) // XML escaping survives the round trip
        assertEquals(135050L, a.amountMinor)
        assertTrue(a.isSpent)
        assertEquals(day(1_700_000_000_000L), day(a.dateMillis!!))
        val b = parsed.transactions.first { it.description == "Refund" }
        assertTrue(b.isLoan)
        assertTrue(b.isSettled)
        assertTrue(!b.isSpent)
        assertNull(b.dateMillis) // "No date" stays no date — never silently turned into today
    }

    @Test
    fun selectionExport_includesOnlyTheGivenTransactions() {
        val all = listOf(
            Transaction(id = 1, accountId = 1, type = TransactionType.SPENT, amountMinor = 1000L, description = "keep", date = null),
            Transaction(id = 2, accountId = 1, type = TransactionType.SPENT, amountMinor = 2000L, description = "drop", date = null)
        )
        val state = AccountUiState(accountName = "Pick", transactions = all)

        val parsed = HisabExcel.parse(XlsxCodec.read(HisabExcel.toXlsxBytes(state, listOf(all[0]))))!!

        assertEquals(listOf("keep"), parsed.transactions.map { it.description })
    }

    @Test
    fun sheetThatIsNotAHisaabExport_returnsNull() {
        val bytes = XlsxCodec.write("Other", listOf(listOf("Name", "Qty"), listOf("Tea", 2)))
        assertNull(HisabExcel.parse(XlsxCodec.read(bytes)))
    }

    /** A file saved by Excel itself: shared strings instead of inline text, and the date stored as a serial number. */
    @Test
    fun excelStyleFile_withSharedStringsAndDateSerial_isRead() {
        val strings = listOf("Account", "Pat", "Date", "Description", "Type", "Amount", "Tea", "Spent")
        val sst = strings.joinToString("") { "<si><t>$it</t></si>" }
        val sheet = """<?xml version="1.0"?><worksheet><sheetData>
            <row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c></row>
            <row r="2"><c r="A2" t="s"><v>2</v></c><c r="B2" t="s"><v>3</v></c><c r="C2" t="s"><v>4</v></c><c r="D2" t="s"><v>5</v></c></row>
            <row r="3"><c r="A3"><v>46279</v></c><c r="B3" t="s"><v>6</v></c><c r="C3" t="s"><v>7</v></c><c r="D3"><v>12.5</v></c></row>
            </sheetData></worksheet>"""
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("xl/sharedStrings.xml")); zip.write("<sst>$sst</sst>".toByteArray()); zip.closeEntry()
            zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml")); zip.write(sheet.toByteArray()); zip.closeEntry()
        }

        val parsed = HisabExcel.parse(XlsxCodec.read(out.toByteArray()))!!

        assertEquals("Pat", parsed.accountName)
        val t = parsed.transactions.single()
        assertEquals("Tea", t.description)
        assertEquals(1250L, t.amountMinor)
        assertTrue(t.isSpent)
        assertEquals("2026-09-14", day(t.dateMillis!!)) // serial 46279 = 14 Sep 2026
    }
}
