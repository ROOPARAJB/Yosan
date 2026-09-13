package com.example.features.import

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.example.data.local.entity.*
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
        context: Context? = null,
        bytes: ByteArray,
        fileName: String,
        rules: List<CategorizationRuleEntity>,
        existingTransactions: List<TransactionEntity>
    ): ImportPreviewResult {
        val lowerName = fileName.lowercase()
        val isPdf = lowerName.endsWith(".pdf") || (bytes.size >= 4 && bytes[0] == 0x25.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x44.toByte() && bytes[3] == 0x46.toByte())
        val isXlsx = lowerName.endsWith(".xlsx") || (bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() && bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte())
        val isXls = lowerName.endsWith(".xls") || (bytes.size >= 4 && bytes[0] == 0xD0.toByte() && bytes[1] == 0xCF.toByte() && bytes[2] == 0x11.toByte() && bytes[3] == 0xE0.toByte())

        val textContent = when {
            isXlsx || isXls -> extractTextFromXlsxBytes(bytes)
            isPdf -> extractTextFromPdfBytes(context, bytes)
            else -> String(bytes, Charsets.UTF_8)
        }
        val resolvedName = when {
            isPdf && !lowerName.endsWith(".pdf") -> "$fileName.pdf"
            isXlsx && !lowerName.endsWith(".xlsx") -> "$fileName.xlsx"
            isXls && !lowerName.endsWith(".xls") -> "$fileName.xls"
            else -> fileName
        }
        return parseStatementText(textContent, rules, existingTransactions, resolvedName)
    }

    private fun extractTextFromXlsxBytes(bytes: ByteArray): String {
        val sharedStrings = mutableListOf<String>()
        val sheetRows = mutableListOf<String>()
        val bytesMap = mutableMapOf<String, ByteArray>()

        val maxTotalBytes = 50 * 1024 * 1024 // 50MB decompression limit against zip bombs
        var totalBytesRead = 0
        var entryCount = 0
        val maxEntries = 200

        try {
            val zip = ZipInputStream(ByteArrayInputStream(bytes))
            var entry = zip.nextEntry
            while (entry != null) {
                entryCount++
                if (entryCount > maxEntries) {
                    break
                }
                // Prevent Zip Path Traversal (CWE-22)
                val safeName = entry.name.replace("\\", "/")
                if (safeName.contains("..")) {
                    entry = zip.nextEntry
                    continue
                }
                if (safeName == "xl/sharedStrings.xml" || safeName.startsWith("xl/worksheets/sheet")) {
                    val buffer = java.io.ByteArrayOutputStream()
                    val chunk = ByteArray(4096)
                    var read: Int
                    while (zip.read(chunk).also { read = it } != -1) {
                        totalBytesRead += read
                        if (totalBytesRead > maxTotalBytes) {
                            throw IllegalStateException("Decompression limit exceeded (possible zip bomb)")
                        }
                        buffer.write(chunk, 0, read)
                    }
                    bytesMap[safeName] = buffer.toByteArray()
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
            if (com.example.BuildConfig.DEBUG) {
                android.util.Log.e("StatementImportService", "Failed to parse XLSX", e)
            }
        }

        return if (sheetRows.isNotEmpty()) sheetRows.joinToString("\n") else String(bytes, Charsets.UTF_8)
    }

    private fun extractTextFromPdfBytes(context: Context?, bytes: ByteArray): String {
        // 1. Try high-fidelity PDFBox extraction
        try {
            if (context != null) {
                try {
                    if (!PDFBoxResourceLoader.isReady()) {
                        PDFBoxResourceLoader.init(context.applicationContext)
                    }
                } catch (_: Throwable) {}
            }
            PDDocument.load(ByteArrayInputStream(bytes)).use { document ->
                val stripper = PDFTextStripper()
                stripper.sortByPosition = true
                val text = stripper.getText(document)
                if (text.isNotBlank()) {
                    return text
                }
            }
        } catch (e: Throwable) {
            if (com.example.BuildConfig.DEBUG) {
                android.util.Log.e("StatementImportService", "Failed to parse PDF with PDFBox", e)
            }
        }

        // 2. Fallback: Parse decompressed PDF streams and raw tokens
        val extractedLines = mutableListOf<String>()
        try {
            val rawIso = String(bytes, Charsets.ISO_8859_1)
            val streamRegex = Regex("stream\\r?\\n(.*?)\\r?\\nendstream", RegexOption.DOT_MATCHES_ALL)
            val decompressedChunks = mutableListOf<String>()

            for (match in streamRegex.findAll(rawIso)) {
                val streamContent = match.groupValues[1]
                val streamBytes = streamContent.toByteArray(Charsets.ISO_8859_1)
                try {
                    val inflater = java.util.zip.Inflater(false)
                    val out = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(4096)
                    inflater.setInput(streamBytes)
                    while (!inflater.finished()) {
                        val count = inflater.inflate(buffer)
                        if (count <= 0) break
                        out.write(buffer, 0, count)
                    }
                    inflater.end()
                    val decompressed = String(out.toByteArray(), Charsets.UTF_8)
                    if (decompressed.isNotBlank()) {
                        decompressedChunks.add(decompressed)
                    }
                } catch (_: Throwable) {
                    try {
                        val inflaterNowrap = java.util.zip.Inflater(true)
                        val out = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(4096)
                        inflaterNowrap.setInput(streamBytes)
                        while (!inflaterNowrap.finished()) {
                            val count = inflaterNowrap.inflate(buffer)
                            if (count <= 0) break
                            out.write(buffer, 0, count)
                        }
                        inflaterNowrap.end()
                        val decompressed = String(out.toByteArray(), Charsets.UTF_8)
                        if (decompressed.isNotBlank()) {
                            decompressedChunks.add(decompressed)
                        }
                    } catch (_: Throwable) {}
                }
            }

            val sources = if (decompressedChunks.isNotEmpty()) decompressedChunks else listOf(rawIso)
            val textRegex = Regex("\\(([^()\\\\]*(?:\\\\.[^()\\\\]*)*)\\)")

            for (source in sources) {
                var currentLine = StringBuilder()
                textRegex.findAll(source).forEach { match ->
                    val rawToken = match.groupValues[1]
                        .replace("\\(", "(")
                        .replace("\\)", ")")
                        .replace("\\n", " ")
                        .replace("\\r", "")
                        .replace("\\t", " ")
                        .trim()

                    val cleanToken = sanitizeText(rawToken)

                    if (cleanToken.length >= 2 && cleanToken.any { it.isLetterOrDigit() }) {
                        if (currentLine.isNotEmpty()) currentLine.append(" ")
                        currentLine.append(cleanToken)

                        val lineStr = currentLine.toString()
                        if (lineStr.contains(Regex("\\d{1,4}[/-]\\d{1,2}[/-]\\d{1,4}"))) {
                            extractedLines.add(lineStr)
                            currentLine.clear()
                        }
                    }
                }
                if (currentLine.isNotEmpty()) extractedLines.add(currentLine.toString())
            }

            if (extractedLines.size < 2) {
                val lineRegex = Regex("[^\r\n]{10,}")
                lineRegex.findAll(rawIso).forEach { m ->
                    val line = sanitizeText(m.value)
                    if (line.contains(Regex("\\d{1,4}[/-]\\d{1,2}[/-]\\d{1,4}"))) {
                        if (line.length > 5) extractedLines.add(line)
                    }
                }
            }
        } catch (e: Throwable) {
            if (com.example.BuildConfig.DEBUG) {
                android.util.Log.e("StatementImportService", "Fallback PDF text extraction error", e)
            }
        }

        return if (extractedLines.isNotEmpty()) extractedLines.joinToString("\n") else String(bytes, Charsets.UTF_8)
    }

    private fun sanitizeText(input: String): String {
        if (input.isBlank()) return ""
        val cleaned = input.replace("[^\\x20-\\x7E]".toRegex(), " ").trim()
        val collapsed = cleaned.replace("\\s+".toRegex(), " ")
        return if (collapsed.any { it.isLetterOrDigit() }) collapsed else ""
    }

    fun parseStatementText(
        content: String,
        rules: List<CategorizationRuleEntity>,
        existingTransactions: List<TransactionEntity>,
        fileName: String = "bank_statement.csv"
    ): ImportPreviewResult {
        val rawLines = content.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (rawLines.isEmpty()) {
            return ImportPreviewResult(0, emptyList(), 0, emptyList(), fileName)
        }

        // Find the first line that matches the header criteria
        var headerIndex = -1
        for (i in rawLines.indices) {
            if (isHeaderLine(rawLines[i])) {
                headerIndex = i
                break
            }
        }

        val delimiter = if (headerIndex != -1) detectDelimiter(rawLines[headerIndex]) else detectDelimiter(rawLines.first())

        val detectedColumns = if (headerIndex != -1) {
            detectColumns(rawLines[headerIndex], delimiter)
        } else {
            detectColumns(rawLines.first(), delimiter)
        }

        val dataLines = if (headerIndex != -1) {
            rawLines.drop(headerIndex + 1)
        } else {
            rawLines
        }

        // Group multiline / wrapped transaction rows (e.g. from PDF table extract)
        val consolidatedRows = mutableListOf<String>()
        var currentBlock = StringBuilder()

        for (line in dataLines) {
            if (isHeaderLine(line) || isIgnoredOrTotalLine(line)) {
                if (currentBlock.isNotEmpty()) {
                    consolidatedRows.add(currentBlock.toString())
                    currentBlock.clear()
                }
                continue
            }

            val startsWithDate = line.matches(Regex("^\\s*\\d{1,2}[/-](?:[A-Za-z]{3,9}|\\d{1,2})[/-]\\d{2,4}.*", RegexOption.DOT_MATCHES_ALL)) ||
                    line.matches(Regex("^\\s*\\d{4}[/-]\\d{1,2}[/-]\\d{1,2}.*", RegexOption.DOT_MATCHES_ALL)) ||
                    line.matches(Regex("^\\s*[A-Za-z]{3,9}[ -]\\d{1,2},?[ -]\\d{4}.*", RegexOption.DOT_MATCHES_ALL))

            if (startsWithDate) {
                if (currentBlock.isNotEmpty()) {
                    consolidatedRows.add(currentBlock.toString())
                    currentBlock.clear()
                }
                currentBlock.append(line)
            } else if (currentBlock.isNotEmpty()) {
                // Continuation line (wrapped description or amount on next line)
                currentBlock.append(" ").append(line)
            } else {
                if (line.contains(Regex("\\d{1,2}[/-](?:[A-Za-z]{3,9}|\\d{1,2})[/-]\\d{2,4}"))) {
                    consolidatedRows.add(line)
                }
            }
        }
        if (currentBlock.isNotEmpty()) {
            consolidatedRows.add(currentBlock.toString())
        }

        val parsedRows = mutableListOf<ParsedImportRow>()
        for (line in consolidatedRows) {
            val row = parseLine(line, delimiter, detectedColumns, rules, existingTransactions)
            if (row != null && row.description.isNotBlank() && row.amount > 0.0) {
                parsedRows.add(row)
            }
        }

        // 2-Pass Balance Delta Mathematical Verification:
        // Extract initial opening balance if present in statement text
        var runningBal: Double? = null
        val openingBalMatch = Regex("Opening\\s*Balance\\s*[:\\-]?\\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]{2})|[0-9]+\\.[0-9]{2})", RegexOption.IGNORE_CASE).find(content)
        if (openingBalMatch != null) {
            runningBal = parseAmount(openingBalMatch.groupValues[1])
        }

        val finalizedRows = mutableListOf<ParsedImportRow>()
        var duplicates = 0

        for (row in parsedRows) {
            var finalDebit = row.debitAmount
            var finalCredit = row.creditAmount
            var finalType = row.suggestedType

            if (row.balance != null && runningBal != null) {
                val diff = row.balance - runningBal
                if (diff > 0.009) {
                    // Balance increased -> Definitive Income / Credit!
                    finalCredit = if (finalCredit > 0.0) finalCredit else diff
                    finalDebit = 0.0
                    finalType = TransactionType.INCOME
                } else if (diff < -0.009) {
                    // Balance decreased -> Definitive Expense / Debit!
                    finalDebit = if (finalDebit > 0.0) finalDebit else Math.abs(diff)
                    finalCredit = 0.0
                    finalType = TransactionType.EXPENSE
                }
            }
            if (row.balance != null) {
                runningBal = row.balance
            }

            val finalAmount = if (finalDebit > 0.0) finalDebit else finalCredit
            val isDup = existingTransactions.any {
                it.transactionDate == row.date &&
                        it.description.equals(row.description, ignoreCase = true) &&
                        Math.abs(it.debitAmount - finalDebit) < 0.01 &&
                        Math.abs(it.creditAmount - finalCredit) < 0.01
            }
            if (isDup) duplicates++

            finalizedRows.add(row.copy(
                debitAmount = finalDebit,
                creditAmount = finalCredit,
                amount = finalAmount,
                suggestedType = finalType,
                isDuplicate = isDup
            ))
        }

        return ImportPreviewResult(
            totalRows = finalizedRows.size,
            validRows = finalizedRows,
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
        return listOf("Transaction Date", "Value Date", "Reference No", "Description", "Withdrawals", "Deposits", "Running Balance")
    }

    private fun isHeaderLine(line: String): Boolean {
        val lower = line.lowercase()
        // Never match account summary/metadata lines
        if (lower.contains("account opening") || lower.contains("primary holder") || lower.contains("statement of account") || lower.contains("statement period") || lower.contains("nominee details") || lower.contains("branch details") || lower.contains("joint holder")) {
            return false
        }
        val hasDate = lower.contains("date") || lower.contains("txn dt") || lower.contains("value dt")
        val hasDesc = lower.contains("description") || lower.contains("particular") || lower.contains("narration") || lower.contains("remark") || lower.contains("transaction details")
        val hasAmount = lower.contains("debit") || lower.contains("credit") || lower.contains("withdrawal") || lower.contains("deposit") || lower.contains("paid out") || lower.contains("paid in") || lower.contains("dr") || lower.contains("cr")
        val hasBalance = lower.contains("balance") || lower.contains("running") || lower.contains("closing")
        return (hasDate && hasDesc && (hasAmount || hasBalance)) || (hasDesc && hasAmount && hasBalance)
    }

    private fun isIgnoredOrTotalLine(line: String, description: String = ""): Boolean {
        val lowerLine = line.lowercase().trim()
        val lowerDesc = description.lowercase().trim()

        val ignoredKeywords = listOf(
            "opening balance", "closing balance", "total withdrawals", "total deposits",
            "total debit", "total credit", "total debits", "total credits", "total amount",
            "total value", "total sum", "grand total", "sub total", "subtotal", "running total",
            "brought forward", "carried forward", "b/f", "c/f", "balance b/f", "balance c/f",
            "statement of account", "statement period", "transaction details for", "statement summary",
            "account status", "account variant", "smart salary", "primary holder", "nominee details",
            "joint holder", "od limit", "uncleared amount", "sweep in", "mandatory disclaimer",
            "transaction codes in your account", "page 1 of", "page 2 of", "page 3 of", "page of",
            "reward points", "to redeem your rewardz", "yes touch", "phonebanking number",
            "cin -", "branch details", "ifsc code", "micr code", "customer id", "cust id",
            "registered email", "mobile no", "end of statement", "computer generated",
            "authorized signatory", "disclaimer", "total inflow", "total outflow", "net balance",
            "summary for the period", "account statement", "e-statement"
        )

        for (kw in ignoredKeywords) {
            if (lowerLine.contains(kw) || lowerDesc.contains(kw)) {
                return true
            }
        }

        // Header / meta description matching
        if (lowerDesc in listOf("description", "particulars", "narration", "remarks", "details", "transaction details", "txn details", "date", "amount", "total", "totals")) {
            return true
        }

        if (lowerDesc.matches(Regex("^total\\s*[:\\-]?.*$", RegexOption.IGNORE_CASE)) && !lowerDesc.contains("total energies") && !lowerDesc.contains("total gas")) {
            return true
        }

        return false
    }

    private fun parseLine(
        line: String,
        delimiter: String,
        detectedCols: List<String>,
        rules: List<CategorizationRuleEntity>,
        existingTxs: List<TransactionEntity>
    ): ParsedImportRow? {
        if (isHeaderLine(line) || isIgnoredOrTotalLine(line)) {
            return null
        }

        val tokens = if (delimiter.isNotEmpty()) {
            line.split(delimiter).map { sanitizeText(it.trim().trim('"')) }
        } else {
            line.split("\\s{2,}".toRegex()).map { sanitizeText(it) }.filter { it.isNotBlank() }
        }

        if (tokens.isEmpty()) return null

        var date = ""
        var description = ""
        var debit = 0.0
        var credit = 0.0
        var balance: Double? = null
        var ref = ""
        var explicitCategory: String? = null

        if (delimiter.isNotEmpty() && tokens.size >= 2) {
            var dateIdx = -1
            var descIdx = -1
            var debitIdx = -1
            var creditIdx = -1
            var balanceIdx = -1
            var refIdx = -1
            var categoryIdx = -1

            for (i in detectedCols.indices) {
                val col = detectedCols[i].lowercase().trim()
                when {
                    (col.contains("txn") && col.contains("date")) || (col.contains("trans") && col.contains("date")) || (col == "date") -> dateIdx = i
                    dateIdx == -1 && (col.contains("date") || col.contains("txn dt")) && !col.contains("value") -> dateIdx = i
                    descIdx == -1 && (col.contains("desc") || col.contains("particular") || col.contains("narration") || col.contains("remark") || col.contains("detail")) -> descIdx = i
                    debitIdx == -1 && (col.contains("withdrawal") || col.contains("debit") || col.contains("paid out") || col.contains("dr")) && !col.contains("credit") -> debitIdx = i
                    creditIdx == -1 && (col.contains("deposit") || col.contains("credit") || col.contains("paid in") || col.contains("cr")) && !col.contains("debit") -> creditIdx = i
                    balanceIdx == -1 && (col.contains("balance") || col.contains("closing") || col.contains("bal") || col.contains("running")) -> balanceIdx = i
                    refIdx == -1 && (col.contains("ref") || col.contains("utr") || col.contains("chq") || col.contains("cheque") || col.contains("trans id") || col.contains("txn id") || col.contains("cheque no")) -> refIdx = i
                    categoryIdx == -1 && (col.contains("category") || col.contains("cat")) -> categoryIdx = i
                }
            }

            if (dateIdx == -1) dateIdx = 0
            if (descIdx == -1) descIdx = if (tokens.size >= 4) 3 else 1
            if (debitIdx == -1 && tokens.size >= 5) debitIdx = 4
            if (creditIdx == -1 && tokens.size >= 6) creditIdx = 5
            if (balanceIdx == -1 && tokens.size >= 7) balanceIdx = 6

            date = if (dateIdx in tokens.indices) normalizeDate(tokens[dateIdx]) else ""
            description = if (descIdx in tokens.indices) sanitizeText(tokens[descIdx]) else ""
            debit = if (debitIdx in tokens.indices) parseAmount(tokens[debitIdx]) else 0.0
            credit = if (creditIdx in tokens.indices) parseAmount(tokens[creditIdx]) else 0.0
            balance = if (balanceIdx in tokens.indices) parseAmountOrNull(tokens[balanceIdx]) else null
            if (refIdx != -1 && refIdx in tokens.indices) ref = sanitizeText(tokens[refIdx])
            if (categoryIdx != -1 && categoryIdx in tokens.indices) explicitCategory = tokens[categoryIdx].takeIf { it.isNotBlank() }
        }

        // Positional fallback for space-separated lines (e.g. Yes Bank, SBI, HDFC statements)
        if (!isValidNormalizedDate(date) || (debit == 0.0 && credit == 0.0)) {
            val dateRegex = Regex("(\\d{1,2}[/-](?:[A-Za-z]{3,9}|\\d{1,2})[/-]\\d{2,4}|\\d{4}[/-]\\d{1,2}[/-]\\d{1,2})")
            val dateMatch = dateRegex.find(line)
            if (dateMatch != null) {
                date = normalizeDate(dateMatch.value)
            }

            val numberRegex = Regex("([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]{2})|[0-9]+\\.[0-9]{2})")
            val allNumberMatches = numberRegex.findAll(line).map { it.value }.toList()

            if (allNumberMatches.isNotEmpty()) {
                val hasExplicitCredit = line.contains("NEFT Cr-", ignoreCase = true) ||
                        line.contains("IMPS Cr-", ignoreCase = true) ||
                        line.contains("RTGS Cr-", ignoreCase = true) ||
                        line.contains("UPI Cr-", ignoreCase = true) ||
                        line.contains("Cr/", ignoreCase = true) ||
                        line.contains(" Cr-", ignoreCase = true) ||
                        line.contains("Cr.", ignoreCase = true) ||
                        line.contains("Refund", ignoreCase = true) ||
                        line.contains("Reversal", ignoreCase = true) ||
                        line.contains("Salary", ignoreCase = true) ||
                        line.contains("Deposit", ignoreCase = true) ||
                        line.contains("Interest Paid", ignoreCase = true) ||
                        Regex("\\b(?:Cr|Credit|Credited|Deposits?)\\b", RegexOption.IGNORE_CASE).containsMatchIn(line)

                val hasExplicitDebit = line.contains("NEFT Dr-", ignoreCase = true) ||
                        line.contains("IMPS Dr-", ignoreCase = true) ||
                        line.contains("RTGS Dr-", ignoreCase = true) ||
                        line.contains("UPI Dr-", ignoreCase = true) ||
                        line.contains("Dr/", ignoreCase = true) ||
                        line.contains(" Dr-", ignoreCase = true) ||
                        line.contains("Dr.", ignoreCase = true) ||
                        line.contains("Withdrawal", ignoreCase = true) ||
                        line.contains("Debit", ignoreCase = true) ||
                        line.contains("Debited", ignoreCase = true) ||
                        line.contains("Paid to", ignoreCase = true) ||
                        line.contains("Payment to", ignoreCase = true) ||
                        line.contains("OUT", ignoreCase = false) ||
                        Regex("\\b(?:Dr|Debit|Debited|Withdrawals?)\\b", RegexOption.IGNORE_CASE).containsMatchIn(line)

                if (allNumberMatches.size >= 2) {
                    val lastNum = parseAmount(allNumberMatches.last())
                    val secondLastNum = parseAmount(allNumberMatches[allNumberMatches.size - 2])
                    balance = lastNum

                    if (hasExplicitCredit && !hasExplicitDebit) {
                        credit = secondLastNum
                        debit = 0.0
                    } else if (hasExplicitDebit && !hasExplicitCredit) {
                        debit = secondLastNum
                        credit = 0.0
                    } else if (hasExplicitCredit) {
                        credit = secondLastNum
                        debit = 0.0
                    } else {
                        debit = secondLastNum
                        credit = 0.0
                    }
                } else {
                    val singleNum = parseAmount(allNumberMatches.last())
                    if (hasExplicitCredit) {
                        credit = singleNum
                        debit = 0.0
                    } else {
                        debit = singleNum
                        credit = 0.0
                    }
                }
            }

            val refRegex = Regex("\\b([A-Z0-9]{10,}(?:OUT)?)\\b")
            val refMatch = refRegex.findAll(line).firstOrNull { match ->
                val v = match.value
                !v.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) && !v.matches(Regex("[0-9.,]+"))
            }
            if (refMatch != null) {
                ref = refMatch.value
            }

            if (description.isBlank()) {
                var cleanDesc = line
                if (dateMatch != null) {
                    cleanDesc = cleanDesc.replace(dateMatch.value, "")
                }
                val secondDate = dateRegex.find(cleanDesc)
                if (secondDate != null) {
                    cleanDesc = cleanDesc.replace(secondDate.value, "")
                }
                if (ref.isNotBlank()) {
                    cleanDesc = cleanDesc.replace(ref, "")
                }
                for (numStr in allNumberMatches.takeLast(2)) {
                    cleanDesc = cleanDesc.replace(numStr, "")
                }
                description = sanitizeText(cleanDesc)
            }
        }

        if (!isValidNormalizedDate(date)) {
            return null
        }

        if (description.isBlank() || isIgnoredOrTotalLine(line, description)) {
            return null
        }

        val isCredit = credit > 0.0
        val amount = if (debit > 0.0) debit else credit

        if (amount <= 0.0) {
            return null
        }

        if (ref.isBlank()) {
            val upiMatch = Regex("(?:UPI|IMPS|NEFT|RTGS|TRANSFER)[/-]([A-Za-z0-9]+)").find(description)
            if (upiMatch != null) {
                ref = upiMatch.groupValues[1]
            }
        }

        val finalCategory = explicitCategory ?: "Uncategorized"
        val finalType = if (isCredit) TransactionType.INCOME else TransactionType.EXPENSE

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
            suggestedCategory = finalCategory,
            suggestedCategoryId = null,
            suggestedType = finalType,
            confidence = 0f,
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

    private fun isValidNormalizedDate(dateStr: String): Boolean {
        if (!dateStr.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$"))) return false
        val parts = dateStr.split("-")
        if (parts.size != 3) return false
        val y = parts[0].toIntOrNull() ?: return false
        val m = parts[1].toIntOrNull() ?: return false
        val d = parts[2].toIntOrNull() ?: return false
        return y in 1990..2099 && m in 1..12 && d in 1..31
    }

    private fun normalizeDate(raw: String): String {
        val trimmed = raw.trim()
        if (isValidNormalizedDate(trimmed)) return trimmed

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
            val formatted = String.format(Locale.ENGLISH, "%04d-%02d-%02d", y, m, d)
            if (isValidNormalizedDate(formatted)) return formatted
        }

        // Formats: DD/MM/YYYY or DD-MM-YYYY
        val dmyMatch = Regex("^(\\d{1,2})[/-](\\d{1,2})[/-](\\d{4})$").find(trimmed)
        if (dmyMatch != null) {
            val (d, m, y) = dmyMatch.destructured
            val formatted = String.format(Locale.ENGLISH, "%04d-%02d-%02d", y.toInt(), m.toInt(), d.toInt())
            if (isValidNormalizedDate(formatted)) return formatted
        }

        // Formats: YYYY/MM/DD or YYYY-MM-DD
        val ymdMatch = Regex("^(\\d{4})[/-](\\d{1,2})[/-](\\d{1,2})$").find(trimmed)
        if (ymdMatch != null) {
            val (y, m, d) = ymdMatch.destructured
            val formatted = String.format(Locale.ENGLISH, "%04d-%02d-%02d", y.toInt(), m.toInt(), d.toInt())
            if (isValidNormalizedDate(formatted)) return formatted
        }

        // Formats: DD-MMM-YYYY (e.g. 01-Aug-2026 or 01 Aug 2026)
        val dMmmYMatch = Regex("^(\\d{1,2})[ -]([A-Za-z]{3,9})[ -](\\d{4})$").find(trimmed)
        if (dMmmYMatch != null) {
            val (d, mStr, y) = dMmmYMatch.destructured
            val m = monthNameToNumber(mStr)
            val formatted = String.format(Locale.ENGLISH, "%04d-%02d-%02d", y.toInt(), m, d.toInt())
            if (isValidNormalizedDate(formatted)) return formatted
        }

        // Formats: MMM-DD-YYYY or MMM DD YYYY (e.g. Aug 01, 2026)
        val mmmDYMatch = Regex("^([A-Za-z]{3,9})[ -](\\d{1,2}),?[ -](\\d{4})$").find(trimmed)
        if (mmmDYMatch != null) {
            val (mStr, d, y) = mmmDYMatch.destructured
            val m = monthNameToNumber(mStr)
            val formatted = String.format(Locale.ENGLISH, "%04d-%02d-%02d", y.toInt(), m, d.toInt())
            if (isValidNormalizedDate(formatted)) return formatted
        }

        return ""
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
