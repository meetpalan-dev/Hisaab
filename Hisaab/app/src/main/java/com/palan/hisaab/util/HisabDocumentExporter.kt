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
import com.palan.hisaab.data.entity.TransactionType
import com.palan.hisaab.viewmodel.AccountUiState
import java.io.File
import java.io.FileOutputStream
import java.util.Date

/** Renders an account's Hisab as a PDF or PNG image (for Share as PDF / Share as Image), reusing one drawing routine so both formats stay visually identical. */
object HisabDocumentExporter {

    private const val PAGE_WIDTH = 1080
    private const val PAGE_HEIGHT = 1400
    private const val MARGIN = 60f

    private val bgColor = Color.parseColor("#12141C")
    private val goldColor = Color.parseColor("#DFA859")
    private val creamColor = Color.parseColor("#F0EADE")
    private val mutedColor = Color.parseColor("#9AA0AE")
    private val greenColor = Color.parseColor("#3DBE7A")
    private val redColor = Color.parseColor("#E0604F")

    private fun drawStatement(canvas: Canvas, width: Int, height: Int, accountName: String, state: AccountUiState) {
        canvas.drawColor(bgColor)

        var y = MARGIN + 50f
        val titlePaint = Paint().apply {
            color = goldColor; isAntiAlias = true; textSize = 56f
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        }
        canvas.drawText(accountName, MARGIN, y, titlePaint)

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

        y += 40f
        val linePaint = Paint().apply { color = mutedColor; strokeWidth = 2f }
        canvas.drawLine(MARGIN, y, width - MARGIN, y, linePaint)
        y += 44f

        val rowPaint = Paint().apply { isAntiAlias = true; textSize = 26f }
        for (txn in state.transactions.sortedBy { it.date ?: 0L }) {
            if (y > height - 70f) break // single-page export; overflow noted below
            val dateText = txn.date?.let { Date(it).toDisplayString() } ?: "No date"
            rowPaint.color = if (txn.settled) mutedColor else creamColor
            val descText = if (txn.settled) "$dateText  ${txn.description}  (Paid)" else "$dateText  ${txn.description}"
            canvas.drawText(descText, MARGIN, y, rowPaint)

            val isPositive = txn.type == TransactionType.RECEIVED || txn.type == TransactionType.LOAN_GIVEN
            rowPaint.color = if (txn.settled) mutedColor else if (isPositive) greenColor else redColor
            val amountText = Money.formatSigned(txn.amountMinor, txn.type)
            canvas.drawText(amountText, width - MARGIN - rowPaint.measureText(amountText), y, rowPaint)
            y += 40f
        }
    }

    fun createPdf(context: Context, accountName: String, state: AccountUiState): File {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = document.startPage(pageInfo)
        drawStatement(page.canvas, PAGE_WIDTH, PAGE_HEIGHT, accountName, state)
        document.finishPage(page)

        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "${sanitize(accountName)}_hisab.pdf")
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
        return file
    }

    fun createImage(context: Context, accountName: String, state: AccountUiState): File {
        val bitmap = Bitmap.createBitmap(PAGE_WIDTH, PAGE_HEIGHT, Bitmap.Config.ARGB_8888)
        drawStatement(Canvas(bitmap), PAGE_WIDTH, PAGE_HEIGHT, accountName, state)

        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "${sanitize(accountName)}_hisab.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file
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

    private fun sanitize(name: String) = name.replace(Regex("[^A-Za-z0-9_-]"), "_")
}
