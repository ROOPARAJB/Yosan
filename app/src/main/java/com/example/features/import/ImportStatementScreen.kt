package com.example.features.import

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.example.ui.components.CategoryChip
import com.example.ui.theme.*
import com.example.features.transactions.FinanceViewModel
import com.example.utils.CurrencyFormatter
import com.example.utils.DateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportStatementScreen(
    viewModel: FinanceViewModel,
    onImportSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val previewResult by viewModel.importPreview.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    var customText by remember { mutableStateOf("") }
    var skipDuplicates by remember { mutableStateOf(true) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var selectedAccountId by remember { mutableStateOf<Long?>(null) }

    // Auto-detect account from file name when file is selected
    LaunchedEffect(selectedFileName, accounts) {
        if (selectedFileName != null && accounts.isNotEmpty()) {
            val matched = accounts.firstOrNull { acc ->
                selectedFileName!!.contains(acc.bankName, ignoreCase = true) ||
                selectedFileName!!.contains(acc.accountName, ignoreCase = true)
            }
            if (matched != null) {
                selectedAccountId = matched.id
            } else if (selectedAccountId == null) {
                selectedAccountId = accounts.first().id
            }
        } else if (selectedAccountId == null && accounts.isNotEmpty()) {
            selectedAccountId = accounts.first().id
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val resolved = resolveStatementFileInfo(context, it)
            if (resolved.isSupported) {
                selectedFileName = resolved.fileName
                viewModel.parseStatementUri(context, it, resolved.fileName)
            } else {
                viewModel.showMessage("Invalid file format. Please select an Excel (.xlsx, .xls) or PDF (.pdf) statement.")
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("import_statement_screen"),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // 1. Header
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "Upload bank statements in Excel (.xlsx, .xls) or PDF (.pdf) formats with automatic categorization.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 2. Upload Document Card (PDF, Excel, CSV)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.FileUpload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Upload Bank Statement (Excel / PDF)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Select an Excel spreadsheet (.xlsx, .xls) or PDF statement from your device storage.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (selectedFileName != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = selectedFileName!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { filePickerLauncher.launch(SUPPORTED_STATEMENT_MIMES) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Select Statement File (.xlsx, .xls, .pdf)")
                    }
                }
            }
        }

        // 3. Custom Paste Input Area
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Or Paste Statement Text",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customText,
                        onValueChange = { customText = it },
                        placeholder = { Text("Txn Date,Description,Debit,Credit,Balance\n2026-02-01,STORE PURCHASE,340,0,45000") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.previewStatement(customText, "pasted_statement.csv") },
                        enabled = customText.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Parse & Preview Text")
                    }
                }
            }
        }

        // 4. Preview & Validation Section
        if (previewResult != null) {
            val preview = previewResult!!
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Validation Summary • ${preview.fileName}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "Total Rows: ${preview.totalRows}")
                                Text(text = "Valid: ${preview.validRows.size}", color = IncomeGreen, fontWeight = FontWeight.Bold)
                                Text(text = "Duplicates: ${preview.duplicateCount}", color = if (preview.duplicateCount > 0) OutstandingAmber else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    // Bank Account Selector Card
                    if (accounts.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Assign Statement to Bank Account",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    accounts.forEach { acc ->
                                        val isSelected = (selectedAccountId ?: accounts.first().id) == acc.id
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { selectedAccountId = acc.id },
                                            leadingIcon = {
                                                if (isSelected) {
                                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                                }
                                            },
                                            label = { Text("${acc.bankName} (${acc.accountName})") }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = skipDuplicates,
                            onCheckedChange = { skipDuplicates = it }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Skip duplicate transactions", style = MaterialTheme.typography.bodyMedium)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            val accountId = selectedAccountId ?: (accounts.firstOrNull()?.id ?: 1L)
                            viewModel.confirmImport(skipDuplicates = skipDuplicates, targetAccountId = accountId)
                            onImportSuccess()
                        },
                        enabled = !isImporting && preview.validRows.isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = IncomeGreen)
                    ) {
                        if (isImporting) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                        } else {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Confirm & Import ${preview.validRows.size} Transactions", fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Transactions Preview (First 20)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            items(preview.validRows.take(20)) { row ->
                ImportRowPreviewCard(row = row, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
        }
    }
}

data class ResolvedStatementFile(
    val fileName: String,
    val isSupported: Boolean
)

private val SUPPORTED_STATEMENT_MIMES = arrayOf(
    "application/pdf",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/vnd.ms-excel",
    "application/x-excel",
    "application/msexcel",
    "application/excel",
    "text/csv",
    "text/comma-separated-values",
    "text/plain",
    "application/octet-stream",
    "*/*"
)

private fun resolveStatementFileInfo(context: Context, uri: Uri): ResolvedStatementFile {
    var fileName = ""

    // 1. Try OpenableColumns.DISPLAY_NAME
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    fileName = cursor.getString(nameIndex) ?: ""
                }
            }
        }
    } catch (_: Exception) {}

    // 2. Fallback to URI lastPathSegment
    if (fileName.isBlank()) {
        val lastSegment = uri.lastPathSegment
        if (!lastSegment.isNullOrBlank()) {
            fileName = lastSegment.substringAfterLast('/').substringAfterLast(':')
        }
    }
    if (fileName.isBlank()) {
        fileName = "statement"
    }

    val lower = fileName.lowercase()
    val hasValidExt = lower.endsWith(".xlsx") || lower.endsWith(".xls") || lower.endsWith(".pdf") || lower.endsWith(".csv")
    if (hasValidExt) {
        return ResolvedStatementFile(fileName, true)
    }

    // 3. Inspect ContentResolver MIME type
    val mimeType = try {
        context.contentResolver.getType(uri)?.lowercase()
    } catch (_: Exception) { null }

    when {
        mimeType == "application/pdf" -> return ResolvedStatementFile("$fileName.pdf", true)
        mimeType == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> return ResolvedStatementFile("$fileName.xlsx", true)
        mimeType == "application/vnd.ms-excel" || mimeType == "application/x-excel" || mimeType == "application/msexcel" || mimeType == "application/excel" -> return ResolvedStatementFile("$fileName.xls", true)
        mimeType == "text/csv" || mimeType == "text/comma-separated-values" -> return ResolvedStatementFile("$fileName.csv", true)
    }

    // 4. Magic bytes inspection from stream
    try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val header = ByteArray(16)
            val read = stream.read(header)
            if (read >= 4) {
                // PDF: %PDF
                if (header[0] == 0x25.toByte() && header[1] == 0x50.toByte() && header[2] == 0x44.toByte() && header[3] == 0x46.toByte()) {
                    return ResolvedStatementFile("$fileName.pdf", true)
                }
                // ZIP / XLSX: PK\x03\x04
                if (header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() && header[2] == 0x03.toByte() && header[3] == 0x04.toByte()) {
                    return ResolvedStatementFile("$fileName.xlsx", true)
                }
                // OLE2 / XLS: 0xD0, 0xCF, 0x11, 0xE0
                if (read >= 8 && header[0] == 0xD0.toByte() && header[1] == 0xCF.toByte() && header[2] == 0x11.toByte() && header[3] == 0xE0.toByte()) {
                    return ResolvedStatementFile("$fileName.xls", true)
                }
            }
        }
    } catch (_: Exception) {}

    return ResolvedStatementFile(fileName, false)
}

@Composable
fun ImportRowPreviewCard(row: ParsedImportRow, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(
            containerColor = if (row.isDuplicate) OutstandingAmber.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.description,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryChip(categoryName = row.suggestedCategory)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = DateUtils.formatShortDisplay(row.date),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                    if (row.isDuplicate) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Duplicate",
                            color = OutstandingAmber,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                val isIncome = row.creditAmount > 0
                Text(
                    text = "${if (isIncome) "+" else "-"}${CurrencyFormatter.formatInr(row.amount)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isIncome) IncomeGreen else ExpenseRed
                )
                if (row.balance != null) {
                    Text(
                        text = "Bal: ${CurrencyFormatter.formatInrCompact(row.balance)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}
