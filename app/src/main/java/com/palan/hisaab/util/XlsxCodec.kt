package com.palan.hisaab.util

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * A minimal, dependency-free .xlsx reader/writer — one worksheet, plain cells, no styling. An
 * .xlsx is just a ZIP of XML parts, so this uses only java.util.zip and the platform XML parser;
 * no Excel library has to be pulled into the app (and none can fail to resolve in the build).
 * It opens in Excel, Google Sheets, and LibreOffice, and reads back both its own output and
 * ordinary files saved by Excel (shared strings and numeric cells are handled).
 *
 * Cell values: a [String] is written as text; a [Number] (including [BigDecimal]) as a real
 * numeric cell, so amounts can be summed in the spreadsheet; null is an empty cell.
 */
object XlsxCodec {

    fun write(sheetName: String, rows: List<List<Any?>>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun entry(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            entry(
                "[Content_Types].xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>"""
            )
            entry(
                "_rels/.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""
            )
            entry(
                "xl/workbook.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="${escape(sheetName.take(31))}" sheetId="1" r:id="rId1"/></sheets></workbook>"""
            )
            entry(
                "xl/_rels/workbook.xml.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>"""
            )

            val sb = StringBuilder()
            sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
            rows.forEachIndexed { r, row ->
                sb.append("""<row r="${r + 1}">""")
                row.forEachIndexed { c, value ->
                    val ref = columnName(c) + (r + 1)
                    when (value) {
                        null -> {}
                        is BigDecimal -> sb.append("""<c r="$ref"><v>${value.toPlainString()}</v></c>""")
                        is Number -> sb.append("""<c r="$ref"><v>$value</v></c>""")
                        else -> sb.append("""<c r="$ref" t="inlineStr"><is><t xml:space="preserve">${escape(value.toString())}</t></is></c>""")
                    }
                }
                sb.append("</row>")
            }
            sb.append("</sheetData></worksheet>")
            entry("xl/worksheets/sheet1.xml", sb.toString())
        }
        return out.toByteArray()
    }

    /** Rows of the first worksheet as text (numbers come back as their plain text form); empty cells are "". */
    fun read(bytes: ByteArray): List<List<String>> {
        var sheetXml: ByteArray? = null
        var sharedXml: ByteArray? = null
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                when {
                    e.name == "xl/sharedStrings.xml" -> sharedXml = zip.readBytes()
                    e.name == "xl/worksheets/sheet1.xml" -> sheetXml = zip.readBytes()
                    sheetXml == null && e.name.startsWith("xl/worksheets/sheet") && e.name.endsWith(".xml") -> sheetXml = zip.readBytes()
                }
            }
        }
        val sheet = sheetXml ?: return emptyList()
        val shared = sharedXml?.let { parseSharedStrings(it) } ?: emptyList()

        val doc = parse(sheet)
        val result = mutableListOf<MutableList<String>>()
        val rowNodes = doc.getElementsByTagName("row")
        for (i in 0 until rowNodes.length) {
            val rowEl = rowNodes.item(i) as Element
            val rowIndex = (rowEl.getAttribute("r").toIntOrNull() ?: (result.size + 1)) - 1
            while (result.size <= rowIndex) result.add(mutableListOf())
            val cells = result[rowIndex]
            var nextCol = 0
            val cellNodes = rowEl.getElementsByTagName("c")
            for (j in 0 until cellNodes.length) {
                val c = cellNodes.item(j) as Element
                val col = columnIndex(c.getAttribute("r")) ?: nextCol
                while (cells.size <= col) cells.add("")
                cells[col] = cellText(c, shared)
                nextCol = col + 1
            }
        }
        return result
    }

    private fun cellText(c: Element, shared: List<String>): String {
        val type = c.getAttribute("t")
        return when (type) {
            "inlineStr" -> textOf(firstChild(c, "is"))
            "s" -> firstChild(c, "v")?.textContent?.trim()?.toIntOrNull()?.let { shared.getOrNull(it) } ?: ""
            "b" -> if (firstChild(c, "v")?.textContent?.trim() == "1") "TRUE" else "FALSE"
            else -> firstChild(c, "v")?.textContent?.trim() ?: ""
        }
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val doc = parse(bytes)
        val items = doc.getElementsByTagName("si")
        return (0 until items.length).map { textOf(items.item(it)) }
    }

    /** All text under a node — covers both plain <t> and rich-text <r><t> runs. */
    private fun textOf(node: Node?): String {
        if (node == null) return ""
        val sb = StringBuilder()
        fun walk(n: Node) {
            if (n is Element && n.tagName.substringAfter(':') == "t") sb.append(n.textContent)
            else {
                var child = n.firstChild
                while (child != null) { walk(child); child = child.nextSibling }
            }
        }
        walk(node)
        return sb.toString()
    }

    private fun firstChild(parent: Element, name: String): Element? {
        var child = parent.firstChild
        while (child != null) {
            if (child is Element && child.tagName.substringAfter(':') == name) return child
            child = child.nextSibling
        }
        return null
    }

    private fun parse(bytes: ByteArray): Document =
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { isExpandEntityReferences = false }
        }.newDocumentBuilder().parse(ByteArrayInputStream(bytes))

    /** 0 -> A, 25 -> Z, 26 -> AA ... */
    private fun columnName(index: Int): String {
        var n = index
        val sb = StringBuilder()
        do {
            sb.insert(0, ('A' + n % 26))
            n = n / 26 - 1
        } while (n >= 0)
        return sb.toString()
    }

    /** "C12" -> 2. Null if the reference has no column letters. */
    private fun columnIndex(ref: String): Int? {
        val letters = ref.takeWhile { it.isLetter() }.uppercase()
        if (letters.isEmpty()) return null
        var n = 0
        for (ch in letters) n = n * 26 + (ch - 'A' + 1)
        return n - 1
    }

    private fun escape(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) {
            when {
                ch == '&' -> sb.append("&amp;")
                ch == '<' -> sb.append("&lt;")
                ch == '>' -> sb.append("&gt;")
                ch == '"' -> sb.append("&quot;")
                ch == '\'' -> sb.append("&apos;")
                ch.code < 0x20 && ch != '\n' && ch != '\r' && ch != '\t' -> {} // not legal in XML 1.0
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }
}
