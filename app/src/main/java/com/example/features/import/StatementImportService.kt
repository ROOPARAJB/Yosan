package com.example.features.import

import com.example.data.local.entity.*
import com.example.features.rules.CategorizationEngine
import com.example.features.rules.CategorizationResult
import java.io.ByteArrayInputStream
import java.util.*
import java.util.zip.ZipInputStream

data class ParsedImportRow(
    val rawLine: String,
    val date: String,
    val description: String,
    val debitAmount: Double,
    val creditAmount: Double,
    val amount: Double,
    val balance: Double?,
    val referenceNumber: String,
    val suggestedCategory: String,
    val suggestedCategoryId: Long?,
    val suggestedType: TransactionType,
    val confidence: Float,
    val isDuplicate: Boolean = false
)

data class ImportPreviewResult(
    val totalRows: Int,
    val validRows: List<ParsedImportRow>,
    val duplicateCount: Int,
    val detectedColumns: List<String>,
    val fileName: String
)

object StatementImportService {

    private fun columnLetterToIndex(letter: String): Int {
        var index = 0
        for (char in letter) {
            if (char in 'A'..'Z') {
                index = index * 26 + (char - 'A' + 1)
            }
        }
        return index - 1
    }

    fun parseStatementBytes(
        bytes: ByteArray,
        fileName: String,
        rules: List<CategorizationRuleEntity>,
        existingTransactions: List<TransactionEntity>
    ): ImportPreviewResult {
        val lowerName = fileName.lowercase()
        val textContent = when {
            lowerName.endsWith(".xlsx") || lowerName.endsWith(".xls") -> extractTextFromXlsxBytes(bytes)
            lowerName.endsWith(".pdf") -> extractTextFromPdfBytes(bytes)
            else -> String(bytes, Charsets.UTF_8)
        }
        return parseStatementText(textContent, rules, existingTransactions, fileName)
    }

    private fun extractTextFromXlsxBytes(bytes: ByteArray): String {
        val sharedStrings = mutableListOf<String>()
        val sheetRows = mutableListOf<String>()
        val bytesMap = mutableMapOf<String, ByteArray>()

        try {
            val zip = ZipInputStream(ByteArrayInputStream(bytes))
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "xl/sharedStrings.xml" || entry.name.startsWith("xl/worksheets/sheet")) {
                    bytesMap[entry.name] = zip.readBytes()
                }
                entry = zip.nextEntry
            }
            zip.close()

            // 1. Extract shared strings
            bytesMap["xl/sharedStrings.xml"]?.let { xmlBytes ->
                val xml = String(xmlBytes, Charsets.UTF_8)
                val siRegex = Regex("<si>(.*?)</si>", RegexOption.DOT_MATCHES_ALL)
                val tRegex = Regex("<t[^>]*?>(.*?)</t>", RegexOption.DOT_MATCHES_ALL)
                siRegex.findAll(xml).forEach { siMatch ->
                    val siContent = siMatch.groupValues[1]
                    val tMatches = tRegex.findAll(siContent)
                    val itemText = tMatches.map { it.groupValues[1] }.joinToString("")
                    val cleanText = itemText
                        .replace("&amp;", "&")
                        .replace("&lt;", "<")
                        .replace("&gt;", ">")
                        .replace("&quot;", "\"")
                        .trim()
                    sharedStrings.add(sanitizeText(cleanText))
                }
            }

            // 2. Extract worksheets
            val sheetKeys = bytesMap.keys.filter { it.startsWith("xl/worksheets/sheet") }.sorted()
            for (sheetKey in sheetKeys) {
                val xmlBytes = bytesMap[sheetKey] ?: continue
                val xml = String(xmlBytes, Charsets.UTF_8)

                val rowRegex = Regex("<row[^>]*>(.*?)</row>", RegexOption.DOT_MATCHES_ALL)
                val cellRegex = Regex("<c([^>]*?)/>|<c([^>]*?)>((?:(?!<c).)*?)</c>", RegexOption.DOT_MATCHES_ALL)
                val rAttrRegex = Regex("r=\"([A-Z]+)(\\d+)\"")
                val tAttrRegex = Regex("t=\"([^\"]+)\"")
                val vRegex = Regex("<v>(.*?)</v>", RegexOption.DOT_MATCHES_ALL)

                rowRegex.findAll(xml).forEach { rowMatch ->
                    val rowXml = rowMatch.groupValues[1]
                    var maxColIdx = -1
                    val parsedCells = mutableListOf<Pair<Int, String>>()

                    cellRegex.findAll(rowXml).forEach { cellMatch ->
                        val g1 = cellMatch.groupValues[1]
                        val g2 = cellMatch.groupValues[2]
                        val g3 = cellMatch.groupValues[3]

                        val attrs = if (g1.isNotEmpty()) g1 else g2
                        val innerContent = g3

                        val rMatch = rAttrRegex.find(attrs)
                        if (rMatch != null) {
                            val colLetter = rMatch.groupValues[1]
                            val colIndex = columnLetterToIndex(colLetter)
                            if (colIndex > maxColIdx) {
                                maxColIdx = colIndex
                            }

                            val tMatch = tAttrRegex.find(attrs)
                            val cellType = tMatch?.groupValues[1] ?: ""

                            var cellText = ""
                            if (innerContent.isNotEmpty()) {
                                val vMatch = vRegex.find(innerContent)
                                val vVal = vMatch?.groupValues[1] ?: ""

                                cellText = when {
                                    cellType == "s" && vVal.isNotEmpty() -> {
                                        val idx = vVal.toIntOrNull()
                                        if (idx != null && idx in sharedStrings.indices) sharedStrings[idx] else vVal
                                    }
                                    vVal.isNotEmpty() -> vVal
                                    else -> ""
                                }
                            }
                            parsedCells.add(Pair(colIndex, sanitizeText(cellText)))
                        }
                    }

                    if (maxColIdx >= 0) {
                        val rowCells = Array(maxColIdx + 1) { "" }
                        var hasData = false
                        for ((colIndex, text) in parsedCells) {
                            rowCells[colIndex] = text
                            if (text.isNotEmpty()) {
                                hasData = true
                            }
                        }
                        if (hasData) {
                            sheetRows.add(rowCells.joinToString("\t"))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return if (sheetRows.isNotEmpty()) sheetRows.joinToString("\n") else String(bytes, Charsets.UTF_8)
    }

    private fun extractTextFromPdfBytes(bytes: ByteArray): String {
        val extractedLines = mutableListOf<String>()
        try {
            val raw = String(bytes, Charsets.ISO_8859_1)

            // Extract readable text chunks enclosed in PDF parenthetical syntax (text)
            val textRegex = Regex("\\(([^()\\\\]*(?:\\\\.[^()\\\\]*)*)\\)")
            var currentLine = StringBuilder()

            textRegex.findAll(raw).forEach { match ->
                val rawToken = match.groupValues[1]
                    .replace("\\(", "(")
                    .replace("\\)", ")")
                    .replace("\\n", " ")
                    .replace("\\r", "")
                    .replace("\\t", " ")
                    .trim()

                val cleanToken = sanitizeText(rawToken)

                if (cleanToken.length >= 2 && cleanToken.any { it.isLetterOrDigit() }) {
                    if (currentLine.isNotEmpty()) currentLine.append(",")
                    currentLine.append(cleanToken)

                    val lineStr = currentLine.toString()
                    if (lineStr.contains(Regex("\\d{1,4}[/-]\\d{1,2}[/-]\\d{1,4}"))) {
                        extractedLines.add(lineStr)
                        currentLine.clear()
                    }
                }
            }

            if (currentLine.isNotEmpty()) extractedLines.add(currentLine.toString())

            // Fallback: Scan line-by-line for printable text lines containing dates
            if (extractedLines.size < 2) {
                val lineRegex = Regex("[^\r\n]{10,}")
                lineRegex.findAll(raw).forEach { m ->
                    val line = sanitizeText(m.value)
                    if (line.contains(Regex("\\d{1,4}[/-]\\d{1,2}[/-]\\d{1,4}"))) {
                        if (line.length > 5) extractedLines.add(line)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return if (extractedLines.isNotEmpty()) extractedLines.joinToString("\n") else String(bytes, Charsets.UTF_8)
    }

    private fun sanitizeText(input: String): String {
        if (input.isBlank()) return ""
        // Remove non-printable ASCII and unreadable Mojibake symbols
        val cleaned = input.replace("[^\\x20-\\x7E]".toRegex(), " ").trim()
        val collapsed = cleaned.replace("\\s+".toRegex(), " ")
        // Ensure string contains at least some alphanumeric content
        return if (collapsed.any { it.isLetterOrDigit() }) collapsed else ""
    }

    fun parseStatementText(
        content: String,
        rules: List<CategorizationRuleEntity>,
        existingTransactions: List<TransactionEntity>,
        fileName: String = "bank_statement.csv"
    ): ImportPreviewResult {
        val lines = content.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (lines.isEmpty()) {
            return ImportPreviewResult(0, emptyList(), 0, emptyList(), fileName)
        }

        // Find the first line that matches the header criteria
        var headerIndex = -1
        for (i in lines.indices) {
            if (isHeaderLine(lines[i])) {
                headerIndex = i
                break
            }
        }

        val delimiter = if (headerIndex != -1) detectDelimiter(lines[headerIndex]) else detectDelimiter(lines.first())

        val detectedColumns = if (headerIndex != -1) {
            detectColumns(lines[headerIndex], delimiter)
        } else {
            detectColumns(lines.first(), delimiter)
        }

        val dataLines = if (headerIndex != -1) {
            lines.drop(headerIndex + 1)
        } else {
            lines
        }

        val parsedRows = mutableListOf<ParsedImportRow>()
        var duplicates = 0

        for (line in dataLines) {
            val row = parseLine(line, delimiter, detectedColumns, rules, existingTransactions)
            if (row != null && row.description.isNotBlank()) {
                if (row.isDuplicate) duplicates++
                parsedRows.add(row)
            }
        }

        return ImportPreviewResult(
            totalRows = parsedRows.size,
            validRows = parsedRows,
            duplicateCount = duplicates,
            detectedColumns = detectedColumns,
            fileName = fileName
        )
    }

    private fun detectDelimiter(header: String): String {
        return when {
            header.contains("\t") -> "\t"
            header.contains("|") -> "|"
            header.contains(";") -> ";"
            header.contains(",") -> ","
            else -> ""
        }
    }

    private fun detectColumns(header: String, delimiter: String): List<String> {
        val cols = if (delimiter.isNotEmpty()) {
            header.split(delimiter).map { sanitizeText(it) }
        } else {
            header.split("\\s{2,}".toRegex()).map { sanitizeText(it) }.filter { it.isNotBlank() }
        }
        if (cols.size > 1) return cols
        return listOf("Date", "Description", "Debit", "Credit", "Balance", "Category")
    }

    private fun isHeaderLine(line: String): Boolean {
        val lower = line.lowercase()
        val hasDate = lower.contains("date") || lower.contains("txn")
        val hasDesc = lower.contains("description") || lower.contains("particular") || lower.contains("narration")
        val hasAmount = lower.contains("debit") || lower.contains("credit") || lower.contains("amount") || lower.contains("balance")
        return (hasDate && hasDesc) || (hasDate && hasAmount) || (hasDesc && hasAmount)
    }

    private fun parseLine(
        line: String,
        delimiter: String,
        detectedCols: List<String>,
        rules: List<CategorizationRuleEntity>,
        existingTxs: List<TransactionEntity>
    ): ParsedImportRow? {
        val tokens = if (delimiter.isNotEmpty()) {
            line.split(delimiter).map { sanitizeText(it.trim().trim('"')) }
        } else {
            line.split("\\s{2,}".toRegex()).map { sanitizeText(it) }.filter { it.isNotBlank() }
        }

        if (tokens.size < 2) return null

        var date = ""
        var description = ""
        var debit = 0.0
        var credit = 0.0
        var balance: Double? = null
        var ref = ""
        var explicitCategory: String? = null

        // Find indices of columns case-insensitively
        var dateIdx = -1
        var descIdx = -1
        var debitIdx = -1
        var creditIdx = -1
        var balanceIdx = -1
        var categoryIdx = -1

        for (i in detectedCols.indices) {
            val col = detectedCols[i].lowercase()
            when {
                col.contains("date") -> if (dateIdx == -1) dateIdx = i
                col.contains("desc") || col.contains("particular") || col.contains("narration") -> if (descIdx == -1) descIdx = i
                col.contains("debit") || col.contains("withdrawal") || col.contains("payment") || col.contains("dr") -> if (debitIdx == -1) debitIdx = i
                col.contains("credit") || col.contains("deposit") || col.contains("received") || col.contains("cr") -> if (creditIdx == -1) creditIdx = i
                col.contains("balance") || col.contains("bal") -> if (balanceIdx == -1) balanceIdx = i
                col.contains("category") || col.contains("cat") -> if (categoryIdx == -1) categoryIdx = i
            }
        }

        // Fallbacks
        if (dateIdx == -1) dateIdx = 0
        if (descIdx == -1) descIdx = 1
        if (debitIdx == -1) debitIdx = 2
        if (creditIdx == -1) creditIdx = 3
        if (balanceIdx == -1) balanceIdx = 4

        // Extract values safely
        date = if (dateIdx in tokens.indices) normalizeDate(tokens[dateIdx]) else ""
        description = if (descIdx in tokens.indices) sanitizeText(tokens[descIdx]) else ""
        debit = if (debitIdx in tokens.indices) parseAmount(tokens[debitIdx]) else 0.0
        credit = if (creditIdx in tokens.indices) parseAmount(tokens[creditIdx]) else 0.0
        balance = if (balanceIdx in tokens.indices) parseAmountOrNull(tokens[balanceIdx]) else null
        if (categoryIdx != -1 && categoryIdx in tokens.indices) {
            explicitCategory = tokens[categoryIdx].takeIf { it.isNotBlank() }
        }

        if (date.isBlank()) date = "2026-01-01"
        if (description.isBlank() || description.length < 2) description = "Store Transaction"

        val isCredit = credit > 0.0
        val amount = if (debit > 0.0) debit else credit

        val catResult = if (explicitCategory != null && explicitCategory != "Uncategorized") {
            CategorizationResult(
                categoryId = null,
                categoryName = explicitCategory,
                transactionType = if (isCredit) TransactionType.INCOME else TransactionType.EXPENSE,
                confidence = 1.0f,
                matchedRule = null
            )
        } else {
            CategorizationEngine.categorize(description, rules, isCredit)
        }

        val isDuplicate = existingTxs.any {
            it.transactionDate == date &&
                    it.description.equals(description, ignoreCase = true) &&
                    Math.abs(it.debitAmount - debit) < 0.01 &&
                    Math.abs(it.creditAmount - credit) < 0.01
        }

        return ParsedImportRow(
            rawLine = line,
            date = date,
            description = description,
            debitAmount = debit,
            creditAmount = credit,
            amount = amount,
            balance = balance,
            referenceNumber = ref,
            suggestedCategory = catResult.categoryName,
            suggestedCategoryId = catResult.categoryId,
            suggestedType = catResult.transactionType,
            confidence = catResult.confidence,
            isDuplicate = isDuplicate
        )
    }

    private fun parseAmount(str: String): Double {
        return parseAmountOrNull(str) ?: 0.0
    }

    private fun parseAmountOrNull(str: String): Double? {
        val clean = str.replace("₹", "").replace(",", "").replace("CR", "", true).replace("DR", "", true).trim()
        return clean.toDoubleOrNull()
    }

    private fun normalizeDate(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$"))) return trimmed
        
        // Handle Excel serial numeric date
        val doubleVal = trimmed.toDoubleOrNull()
        if (doubleVal != null && doubleVal > 10000.0 && doubleVal < 100000.0) {
            val days = doubleVal.toLong()
            val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            calendar.set(1899, Calendar.DECEMBER, 30, 0, 0, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            calendar.add(Calendar.DAY_OF_YEAR, days.toInt())
            val y = calendar.get(Calendar.YEAR)
            val m = calendar.get(Calendar.MONTH) + 1
            val d = calendar.get(Calendar.DAY_OF_MONTH)
            return String.format(Locale.ENGLISH, "%04d-%02d-%02d", y, m, d)
        }

        val dmyMatch = Regex("^(\\d{1,2})[/-](\\d{1,2})[/-](\\d{4})$").find(trimmed)
        if (dmyMatch != null) {
            val (d, m, y) = dmyMatch.destructured
            return String.format(Locale.ENGLISH, "%04d-%02d-%02d", y.toInt(), m.toInt(), d.toInt())
        }
        val dMmmYMatch = Regex("^(\\d{1,2})[ -]([A-Za-z]{3})[ -](\\d{4})$").find(trimmed)
        if (dMmmYMatch != null) {
            val (d, mStr, y) = dMmmYMatch.destructured
            val m = monthNameToNumber(mStr)
            return String.format(Locale.ENGLISH, "%04d-%02d-%02d", y.toInt(), m, d.toInt())
        }
        return trimmed
    }

    private fun monthNameToNumber(name: String): Int {
        return when (name.lowercase().take(3)) {
            "jan" -> 1
            "feb" -> 2
            "mar" -> 3
            "apr" -> 4
            "may" -> 5
            "jun" -> 6
            "jul" -> 7
            "aug" -> 8
            "sep" -> 9
            "oct" -> 10
            "nov" -> 11
            "dec" -> 12
            else -> 1
        }
    }
}
