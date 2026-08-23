package com.example.features.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.TransactionEntity
import com.example.data.local.entity.TransactionType
import com.example.ui.components.EmptyState
import com.example.ui.components.SectionHeader
import com.example.ui.components.TransactionItemCard
import com.example.ui.components.PrivacyAmountText
import com.example.ui.theme.*
import com.example.features.transactions.FinanceViewModel
import com.example.utils.CurrencyFormatter
import com.example.utils.DateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    viewModel: FinanceViewModel,
    onTransactionClick: (TransactionEntity) -> Unit,
    onNavigateToImport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val transactions by viewModel.filteredTransactions.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedType by viewModel.selectedTypeFilter.collectAsState()
    val selectedCategory by viewModel.selectedCategoryFilter.collectAsState()
    val selectedMonth by viewModel.selectedMonthFilter.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()

    // Group transactions by Date
    val groupedTransactions = remember(transactions) {
        transactions.groupBy { it.transactionDate }
    }

    // Totals for filtered transactions
    val filteredIncome = remember(transactions) {
        transactions.filter { it.transactionType == TransactionType.INCOME || it.transactionType == TransactionType.REFUND }
            .sumOf { if (it.creditAmount > 0) it.creditAmount else it.amount }
    }
    val filteredExpense = remember(transactions) {
        transactions.filter { it.transactionType == TransactionType.EXPENSE }
            .sumOf { if (it.debitAmount > 0) it.debitAmount else it.amount }
    }

    var showDeleteAllDialog by remember { mutableStateOf(false) }
    val selectedTxIds = remember { mutableStateListOf<Long>() }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text("Delete All Transactions?", fontWeight = FontWeight.Bold) },
            text = { Text("This will permanently delete all transaction records from the database. This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAllDialog = false
                        viewModel.deleteAllTransactions()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color.Red)
                ) {
                    Text("Delete All", color = androidx.compose.ui.graphics.Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Bulk company expense dialog
    val pendingCompanyExpenses by viewModel.pendingCompanyExpenses.collectAsState()
    if (pendingCompanyExpenses.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissPendingCompanyExpenses() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Business,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Official Expenses Detected",
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column {
                    Text(
                        text = "Found ${pendingCompanyExpenses.size} transaction${if (pendingCompanyExpenses.size > 1) "s" else ""} with the 'company' keyword. Log them all as Official Expenses?",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    pendingCompanyExpenses.take(5).forEach { p ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = p.description.take(28) + if (p.description.length > 28) "…" else "",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = com.example.utils.CurrencyFormatter.formatInrCompact(p.amount),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = ExpenseRed
                            )
                        }
                    }
                    if (pendingCompanyExpenses.size > 5) {
                        Text(
                            text = "...and ${pendingCompanyExpenses.size - 5} more",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.logAllPendingCompanyExpenses() },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Log All (${pendingCompanyExpenses.size})")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissPendingCompanyExpenses() }) {
                    Text("Skip")
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("transactions_screen")
    ) {
        val isSelectionMode = selectedTxIds.isNotEmpty()

        // Header with Delete All or Multi-delete options
        if (isSelectionMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { selectedTxIds.clear() }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear Selection",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${selectedTxIds.size} Selected",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(
                    onClick = {
                        selectedTxIds.forEach { id ->
                            viewModel.deleteTransaction(id)
                        }
                        selectedTxIds.clear()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete Selected",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Statement Records",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = onNavigateToImport
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileUpload,
                            contentDescription = "Import",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Import",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = { showDeleteAllDialog = true }
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete All",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Delete All",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // 1. Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            placeholder = { Text("Search description, category, reference...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("transaction_search_input"),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
            )
        )

        // 2. Horizontal Filter Type Chips
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (selectedCategory != null) {
                item {
                    InputChip(
                        selected = true,
                        onClick = { viewModel.setCategoryFilter(null) },
                        label = { Text("Category: $selectedCategory", fontWeight = FontWeight.Bold) },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear Category Filter",
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    )
                }
            }
            item {
                FilterChip(
                    selected = selectedType == null,
                    onClick = { viewModel.setTypeFilter(null) },
                    label = { Text("All") }
                )
            }
            item {
                FilterChip(
                    selected = selectedType == TransactionType.EXPENSE,
                    onClick = { viewModel.setTypeFilter(if (selectedType == TransactionType.EXPENSE) null else TransactionType.EXPENSE) },
                    label = { Text("Expense") },
                    leadingIcon = {
                        if (selectedType == TransactionType.EXPENSE) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                )
            }
            item {
                FilterChip(
                    selected = selectedType == TransactionType.INCOME,
                    onClick = { viewModel.setTypeFilter(if (selectedType == TransactionType.INCOME) null else TransactionType.INCOME) },
                    label = { Text("Income") },
                    leadingIcon = {
                        if (selectedType == TransactionType.INCOME) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                )
            }
        }

        // 3. Sub-bar with filtered count & debit/credit total
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (selectedType != null) "${transactions.size} ${selectedType?.name?.lowercase()} transactions" else "${transactions.size} transactions",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
                Row {
                    val isRevealed by viewModel.isAmountTemporarilyRevealed.collectAsState()
                    val privacyEnabled = userProfile?.isPrivacyBlurEnabled ?: true
                    if (selectedType == null || selectedType == TransactionType.INCOME) {
                        PrivacyAmountText(
                            amount = filteredIncome,
                            prefix = "+",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = IncomeGreen,
                            isRevealed = isRevealed,
                            isPrivacyEnabled = privacyEnabled,
                            onTap = { viewModel.revealAmountsTemporarily() }
                        )
                        if (selectedType == null) {
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                    }
                    if (selectedType == null || selectedType == TransactionType.EXPENSE) {
                        PrivacyAmountText(
                            amount = filteredExpense,
                            prefix = "-",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = ExpenseRed,
                            isRevealed = isRevealed,
                            isPrivacyEnabled = privacyEnabled,
                            onTap = { viewModel.revealAmountsTemporarily() }
                        )
                    }
                }
            }
        }

        val allTx by viewModel.allTransactions.collectAsState()

        // 4. Statement Transactions List
        if (transactions.isEmpty()) {
            if (allTx.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.ReceiptLong,
                    title = "No Statements Recorded",
                    description = "Import your bank Excel statement or add transactions manually to get started.",
                    actionText = "Import Statement",
                    onActionClick = onNavigateToImport
                )
            } else if (selectedCategory != null) {
                EmptyState(
                    icon = Icons.Outlined.Category,
                    title = "No $selectedCategory Statements",
                    description = "No transaction records found under category '$selectedCategory'.",
                    actionText = "Show All Statements",
                    onActionClick = { viewModel.setCategoryFilter(null) }
                )
            } else if (selectedType != null) {
                EmptyState(
                    icon = if (selectedType == TransactionType.INCOME) Icons.Outlined.Savings else Icons.Outlined.ReceiptLong,
                    title = if (selectedType == TransactionType.INCOME) "No Income Transactions" else "No Expense Transactions",
                    description = "No ${selectedType?.name?.lowercase()} statements found. Switch to All or import more statements.",
                    actionText = "Show All Statements",
                    onActionClick = { viewModel.setTypeFilter(null) }
                )
            } else {
                EmptyState(
                    icon = Icons.Outlined.SearchOff,
                    title = "No Matching Transactions",
                    description = "Try adjusting your search keywords or clearing filters.",
                    actionText = "Clear Filters",
                    onActionClick = { viewModel.clearFilters() }
                )
            }
        } else {
            val isRevealed by viewModel.isAmountTemporarilyRevealed.collectAsState()
            val privacyEnabled = userProfile?.isPrivacyBlurEnabled ?: true
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                groupedTransactions.forEach { (date, txList) ->
                    item(key = "header_$date") {
                        Text(
                            text = DateUtils.formatForDisplay(date),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                        )
                    }
                    items(txList, key = { it.id }) { tx ->
                        val isSelected = selectedTxIds.contains(tx.id)
                        TransactionItemCard(
                            transaction = tx,
                            isSelected = isSelected,
                            isRevealed = isRevealed,
                            isPrivacyEnabled = privacyEnabled,
                            onToggleReveal = { viewModel.revealAmountsTemporarily() },
                            onClick = {
                                if (isSelectionMode) {
                                    if (isSelected) {
                                        selectedTxIds.remove(tx.id)
                                    } else {
                                        selectedTxIds.add(tx.id)
                                    }
                                } else {
                                    onTransactionClick(tx)
                                }
                            },
                            onLongClick = {
                                if (!isSelectionMode) {
                                    selectedTxIds.add(tx.id)
                                } else {
                                    if (isSelected) {
                                        selectedTxIds.remove(tx.id)
                                    } else {
                                        selectedTxIds.add(tx.id)
                                    }
                                }
                            },
                            onDelete = { viewModel.deleteTransaction(tx.id) }
                        )
                    }
                }
            }

        }
    }
}
