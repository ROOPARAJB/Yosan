package com.example.features.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.CategoryType
import com.example.data.local.entity.TransactionEntity
import com.example.data.local.entity.TransactionType
import com.example.features.rules.CategorizationEngine
import com.example.ui.components.CategoryChip
import com.example.ui.theme.*
import com.example.features.transactions.FinanceViewModel
import com.example.features.transactions.SmartRulePrompt
import com.example.utils.CurrencyFormatter
import com.example.utils.DateUtils

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun TransactionDetailSheet(
    transaction: TransactionEntity,
    viewModel: FinanceViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val allTransactions by viewModel.allTransactions.collectAsState()
    val currentTx = allTransactions.find { it.id == transaction.id } ?: transaction

    val categories by viewModel.categories.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isEditingDesc by remember { mutableStateOf(false) }
    var editedDescText by remember(currentTx.description) { mutableStateOf("") }

    var pendingTypeChange by remember { mutableStateOf<TransactionType?>(null) }

    if (pendingTypeChange != null) {
        val targetType = pendingTypeChange!!
        AlertDialog(
            onDismissRequest = { pendingTypeChange = null },
            title = { Text("Change Transaction Type?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Do you want to reclassify this transaction as ${targetType.name}? This will immediately update your dashboard calculations and financial reports.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateTransactionType(currentTx, targetType)
                        pendingTypeChange = null
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingTypeChange = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Transaction Details",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Amount Hero Banner
            val isPositive = currentTx.transactionType == TransactionType.INCOME ||
                    currentTx.transactionType == TransactionType.REFUND ||
                    currentTx.transactionType == TransactionType.BORROWING ||
                    (currentTx.transactionType == TransactionType.TRANSFER && currentTx.creditAmount > 0)

            val amountColor = when (currentTx.transactionType) {
                TransactionType.INCOME, TransactionType.REFUND -> IncomeGreen
                TransactionType.BORROWING -> OutstandingAmber
                TransactionType.LENDING -> LendingIndigo
                TransactionType.INVESTMENT -> Color(0xFF10B981)
                TransactionType.TRANSFER -> if (currentTx.creditAmount > 0) IncomeGreen else TransferSlate
                else -> ExpenseRed
            }

            Surface(
                color = amountColor.copy(alpha = 0.12f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = currentTx.transactionType.name,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = amountColor
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val prefix = if (isPositive) "+" else if (currentTx.transactionType == TransactionType.TRANSFER) "" else "-"
                    Text(
                        text = "$prefix${CurrencyFormatter.formatInr(currentTx.amount)}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = amountColor
                    )
                    if (currentTx.balanceAfterTransaction != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Running Balance: ${CurrencyFormatter.formatInr(currentTx.balanceAfterTransaction)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Transaction Type Selector Chips
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    text = "Transaction Type",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val availableTypes = listOf(
                        TransactionType.EXPENSE to "Expense",
                        TransactionType.INCOME to "Income",
                        TransactionType.LENDING to "Lend",
                        TransactionType.BORROWING to "Borrow",
                        TransactionType.INVESTMENT to "Investment",
                        TransactionType.TRANSFER to "Transfer"
                    )

                    availableTypes.forEach { (type, label) ->
                        val isSelected = currentTx.transactionType == type
                        val typeColor = when (type) {
                            TransactionType.INCOME, TransactionType.REFUND -> IncomeGreen
                            TransactionType.EXPENSE -> ExpenseRed
                            TransactionType.LENDING -> LendingIndigo
                            TransactionType.BORROWING -> OutstandingAmber
                            TransactionType.INVESTMENT -> Color(0xFF10B981)
                            TransactionType.TRANSFER -> TransferSlate
                            else -> MaterialTheme.colorScheme.primary
                        }

                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (!isSelected) {
                                    pendingTypeChange = type
                                }
                            },
                            label = {
                                Text(
                                    text = label,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            leadingIcon = {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = typeColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = typeColor.copy(alpha = 0.18f),
                                selectedLabelColor = typeColor
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                selectedBorderColor = typeColor,
                                borderColor = MaterialTheme.colorScheme.outlineVariant
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Details Table
            DetailRow(label = "Date", value = DateUtils.formatForDisplay(currentTx.transactionDate))

            // Description Block
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            ) {
                Text(
                    text = "Description (Click to append keyword)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                if (isEditingDesc) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = currentTx.description,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = editedDescText,
                                onValueChange = { editedDescText = it },
                                label = { Text("Add Keyword") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    if (editedDescText.isNotBlank()) {
                                        val combined = "${currentTx.description} ${editedDescText.trim()}"
                                        viewModel.updateTransactionDescription(currentTx.id, combined)
                                        isEditingDesc = false
                                        editedDescText = ""
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Check, contentDescription = "Save Keyword", tint = IncomeGreen)
                            }
                            IconButton(
                                onClick = {
                                    editedDescText = ""
                                    isEditingDesc = false
                                }
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel", tint = ExpenseRed)
                            }
                        }
                        // Keyword suggestions from description words
                        val suggestions = remember(currentTx.description) {
                            currentTx.description
                                .split(" ", "\t", "-", "_", "/", ".")
                                .map { it.trim().lowercase() }
                                .filter { it.length > 3 }
                                .distinct()
                                .take(8)
                        }
                        if (suggestions.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Tap a suggestion to fill keyword:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            androidx.compose.foundation.layout.FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                suggestions.forEach { word ->
                                    SuggestionChip(
                                        onClick = { editedDescText = word },
                                        label = { Text(word, style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        text = currentTx.description,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isEditingDesc = true }
                    )
                }
            }
            
            // Categorisation Section with Direct Interactive Chips
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Category Assignment",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    TextButton(
                        onClick = { showCategoryPicker = true },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add New", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                androidx.compose.foundation.layout.FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { cat ->
                        val isCurrent = currentTx.categoryName.equals(cat.name, ignoreCase = true)
                        val catColor = try {
                            Color(android.graphics.Color.parseColor(cat.colorHex))
                        } catch (e: Exception) {
                            MaterialTheme.colorScheme.primary
                        }

                        FilterChip(
                            selected = isCurrent,
                            onClick = {
                                viewModel.updateTransactionCategory(currentTx, cat)
                                onDismiss()
                            },
                            label = {
                                Text(
                                    text = cat.name,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 12.sp
                                )
                            },
                            leadingIcon = {
                                if (isCurrent) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = catColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(catColor)
                                    )
                                }
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = catColor.copy(alpha = 0.18f),
                                selectedLabelColor = MaterialTheme.colorScheme.onSurface
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isCurrent,
                                selectedBorderColor = catColor,
                                borderColor = MaterialTheme.colorScheme.outlineVariant
                            )
                        )
                    }
                }
            }

            val matchingAccount = remember(accounts, currentTx.accountId) {
                accounts.firstOrNull { it.id == currentTx.accountId }
            }
            val accountLabel = matchingAccount?.let {
                if (it.bankName.isNotBlank() && it.accountName.isNotBlank() && !it.bankName.equals(it.accountName, ignoreCase = true)) {
                    "${it.bankName} (${it.accountName})"
                } else {
                    it.bankName.ifBlank { it.accountName }.ifBlank { "Primary Account" }
                }
            } ?: "Primary Account"
            DetailRow(label = "Bank / Account", value = accountLabel)

            if (currentTx.debitAmount > 0) {
                DetailRow(label = "Debit", value = CurrencyFormatter.formatInr(currentTx.debitAmount))
            }
            if (currentTx.creditAmount > 0) {
                DetailRow(label = "Credit", value = CurrencyFormatter.formatInr(currentTx.creditAmount))
            }

            // Advance ID / Batch Tag
            var isEditingAdvanceId by remember { mutableStateOf(false) }
            var editedAdvanceIdText by remember(currentTx.advanceId) { mutableStateOf(currentTx.advanceId ?: "") }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Advance ID / Batch",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isEditingAdvanceId) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = editedAdvanceIdText,
                            onValueChange = { editedAdvanceIdText = it },
                            placeholder = { Text("advance id 1", fontSize = 12.sp) },
                            modifier = Modifier.width(150.dp),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )
                        IconButton(onClick = {
                            viewModel.updateTransactionAdvanceId(currentTx.id, editedAdvanceIdText)
                            isEditingAdvanceId = false
                        }) {
                            Icon(Icons.Default.Check, contentDescription = "Save Advance ID", tint = IncomeGreen)
                        }
                        IconButton(onClick = {
                            editedAdvanceIdText = currentTx.advanceId ?: ""
                            isEditingAdvanceId = false
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel", tint = ExpenseRed)
                        }
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { isEditingAdvanceId = true }
                    ) {
                        if (!currentTx.advanceId.isNullOrBlank()) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = currentTx.advanceId,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        } else {
                            Text(
                                text = "None (tap to add)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit Advance ID",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (currentTx.referenceNumber.isNotBlank()) {
                DetailRow(label = "Reference #", value = currentTx.referenceNumber)
            }
            if (currentTx.notes.isNotBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    Text(
                        text = "Notes",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = currentTx.notes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            DetailRow(
                label = "Source",
                value = if (currentTx.isManual) "Manual Entry" else "Imported (${currentTx.source})"
            )

            if (currentTx.categorizationConfidence > 0f) {
                DetailRow(
                    label = "Rule Confidence",
                    value = "${(currentTx.categorizationConfidence * 100).toInt()}% match"
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Actions: Delete only
            Button(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Delete Transaction")
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Category Picker Dialog
        if (showCategoryPicker) {
            var showAddCatInput by remember { mutableStateOf(false) }
            var newCatName by remember { mutableStateOf("") }
            var newCatType by remember { mutableStateOf(CategoryType.EXPENSE) }

            AlertDialog(
                onDismissRequest = { showCategoryPicker = false },
                title = { Text("Select Category", fontWeight = FontWeight.Bold) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (!showAddCatInput) {
                            if (categories.isEmpty()) {
                                Text(
                                    text = "No categories defined yet.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(vertical = 12.dp)
                                )
                            }
                            LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                                items(categories) { cat: CategoryEntity ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.updateTransactionCategory(transaction, cat)
                                                showCategoryPicker = false
                                                onDismiss()
                                            }
                                            .padding(vertical = 10.dp, horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CategoryChip(categoryName = cat.name, colorHex = cat.colorHex)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(text = cat.name, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    showAddCatInput = true
                                    newCatType = if (transaction.transactionType == TransactionType.INCOME || transaction.transactionType == TransactionType.REFUND) CategoryType.INCOME else CategoryType.EXPENSE
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Add Custom Category")
                            }
                        } else {
                            Text("Create New Category", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = newCatName,
                                onValueChange = { newCatName = it },
                                label = { Text("Category Name") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { showAddCatInput = false }) { Text("Back") }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        if (newCatName.isNotBlank()) {
                                            val colorsList = listOf("#EF4444", "#F59E0B", "#3B82F6", "#10B981", "#8B5CF6", "#EC4899", "#06B6D4", "#94A3B8")
                                            val usedColors = categories.map { it.colorHex.uppercase() }.toSet()
                                            val autoColor = colorsList.firstOrNull { it.uppercase() !in usedColors } ?: colorsList[categories.size % colorsList.size]
                                            viewModel.addCategory(newCatName.trim(), newCatType, autoColor)
                                            newCatName = ""
                                            showAddCatInput = false
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Create")
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    if (!showAddCatInput) {
                        TextButton(onClick = { showCategoryPicker = false }) { Text("Cancel") }
                    }
                }
            )
        }

        // Delete Confirmation Dialog
        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("Delete Transaction?") },
                text = { Text("Are you sure you want to delete this transaction record?") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteTransaction(transaction.id)
                            showDeleteConfirm = false
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ExpenseRed)
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(2f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}
