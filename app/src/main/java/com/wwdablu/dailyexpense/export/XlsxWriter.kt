package com.wwdablu.dailyexpense.export

import com.wwdablu.dailyexpense.data.CategoryTotal
import java.io.OutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Writes a [Report] as an Excel workbook (.xlsx) with two sheets:
 * "Summary" (totals and per-type amounts) and "Entries" (every entry in the period).
 * An .xlsx file is a zip of XML parts, so this writes them directly instead of pulling in a library.
 */
object XlsxWriter {

    // Cell style indexes; they match <cellXfs> in STYLES below.
    private const val S_TEXT = 0
    private const val S_HEADER = 1
    private const val S_TITLE = 2
    private const val S_NOTE = 3
    private const val S_MONEY = 4
    private const val S_DATE = 5
    private const val S_BOLD_MONEY = 6
    private const val S_BOLD = 7
    private const val S_PERCENT = 8

    private sealed interface Cell
    private data class Txt(val value: String, val style: Int = S_TEXT) : Cell
    private data class Num(val value: Double, val style: Int) : Cell
    private data object Blank : Cell

    private class Sheet(val name: String, val colWidths: List<Double>) {
        val rows = mutableListOf<List<Cell>>()
        fun row(vararg cells: Cell) { rows += cells.toList() }
        fun blank() { rows += emptyList<Cell>() }
    }

    private fun money(minor: Long, style: Int = S_MONEY) = Num(minor / 100.0, style)

    fun write(report: Report, out: OutputStream) {
        val sheets = listOf(summarySheet(report), entriesSheet(report))
        ZipOutputStream(out).use { zip ->
            fun put(path: String, xml: String) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(xml.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            put("[Content_Types].xml", contentTypes(sheets.size))
            put("_rels/.rels", ROOT_RELS)
            put("docProps/app.xml", appProps(report))
            put("docProps/core.xml", coreProps(report))
            put("xl/workbook.xml", workbook(sheets))
            put("xl/_rels/workbook.xml.rels", workbookRels(sheets.size))
            put("xl/styles.xml", STYLES)
            sheets.forEachIndexed { i, sheet -> put("xl/worksheets/sheet${i + 1}.xml", sheetXml(sheet)) }
        }
    }

    // ---- Sheet contents ----

    private fun summarySheet(r: Report) = Sheet("Summary", listOf(28.0, 18.0, 12.0)).apply {
        row(Txt(r.title, S_TITLE))
        row(Txt("Period", S_BOLD), Txt(r.periodLabel))
        row(Txt(r.generatedNote, S_NOTE))
        blank()
        row(Txt("Totals", S_HEADER), Txt("Amount", S_HEADER))
        row(Txt("Earned"), money(r.totals.income))
        row(Txt("Spent"), money(r.totals.expense))
        row(Txt("Net", S_BOLD), money(r.totals.income - r.totals.expense, S_BOLD_MONEY))
        if (r.budget != null) {
            row(Txt("Monthly budget"), money(r.budget))
            row(Txt("Budget left"), money(r.budget - r.totals.expense))
        }
        blank()
        typeTable("Spending by type", "Spent", r.spendingByType, r.totals.expense)
        blank()
        typeTable("Earnings by type", "Earned", r.earningsByType, r.totals.income)
    }

    private fun Sheet.typeTable(heading: String, amountLabel: String, rows: List<CategoryTotal>, total: Long) {
        row(Txt(heading, S_HEADER), Txt(amountLabel, S_HEADER), Txt("Share", S_HEADER))
        if (rows.isEmpty()) row(Txt("None"))
        rows.forEach { t ->
            row(
                Txt(t.categoryName ?: "Uncategorized"),
                money(t.total),
                Num(if (total == 0L) 0.0 else t.total.toDouble() / total, S_PERCENT),
            )
        }
        row(Txt("Total", S_BOLD), money(total, S_BOLD_MONEY))
    }

    private fun entriesSheet(r: Report) = Sheet("Entries", listOf(14.0, 18.0, 36.0, 14.0, 14.0)).apply {
        row(Txt("Date", S_HEADER), Txt("Type", S_HEADER), Txt("Note", S_HEADER), Txt("Expense", S_HEADER), Txt("Earning", S_HEADER))
        r.entries.forEach { item ->
            val e = item.expense
            row(
                Num(excelDate(LocalDate.ofEpochDay(e.epochDay)), S_DATE),
                Txt(item.categoryName ?: "Uncategorized"),
                Txt(e.note),
                if (e.isIncome) Blank else money(e.amountMinor),
                if (e.isIncome) money(e.amountMinor) else Blank,
            )
        }
        if (r.entries.isEmpty()) row(Txt("No entries in this period"))
        row(Txt("Total", S_BOLD), Blank, Blank, money(r.totals.expense, S_BOLD_MONEY), money(r.totals.income, S_BOLD_MONEY))
        blank()
        row(Txt(r.generatedNote, S_NOTE))
    }

    /** Excel stores dates as days since 30 Dec 1899. */
    private fun excelDate(d: LocalDate) = (d.toEpochDay() + 25569).toDouble()

    // ---- XML parts ----

    private fun esc(s: String) = buildString {
        s.forEach { c ->
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> if (c >= ' ' || c == '\t' || c == '\n') append(c)
            }
        }
    }

    private fun colName(index: Int): String {
        var n = index + 1
        val sb = StringBuilder()
        while (n > 0) { val r = (n - 1) % 26; sb.insert(0, ('A' + r)); n = (n - 1) / 26 }
        return sb.toString()
    }

    private fun sheetXml(sheet: Sheet) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        append("<cols>")
        sheet.colWidths.forEachIndexed { i, w -> append("""<col min="${i + 1}" max="${i + 1}" width="$w" customWidth="1"/>""") }
        append("</cols><sheetData>")
        sheet.rows.forEachIndexed { r, cells ->
            append("""<row r="${r + 1}">""")
            cells.forEachIndexed { c, cell ->
                val ref = "${colName(c)}${r + 1}"
                when (cell) {
                    is Txt -> if (cell.value.isNotEmpty()) append("""<c r="$ref" t="inlineStr" s="${cell.style}"><is><t xml:space="preserve">${esc(cell.value)}</t></is></c>""")
                    is Num -> append("""<c r="$ref" s="${cell.style}"><v>${cell.value}</v></c>""")
                    Blank -> Unit
                }
            }
            append("</row>")
        }
        append("</sheetData></worksheet>")
    }

    private fun contentTypes(sheetCount: Int) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        for (i in 1..sheetCount) {
            append("""<Override PartName="/xl/worksheets/sheet$i.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        }
        append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
        append("""<Override PartName="/docProps/app.xml" ContentType="application/vnd.openxmlformats-officedocument.extended-properties+xml"/>""")
        append("""<Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>""")
        append("</Types>")
    }

    private const val ROOT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties" Target="docProps/app.xml"/><Relationship Id="rId3" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/></Relationships>"""

    /** File properties also name the app, so Excel's File > Info shows where the file came from. */
    private fun appProps(r: Report) =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties"><Application>${esc(r.appName)}</Application></Properties>"""

    private fun coreProps(r: Report): String {
        val created = r.generatedAt.atZone(java.time.ZoneId.systemDefault()).toInstant().toString()
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"><dc:title>${esc(r.title + " – " + r.periodLabel)}</dc:title><dc:creator>${esc(r.appName)} app</dc:creator><dc:description>${esc(r.generatedNote)}</dc:description><dcterms:created xsi:type="dcterms:W3CDTF">$created</dcterms:created></cp:coreProperties>"""
    }

    private fun workbook(sheets: List<Sheet>) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""")
        sheets.forEachIndexed { i, s -> append("""<sheet name="${esc(s.name)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""") }
        append("</sheets></workbook>")
    }

    private fun workbookRels(sheetCount: Int) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        for (i in 1..sheetCount) {
            append("""<Relationship Id="rId$i" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet$i.xml"/>""")
        }
        append("""<Relationship Id="rId${sheetCount + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
        append("</Relationships>")
    }

    private const val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><numFmts count="2"><numFmt numFmtId="164" formatCode="&quot;₹&quot;#,##0.00;[Red]-&quot;₹&quot;#,##0.00"/><numFmt numFmtId="165" formatCode="dd-mmm-yyyy"/></numFmts><fonts count="4"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="14"/><name val="Calibri"/></font><font><i/><sz val="10"/><color rgb="FF666666"/><name val="Calibri"/></font></fonts><fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FFE8F5E9"/><bgColor indexed="64"/></patternFill></fill></fills><borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="9"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/><xf numFmtId="0" fontId="2" fillId="0" borderId="0" xfId="0" applyFont="1"/><xf numFmtId="0" fontId="3" fillId="0" borderId="0" xfId="0" applyFont="1"/><xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/><xf numFmtId="165" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/><xf numFmtId="164" fontId="1" fillId="0" borderId="0" xfId="0" applyNumberFormat="1" applyFont="1"/><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/><xf numFmtId="10" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/></cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>"""
}
