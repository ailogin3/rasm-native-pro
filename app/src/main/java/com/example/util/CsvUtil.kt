package com.example.util

import android.content.Context
import android.net.Uri

/**
 * Minimal CSV reader/writer (no extra library).
 *
 * Writing adds a UTF-8 byte-order mark so Excel shows the rupee sign and non-English names correctly,
 * and defuses cells that start with = or @ so a spreadsheet never runs them as formulas.
 */
object CsvUtil {

    private const val BOM = "\uFEFF"
    private const val BOM_AS_LATIN1 = "\u00EF\u00BB\u00BF"

    /** Quotes a cell when it contains a comma, quote or line break. */
    fun escape(cell: String): String {
        val safe = if (cell.startsWith("=") || cell.startsWith("@")) "'$cell" else cell
        val needsQuotes = safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuotes) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }

    /** Numbers are written as they are (so negatives stay numbers); everything else is escaped text. */
    private fun cellText(value: Any?): String = when (value) {
        null -> ""
        is Number -> value.toString()
        else -> escape(value.toString())
    }

    /**
     * The byte-order mark is added only when the text has non-English characters (rupee sign, Malayalam names...),
     * because Excel needs it to read those correctly. Plain English files stay clean in every app; some
     * phone spreadsheet apps show the mark as stray characters ("i>?") in the first cell.
     */
    fun build(header: List<String>, rows: List<List<Any?>>): String {
        val sb = StringBuilder()
        sb.append(header.joinToString(",") { escape(it) }).append("\r\n")
        rows.forEach { row -> sb.append(row.joinToString(",") { cellText(it) }).append("\r\n") }
        val body = sb.toString()
        return if (body.any { it.code > 127 }) BOM + body else body
    }

    fun writeToUri(context: Context, uri: Uri, text: String): Result<Unit> = runCatching {
        context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            ?: error("Could not open the file")
    }

    fun readFromUri(context: Context, uri: Uri): Result<String> = runCatching {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: error("Could not open the file")
    }

    /** Picks , ; or tab from the first line (Excel in some regions saves with semicolons). */
    private fun detectDelimiter(text: String): Char {
        val firstLine = text.lineSequence().firstOrNull { it.isNotBlank() } ?: return ','
        val candidates = listOf(',', ';', '\t')
        return candidates.maxByOrNull { d -> firstLine.count { it == d } }
            ?.takeIf { d -> firstLine.contains(d) } ?: ','
    }

    /** Parses CSV text into rows of trimmed cells. Blank rows are dropped. Handles quotes, "" escapes and CRLF. */
    fun parse(input: String): List<List<String>> {
        // Also drop the BOM when an app has re-saved it as three stray Latin-1 characters.
        val text = input.removePrefix(BOM).removePrefix(BOM_AS_LATIN1)
        val delimiter = detectDelimiter(text)
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var inQuotes = false

        fun endCell() {
            row.add(cell.toString().trim())
            cell.setLength(0)
        }

        fun endRow() {
            endCell()
            if (row.any { it.isNotEmpty() }) rows.add(row)
            row = mutableListOf()
        }

        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') {
                        cell.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    cell.append(c)
                }
            } else {
                when {
                    c == '"' && cell.isEmpty() -> inQuotes = true
                    c == delimiter -> endCell()
                    c == '\r' -> {
                        if (i + 1 < text.length && text[i + 1] == '\n') i++
                        endRow()
                    }
                    c == '\n' -> endRow()
                    else -> cell.append(c)
                }
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) endRow()
        return rows
    }
}
