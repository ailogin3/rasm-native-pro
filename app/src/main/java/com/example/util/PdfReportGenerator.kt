package com.example.util

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.ByteArrayOutputStream

data class PdfSummaryRow(val label: String, val value: String, val bold: Boolean = false)

/** One receipt or payment line. Exactly one of [receipt] / [payment] is normally non-zero. */
data class PdfLedgerRow(val date: String, val particulars: String, val receipt: Long, val payment: Long)

data class PdfBankRow(val date: String, val type: String, val reference: String, val amount: Long)

/**
 * Builds a simple multi-page A4 financial statement using Android's built-in PdfDocument
 * (no extra library needed). Amounts are printed with "Rs." so they render on every device font.
 */
object PdfReportGenerator {

    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 40f
    private const val RIGHT = PAGE_W - MARGIN

    fun generate(
        orgName: String,
        orgDetails: String,
        periodLabel: String,
        summary: List<PdfSummaryRow>,
        ledger: List<PdfLedgerRow>,
        bank: List<PdfBankRow>
    ): ByteArray {
        val doc = PdfDocument()
        var page: PdfDocument.Page? = null
        lateinit var canvas: Canvas
        var pageNo = 0
        var y = 0f

        val normal = Paint().apply { color = Color.BLACK; textSize = 10f; isAntiAlias = true }
        val bold = Paint().apply {
            color = Color.BLACK; textSize = 10f; isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val heading = Paint().apply {
            color = Color.BLACK; textSize = 12f; isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val grey = Paint().apply { color = Color.DKGRAY; textSize = 9f; isAntiAlias = true }
        val line = Paint().apply { color = Color.LTGRAY; strokeWidth = 0.8f }

        fun newPage() {
            page?.let { doc.finishPage(it) }
            pageNo++
            val p = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
            page = p
            canvas = p.canvas
            y = MARGIN + 6f
        }

        fun ensure(height: Float) {
            if (y + height > PAGE_H - MARGIN) newPage()
        }

        fun fit(text: String, paint: Paint, maxWidth: Float): String {
            if (paint.measureText(text) <= maxWidth) return text
            val count = paint.breakText(text, true, maxWidth - paint.measureText("..."), null)
            return text.take(count) + "..."
        }

        fun rightText(text: String, x: Float, paint: Paint) {
            canvas.drawText(text, x - paint.measureText(text), y, paint)
        }

        fun sectionTitle(title: String) {
            ensure(34f)
            y += 14f
            canvas.drawText(title, MARGIN, y, heading)
            y += 6f
            canvas.drawLine(MARGIN, y, RIGHT, y, line)
            y += 14f
        }

        newPage()

        // Header
        val title = Paint(heading).apply { textSize = 16f }
        canvas.drawText(fit(orgName, title, RIGHT - MARGIN), MARGIN, y + 10f, title)
        y += 26f
        if (orgDetails.isNotBlank()) {
            canvas.drawText(fit(orgDetails, grey, RIGHT - MARGIN), MARGIN, y, grey)
            y += 14f
        }
        canvas.drawText("Financial Statement", MARGIN, y + 4f, heading)
        y += 18f
        canvas.drawText("Period: $periodLabel", MARGIN, y, normal)
        y += 6f
        canvas.drawLine(MARGIN, y, RIGHT, y, line)
        y += 8f

        // Summary
        sectionTitle("Summary")
        summary.forEach { row ->
            ensure(16f)
            val p = if (row.bold) bold else normal
            canvas.drawText(row.label, MARGIN, y, p)
            rightText(row.value, RIGHT, p)
            y += 15f
        }

        // Ledger
        sectionTitle("Receipts & Payments (${ledger.size})")
        if (ledger.isEmpty()) {
            canvas.drawText("No receipts or payments in this period.", MARGIN, y, grey)
            y += 14f
        } else {
            val dateX = MARGIN
            val textX = MARGIN + 66f
            val receiptR = RIGHT - 85f
            val paymentR = RIGHT
            fun ledgerHeader() {
                ensure(20f)
                canvas.drawText("Date", dateX, y, bold)
                canvas.drawText("Particulars", textX, y, bold)
                rightText("Receipts", receiptR, bold)
                rightText("Payments", paymentR, bold)
                y += 5f
                canvas.drawLine(MARGIN, y, RIGHT, y, line)
                y += 12f
            }
            ledgerHeader()
            var totalReceipt = 0L
            var totalPayment = 0L
            ledger.forEach { row ->
                if (y + 14f > PAGE_H - MARGIN) {
                    newPage()
                    ledgerHeader()
                }
                canvas.drawText(row.date, dateX, y, normal)
                canvas.drawText(fit(row.particulars, normal, receiptR - 75f - textX), textX, y, normal)
                if (row.receipt != 0L) rightText(formatAmount(row.receipt), receiptR, normal)
                if (row.payment != 0L) rightText(formatAmount(row.payment), paymentR, normal)
                totalReceipt += row.receipt
                totalPayment += row.payment
                y += 14f
            }
            ensure(22f)
            canvas.drawLine(MARGIN, y - 9f, RIGHT, y - 9f, line)
            y += 4f
            canvas.drawText("Total", textX, y, bold)
            rightText(formatAmount(totalReceipt), receiptR, bold)
            rightText(formatAmount(totalPayment), paymentR, bold)
            y += 16f
        }

        // Bank transactions
        sectionTitle("Bank Log (${bank.size})")
        if (bank.isEmpty()) {
            canvas.drawText("No bank transactions in this period.", MARGIN, y, grey)
            y += 14f
        } else {
            bank.forEach { row ->
                ensure(14f)
                canvas.drawText(row.date, MARGIN, y, normal)
                canvas.drawText(row.type, MARGIN + 66f, y, normal)
                canvas.drawText(fit(row.reference, normal, 250f), MARGIN + 140f, y, normal)
                rightText(formatAmount(row.amount), RIGHT, normal)
                y += 14f
            }
        }

        page?.let { doc.finishPage(it) }
        val out = ByteArrayOutputStream()
        doc.writeTo(out)
        doc.close()
        return out.toByteArray()
    }

    /** Indian digit grouping (1,23,456) with an "Rs." prefix. */
    fun formatAmount(value: Long): String {
        val negative = value < 0
        val digits = Math.abs(value).toString()
        val grouped = if (digits.length <= 3) {
            digits
        } else {
            val head = digits.dropLast(3)
            val tail = digits.takeLast(3)
            head.reversed().chunked(2).joinToString(",").reversed() + "," + tail
        }
        return (if (negative) "-" else "") + "Rs. " + grouped
    }
}
