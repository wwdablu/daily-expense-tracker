package com.wwdablu.dailyexpense.export

import java.io.OutputStream
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

const val CSV_MIME_TYPE = "text/csv"

/**
 * Writes every entry in a [Report] as CSV, one row per entry, for use in any spreadsheet:
 * Date (yyyy-MM-dd), Kind (Expense/Earning), Type, Note, Amount (plain number in rupees).
 * Starts with a UTF-8 byte-order mark so Excel reads non-English notes correctly.
 */
object CsvWriter {
    private val DATE = DateTimeFormatter.ISO_LOCAL_DATE

    fun write(report: Report, out: OutputStream) {
        val sb = StringBuilder("﻿")
        sb.append("Date,Kind,Type,Note,Amount (INR)\r\n")
        report.entries.forEach { item ->
            val e = item.expense
            sb.append(LocalDate.ofEpochDay(e.epochDay).format(DATE)).append(',')
                .append(if (e.isIncome) "Earning" else "Expense").append(',')
                .append(field(item.categoryName ?: "Uncategorized")).append(',')
                .append(field(e.note)).append(',')
                .append(BigDecimal.valueOf(e.amountMinor, 2).toPlainString())
                .append("\r\n")
        }
        out.write(sb.toString().toByteArray(Charsets.UTF_8))
    }

    /** Quotes a field when it contains a comma, quote or line break (RFC 4180). */
    internal fun field(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
}
