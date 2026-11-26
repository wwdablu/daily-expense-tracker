package com.wwdablu.dailyexpense.export

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.TextPaint
import android.text.TextUtils
import com.wwdablu.dailyexpense.data.CategoryTotal
import com.wwdablu.dailyexpense.ui.formatMoney
import java.io.OutputStream
import java.time.LocalDate

/**
 * Writes a [Report] as an A4 PDF: title, period, totals, spending and earnings by type, then
 * every entry. Long reports flow onto more pages; each page's footer says the app generated it.
 */
class PdfWriter private constructor(private val report: Report) {

    private val pageWidth = 595   // A4 in PostScript points
    private val pageHeight = 842
    private val margin = 40f
    private val contentRight = pageWidth - margin
    private val bottomLimit = pageHeight - 60f

    private val doc = PdfDocument()
    private var page: PdfDocument.Page? = null
    private var pageNumber = 0
    private var y = 0f
    private val canvas: Canvas get() = page!!.canvas

    private fun paint(size: Float, bold: Boolean = false, color: Int = Color.BLACK) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    private val titlePaint = paint(20f, bold = true)
    private val headingPaint = paint(13f, bold = true, color = Color.rgb(0x2E, 0x7D, 0x32))
    private val bodyPaint = paint(10.5f)
    private val boldPaint = paint(10.5f, bold = true)
    private val notePaint = paint(9f, color = Color.DKGRAY)
    private val greenPaint = paint(10.5f, color = Color.rgb(0x2E, 0x7D, 0x32))
    private val redPaint = paint(10.5f, color = Color.rgb(0xC6, 0x28, 0x28))
    private val linePaint = Paint().apply { color = Color.LTGRAY; strokeWidth = 0.8f }
    private val headerFill = Paint().apply { color = Color.rgb(0xE8, 0xF5, 0xE9) }

    private fun newPage() {
        page?.let { finishPage(it) }
        pageNumber++
        page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        y = margin
    }

    private fun finishPage(p: PdfDocument.Page) {
        val c = p.canvas
        val footerY = pageHeight - 28f
        c.drawLine(margin, footerY - 14f, contentRight, footerY - 14f, linePaint)
        c.drawText(report.generatedNote, margin, footerY, notePaint)
        val pageText = "Page $pageNumber"
        c.drawText(pageText, contentRight - notePaint.measureText(pageText), footerY, notePaint)
        doc.finishPage(p)
    }

    /** Starts a new page if [needed] points don't fit on this one. */
    private fun ensureSpace(needed: Float) { if (y + needed > bottomLimit) newPage() }

    private fun text(s: String, x: Float, p: TextPaint) = canvas.drawText(s, x, y, p)
    private fun textRight(s: String, right: Float, p: TextPaint) = canvas.drawText(s, right - p.measureText(s), y, p)
    private fun fit(s: String, width: Float, p: TextPaint) = TextUtils.ellipsize(s, p, width, TextUtils.TruncateAt.END).toString()

    private fun write(out: OutputStream) {
        newPage()
        y += 20f
        text(report.title, margin, titlePaint)
        y += 20f
        text("Period: ${report.periodLabel}", margin, boldPaint)
        y += 15f
        text(report.generatedNote, margin, notePaint)
        y += 28f

        // Totals
        heading("Totals")
        keyValue("Earned", formatMoney(report.totals.income), greenPaint)
        keyValue("Spent", formatMoney(report.totals.expense), bodyPaint)
        val net = report.totals.income - report.totals.expense
        keyValue("Net", signed(net), if (net < 0) redPaint else greenPaint, labelPaint = boldPaint)
        report.budget?.let { budget ->
            keyValue("Monthly budget", formatMoney(budget), bodyPaint)
            val left = budget - report.totals.expense
            keyValue(if (left >= 0) "Budget left" else "Over budget by", formatMoney(kotlin.math.abs(left)), if (left >= 0) greenPaint else redPaint)
        }
        y += 14f

        typeTable("Spending by type", report.spendingByType, report.totals.expense)
        typeTable("Earnings by type", report.earningsByType, report.totals.income)
        entriesTable()

        page?.let { finishPage(it) }
        page = null
        doc.writeTo(out)
        doc.close()
    }

    private fun signed(minor: Long) = (if (minor < 0) "− " else "") + formatMoney(kotlin.math.abs(minor))

    private fun heading(s: String) {
        ensureSpace(40f)
        y += 6f
        text(s, margin, headingPaint)
        y += 16f
    }

    private fun keyValue(label: String, value: String, valuePaint: TextPaint, labelPaint: TextPaint = bodyPaint) {
        ensureSpace(16f)
        text(label, margin, labelPaint)
        canvas.drawText(value, 260f - valuePaint.measureText(value), y, valuePaint)
        y += 16f
    }

    /** Shaded header row; [cols] are (label, x, rightAligned). Redrawn at the top of each new page. */
    private fun headerRow(cols: List<Triple<String, Float, Boolean>>) {
        ensureSpace(36f)
        canvas.drawRect(margin, y - 12f, contentRight, y + 5f, headerFill)
        cols.forEach { (label, x, right) -> if (right) textRight(label, x, boldPaint) else text(label, x, boldPaint) }
        y += 18f
    }

    private fun rowDivider() {
        canvas.drawLine(margin, y - 11f, contentRight, y - 11f, linePaint)
    }

    private fun typeTable(title: String, rows: List<CategoryTotal>, total: Long) {
        heading(title)
        val cols = listOf(Triple("Type", margin + 6f, false), Triple("Amount", 420f, true), Triple("Share", contentRight - 6f, true))
        headerRow(cols)
        if (rows.isEmpty()) {
            text("None", margin + 6f, notePaint); y += 16f
        }
        rows.forEach { t ->
            if (y + 16f > bottomLimit) { newPage(); headerRow(cols) }
            text(fit(t.categoryName ?: "Uncategorized", 300f, bodyPaint), margin + 6f, bodyPaint)
            textRight(formatMoney(t.total), 420f, bodyPaint)
            val pct = if (total == 0L) 0.0 else t.total * 100.0 / total
            textRight("%.1f%%".format(pct), contentRight - 6f, bodyPaint)
            y += 16f
            rowDivider()
        }
        ensureSpace(16f)
        text("Total", margin + 6f, boldPaint)
        textRight(formatMoney(total), 420f, boldPaint)
        y += 24f
    }

    private fun entriesTable() {
        heading("All entries (${report.entries.size})")
        val dateX = margin + 6f
        val typeX = 125f
        val noteX = 235f
        val amountRight = contentRight - 6f
        val cols = listOf(
            Triple("Date", dateX, false), Triple("Type", typeX, false),
            Triple("Note", noteX, false), Triple("Amount", amountRight, true),
        )
        headerRow(cols)
        if (report.entries.isEmpty()) {
            text("No entries in this period", dateX, notePaint); y += 16f
        }
        report.entries.forEach { item ->
            if (y + 16f > bottomLimit) { newPage(); headerRow(cols) }
            val e = item.expense
            text(LocalDate.ofEpochDay(e.epochDay).format(Report.ENTRY_DATE), dateX, bodyPaint)
            text(fit(item.categoryName ?: "Uncategorized", noteX - typeX - 8f, bodyPaint), typeX, bodyPaint)
            text(fit(e.note, 200f, bodyPaint), noteX, bodyPaint)
            val amount = (if (e.isIncome) "+ " else "− ") + formatMoney(e.amountMinor)
            textRight(amount, amountRight, if (e.isIncome) greenPaint else bodyPaint)
            y += 16f
            rowDivider()
        }
    }

    companion object {
        fun write(report: Report, out: OutputStream) = PdfWriter(report).write(out)
    }
}
