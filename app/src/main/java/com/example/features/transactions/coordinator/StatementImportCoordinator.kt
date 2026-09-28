package com.example.features.transactions.coordinator

import android.content.Context
import android.net.Uri
import com.example.BuildConfig
import com.example.data.local.AppDatabase
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.CategoryType
import com.example.data.local.entity.TransactionEntity
import com.example.data.local.entity.TransactionType
import com.example.features.import.ImportPreviewResult
import com.example.features.import.StatementImportService
import com.example.features.transactions.data.TransactionRepository
import com.example.repository.FinanceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class StatementImportCoordinator(
    private val database: AppDatabase,
    private val repository: FinanceRepository,
    private val transactionRepository: TransactionRepository,
    private val scope: CoroutineScope,
    private val allTransactionsFlow: StateFlow<List<TransactionEntity>>,
    private val showMessage: (String) -> Unit,
    private val onImportSuccess: () -> Unit
) {
    private val _importPreview = MutableStateFlow<ImportPreviewResult?>(null)
    val importPreview: StateFlow<ImportPreviewResult?> = _importPreview.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    fun previewStatement(content: String, fileName: String = "bank_statement.csv") {
        scope.launch {
            val currentRules = repository.getActiveRulesList()
            val existingTxs = allTransactionsFlow.value
            val preview = StatementImportService.parseStatementText(content, currentRules, existingTxs, fileName)
            _importPreview.value = preview
        }
    }

    fun parseStatementUri(context: Context, uri: Uri, fileName: String = "statement.csv") {
        scope.launch(Dispatchers.IO) {
            try {
                _isImporting.value = true
                val maxFileSize = 25 * 1024 * 1024
                val bytes = context.contentResolver.openInputStream(uri)?.use { stream ->
                    val buffer = java.io.ByteArrayOutputStream()
                    val chunk = ByteArray(8192)
                    var totalRead = 0
                    var read: Int
                    while (stream.read(chunk).also { read = it } != -1) {
                        totalRead += read
                        if (totalRead > maxFileSize) {
                            throw IllegalArgumentException("File exceeds maximum allowed size of 25MB.")
                        }
                        buffer.write(chunk, 0, read)
                    }
                    buffer.toByteArray()
                }
                if (bytes == null || bytes.isEmpty()) {
                    showMessage("Could not read file. File appears to be empty.")
                    return@launch
                }
                val currentRules = repository.getActiveRulesList()
                val existingTxs = allTransactionsFlow.value
                val resolvedFileName = if (fileName.contains('.')) fileName else {
                    when {
                        bytes.size >= 4 && bytes[0] == 0x25.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x44.toByte() && bytes[3] == 0x46.toByte() -> "$fileName.pdf"
                        bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() && bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte() -> "$fileName.xlsx"
                        bytes.size >= 4 && bytes[0] == 0xD0.toByte() && bytes[1] == 0xCF.toByte() && bytes[2] == 0x11.toByte() && bytes[3] == 0xE0.toByte() -> "$fileName.xls"
                        else -> "$fileName.csv"
                    }
                }
                val preview = StatementImportService.parseStatementBytes(context, bytes, resolvedFileName, currentRules, existingTxs)
                if (preview.validRows.isEmpty()) {
                    showMessage("No valid transaction rows found in '$resolvedFileName'. Please ensure it's a supported bank statement.")
                }
                _importPreview.value = preview
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    android.util.Log.e("FinanceViewModel", "Statement import failed", e)
                }
                showMessage("Could not parse statement: ${e.localizedMessage ?: "Invalid or corrupted format"}")
            } finally {
                _isImporting.value = false
            }
        }
    }

    fun confirmImport(skipDuplicates: Boolean = false, targetAccountId: Long = 1) {
        val preview = _importPreview.value ?: return
        scope.launch {
            try {
                _isImporting.value = true
                val rowsToImport = if (skipDuplicates) preview.validRows.filterNot { it.isDuplicate } else preview.validRows

                val txEntities = rowsToImport.map { row ->
                    val containsCompany = row.description.contains("company", ignoreCase = true)
                    var finalCatId = row.suggestedCategoryId
                    var finalCatName = row.suggestedCategory
                    var finalType = row.suggestedType

                    if (containsCompany) {
                        val catName = "Official Expense"
                        val existingCat = database.categoryDao().getCategoryByName(catName)
                        val categoryId = if (existingCat != null) {
                            existingCat.id
                        } else {
                            database.categoryDao().insertCategory(
                                CategoryEntity(
                                    name = catName,
                                    type = CategoryType.EXPENSE,
                                    colorHex = "#06B6D4"
                                )
                            )
                        }
                        finalCatId = categoryId
                        finalCatName = catName
                        finalType = TransactionType.EXPENSE
                    }

                    TransactionEntity(
                        accountId = targetAccountId,
                        transactionDate = row.date,
                        description = row.description,
                        debitAmount = row.debitAmount,
                        creditAmount = row.creditAmount,
                        amount = row.amount,
                        transactionType = finalType,
                        balanceAfterTransaction = row.balance,
                        categoryId = finalCatId,
                        categoryName = finalCatName,
                        source = "IMPORT_EXCEL",
                        referenceNumber = row.referenceNumber,
                        isManual = false,
                        isCategorized = if (containsCompany) true else (row.confidence > 0f),
                        categorizationConfidence = if (containsCompany) 1.0f else row.confidence
                    )
                }

                repository.insertTransactions(txEntities)
                transactionRepository.recalculateAccountBalance(targetAccountId)
                _importPreview.value = null
                showMessage("${txEntities.size} transactions imported successfully!")
                onImportSuccess()
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    android.util.Log.e("FinanceViewModel", "Confirm import failed", e)
                }
                showMessage("Import failed: ${e.localizedMessage ?: "Database error"}")
            } finally {
                _isImporting.value = false
            }
        }
    }

    fun clearImportPreview() {
        _importPreview.value = null
    }
}
