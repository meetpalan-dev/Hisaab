package com.palan.hisaab.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.palan.hisaab.data.entity.Transaction
import com.palan.hisaab.data.entity.TransactionType
import com.palan.hisaab.viewmodel.AccountUiState
import java.io.File
import java.io.FileOutputStream
import java.util.Date

/**
 * Renders an account's Hisab as a PDF or PNG image (for Share as PDF / Share as Image), reusing
 * one drawing routine so both formats stay visually identical.
 *
 * Long histories are automatically paginated -- [drawHeader] and [drawTransactionRows] are the
 * same functions used both to *measure* how many rows fit per page (on a throwaway scratch
 * canvas) and to actually render each page, so the two can never drift out of sync. Every
 * transaction in [AccountUiState.transactions] always ends up on some page; nothing is ever
 * dropped to make it fit.
 */
object HisabDocumentExporter {

    private const val PAGE_WIDTH = 1080
    private const val PAGE_HEIGHT = 1400
    private const val MARGIN = 60f
    private const val ROW_HEIGHT = 40f
    private const val BOTTOM_MARGIN = 70f

    private val bgColor = Color.parseColor("#12141C")
    private val goldColor = Color.parseColor("#DFA859")
    private val creamColor = Color.parseColor("#F0EADE")
    private val mutedColor = Color.parseColor("#9AA0AE")
    private val greenColor = Color.parseColor("#3DBE7A")
    private val redColor = Color.parseColor("#E0604F")

    /**
     * Draws the page background, title, and (on page 1 only) the balance summary -- continuation
     * pages get a compact "(continued)" header instead, since the summary is only meaningful
     * once. Always draws a "Page X of Y" marker when there's more than one page. Returns the y
     * position transaction rows should start being drawn at on this page.
     */
    private fun drawHeader(
        canvas: Canvas,
        width: Int,
        accountName: String,
        state: AccountUiState,
        pageNumber: Int,
        totalPages: Int
    ): Float {
        canvas.drawColor(bgColor)

        var y = MARGIN + 50f
        val titlePaint = Paint().apply {
            color = goldColor; isAntiAlias = true; textSize = 56f
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        }
        canvas.drawText(accountName, MARGIN, y, titlePaint)

        if (totalPages > 1) {
            val pagePaint = Paint().apply { color = mutedColor; isAntiAlias = true; textSize = 24f }
            val label = "Page $pageNumber of $totalPages"
            canvas.drawText(label, width - MARGIN - pagePaint.measureText(label), MARGIN + 10f, pagePaint)
        }

        if (pageNumber == 1) {
            y += 70f
            val labelPaint = Paint().apply { color = mutedColor; isAntiAlias = true; textSize = 28f }
            canvas.drawText("Current Balance", MARGIN, y, labelPaint)

            y += 66f
            val balancePaint = Paint(titlePaint).apply { textSize = 68f }
            canvas.drawText(Money.format(state.balance), MARGIN, y, balancePaint)

            y += 46f
            val bodyPaint = Paint().apply { color = creamColor; isAntiAlias = true; textSize = 26f }
            canvas.drawText("Initial Balance: ${Money.format(state.initialBalance)}", MARGIN, y, bodyPaint)
            y += 36f
            canvas.drawText("Received: ${Money.format(state.received)}   Spent: ${Money.format(state.spent)}", MARGIN, y, bodyPaint)
            if (state.hasLoans) {
                y += 36f
                canvas.drawText(
                    "Loan Given: ${Money.format(state.loanGiven)}   Loan Taken: ${Money.format(state.loanTaken)}",
                    MARGIN, y, bodyPaint
                )
            }
        } else {
            y += 40f
            val bodyPaint = Paint().apply { color = mutedColor; isAntiAlias = true; textSize = 26f }
            canvas.drawText("(continued)", MARGIN, y, bodyPaint)
        }

        y += 40f
        val linePaint = Paint().apply { color = mutedColor; strokeWidth = 2f }
        canvas.drawLine(MARGIN, y, width - MARGIN, y, linePaint)
        y += 44f
        return y
    }

    /**
     * Draws as many transactions starting at [fromIndex] as fit before [height] runs out.
     * Returns the index of the first transaction that didn't fit -- equal to
     * `transactions.size` once everything has been drawn.
     */
    private fun drawTransactionRows(
        canvas: Canvas,
        width: Int,
        height: Int,
        startY: Float,
        transactions: List<Transaction>,
        fromIndex: Int
    ): Int {
        var y = startY
        val rowPaint = Paint().apply { isAntiAlias = true; textSize = 26f }
        var i = fromIndex
        while (i < transactions.size && y <= height - BOTTOM_MARGIN) {
            val txn = transactions[i]
            val dateText = txn.date?.let { Date(it).toDisplayString() } ?: "No date"
            rowPaint.color = if (txn.settled) mutedColor else creamColor
            val descText = if (txn.settled) "$dateText  ${txn.description}  (Cleared)" else "$dateText  ${txn.description}"
            canvas.drawText(descText, MARGIN, y, rowPaint)

            val isPositive = txn.type == TransactionType.RECEIVED || txn.type == TransactionType.LOAN_GIVEN
            rowPaint.color = if (txn.settled) mutedColor else if (isPositive) greenColor else redColor
            val amountText = Money.formatSigned(txn.amountMinor, txn.type)
            canvas.drawText(amountText, width - MARGIN - rowPaint.measureText(amountText), y, rowPaint)
            y += ROW_HEIGHT
            i++
        }
        return i
    }

    /**
     * Runs the exact same header+rows drawing routine on a throwaway scratch canvas purely to
     * find out how many pages the full transaction list needs, and where each page's slice of
     * transactions starts. Because this reuses [drawHeader]/[drawTransactionRows] instead of
     * recomputing the layout math separately, the page count can never drift from what actually
     * gets rendered.
     */
    private fun computePageStarts(accountName: String, state: AccountUiState): List<Int> {
        val transactions = state.transactions.sortedBy { it.date ?: 0L }
        val scratchBitmap = Bitmap.createBitmap(PAGE_WIDTH, PAGE_HEIGHT, Bitmap.Config.ARGB_8888)
        val scratchCanvas = Canvas(scratchBitmap)

        val starts = mutableListOf(0)
        var index = 0
        var pageNumber = 1
        do {
            val startY = drawHeader(scratchCanvas, PAGE_WIDTH, accountName, state, pageNumber, totalPages = 1)
            val nextIndex = drawTransactionRows(scratchCanvas, PAGE_WIDTH, PAGE_HEIGHT, startY, transactions, index)
            index = nextIndex
            pageNumber++
            if (index < transactions.size) starts.add(index)
        } while (index < transactions.size)

        scratchBitmap.recycle()
        return starts
    }

    fun createPdf(context: Context, accountName: String, state: AccountUiState): File {
        val transactions = state.transactions.sortedBy { it.date ?: 0L }
        val pageStarts = computePageStarts(accountName, state)
        val totalPages = pageStarts.size

        val document = PdfDocument()
        pageStarts.forEachIndexed { i, fromIndex ->
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, i + 1).create()
            val page = document.startPage(pageInfo)
            val startY = drawHeader(page.canvas, PAGE_WIDTH, accountName, state, i + 1, totalPages)
            drawTransactionRows(page.canvas, PAGE_WIDTH, PAGE_HEIGHT, startY, transactions, fromIndex)
            document.finishPage(page)
        }

        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "${sanitize(accountName)}_hisab.pdf")
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
        return file
    }

    /** Returns one PNG file per page -- the caller shares all of them together via [shareFiles]. */
    fun createImages(context: Context, accountName: String, state: AccountUiState): List<File> {
        val transactions = state.transactions.sortedBy { it.date ?: 0L }
        val pageStarts = computePageStarts(accountName, state)
        val totalPages = pageStarts.size
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }

        return pageStarts.mapIndexed { i, fromIndex ->
            val bitmap = Bitmap.createBitmap(PAGE_WIDTH, PAGE_HEIGHT, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val startY = drawHeader(canvas, PAGE_WIDTH, accountName, state, i + 1, totalPages)
            drawTransactionRows(canvas, PAGE_WIDTH, PAGE_HEIGHT, startY, transactions, fromIndex)

            val suffix = if (totalPages > 1) "_page${i + 1}" else ""
            val file = File(dir, "${sanitize(accountName)}_hisab$suffix.png")
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            file
        }
    }

    fun shareFile(context: Context, file: File, mimeType: String, chooserTitle: String) {
        val uri = FileProvider.getUriForFile(context, "com.palan.hisaab.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }

    /** Shares one or more files together -- a single file falls back to [shareFile]'s single-item intent, since ACTION_SEND_MULTIPLE with one attachment behaves inconsistently across share targets. */
    fun shareFiles(context: Context, files: List<File>, mimeType: String, chooserTitle: String) {
        if (files.size == 1) {
            shareFile(context, files[0], mimeType, chooserTitle)
            return
        }
        val uris = ArrayList(files.map { FileProvider.getUriForFile(context, "com.palan.hisaab.fileprovider", it) })
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = mimeType
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }

    private fun sanitize(name: String) = name.replace(Regex("[^A-Za-z0-9_-]"), "_")
}
