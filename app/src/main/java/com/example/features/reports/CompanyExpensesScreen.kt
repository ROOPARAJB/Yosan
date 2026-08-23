package com.example.features.reports

import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.CompanyExpenseEntity
import com.example.ui.components.EmptyState
import com.example.ui.components.MetricCard
import com.example.ui.theme.*
import com.example.features.transactions.FinanceViewModel
import com.example.utils.CurrencyFormatter
import com.example.utils.DateUtils

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CompanyExpensesScreen(
    viewModel: FinanceViewModel,
    onAddExpenseClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val companyExpenses by viewModel.companyExpenses.collectAsState()
    val selectedExpenseIds = remember { mutableStateListOf<Long>() }
    var editingExpense by remember { mutableStateOf<CompanyExpenseEntity?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }

    val totalAmount = remember(companyExpenses) { companyExpenses.sumOf { it.amount } }
    val reimbursedAmount = remember(companyExpenses) { companyExpenses.filter { it.isReimbursed }.sumOf { it.amount } }
    val pendingAmount = remember(companyExpenses) { companyExpenses.filterNot { it.isReimbursed }.sumOf { it.amount } }

    var selectedCategoryFilter by remember { mutableStateOf<String?>(null) }

    val categories = remember(companyExpenses) {
        companyExpenses.map { it.category }.distinct()
    }

    val filteredExpenses = remember(companyExpenses, selectedCategoryFilter) {
        if (selectedCategoryFilter == null) companyExpenses
        else companyExpenses.filter { it.category.equals(selectedCategoryFilter, ignoreCase = true) }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("company_expenses_screen"),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        // 1. Header & Summary Cards
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "Track official expenses (Food train, Cab, Client meals) for reimbursement.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        title = "Total Company",
                        amount = totalAmount,
                        icon = Icons.Default.BusinessCenter,
                        iconColor = CompanySky,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Reimbursed",
                        amount = reimbursedAmount,
                        icon = Icons.Default.CheckCircle,
                        iconColor = IncomeGreen,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Pending Claim",
                        amount = pendingAmount,
                        icon = Icons.Default.Pending,
                        iconColor = OutstandingAmber,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Contextual Selection Header
        val isSelectionMode = selectedExpenseIds.isNotEmpty()
        if (isSelectionMode) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { selectedExpenseIds.clear() }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear Selection")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${selectedExpenseIds.size} Selected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = {
                            selectedExpenseIds.forEach { id ->
                                viewModel.deleteCompanyExpense(id)
                            }
                            selectedExpenseIds.clear()
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
            }
        }

        // 2. Filter by Category & Add Button
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    item {
                        FilterChip(
                            selected = selectedCategoryFilter == null,
                            onClick = { selectedCategoryFilter = null },
                            label = { Text("All") }
                        )
                    }
                    items(categories) { cat ->
                        FilterChip(
                            selected = selectedCategoryFilter == cat,
                            onClick = { selectedCategoryFilter = if (selectedCategoryFilter == cat) null else cat },
                            label = { Text(cat) }
                        )
                    }
                }

                Button(
                    onClick = onAddExpenseClick,
                    modifier = Modifier.height(38.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CompanySky),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        // 3. Expenses List
        if (filteredExpenses.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Outlined.BusinessCenter,
                    title = "No Company Expenses",
                    description = "Record official corporate expenses and track reimbursement payouts.",
                    actionText = "Add Expense",
                    onActionClick = onAddExpenseClick
                )
            }
        } else {
            items(filteredExpenses, key = { it.id }) { expense ->
                val isSelected = selectedExpenseIds.contains(expense.id)
                CompanyExpenseCard(
                    expense = expense,
                    isSelected = isSelected,
                    onClick = {
                        if (selectedExpenseIds.isNotEmpty()) {
                            if (isSelected) {
                                selectedExpenseIds.remove(expense.id)
                            } else {
                                selectedExpenseIds.add(expense.id)
                            }
                        } else {
                            editingExpense = expense
                            showEditDialog = true
                        }
                    },
                    onLongClick = {
                        if (!selectedExpenseIds.contains(expense.id)) {
                            selectedExpenseIds.add(expense.id)
                        } else {
                            selectedExpenseIds.remove(expense.id)
                        }
                    },
                    onToggleReimburse = {
                        viewModel.toggleCompanyReimbursement(expense.id, expense.isReimbursed)
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }
    }

    if (showEditDialog && editingExpense != null) {
        val exp = editingExpense!!
        var reason by remember(exp) { mutableStateOf(exp.reason) }
        var amountText by remember(exp) { mutableStateOf(exp.amount.toString()) }
        var date by remember(exp) { mutableStateOf(exp.date) }
        var category by remember(exp) { mutableStateOf(exp.category) }
        var companyName by remember(exp) { mutableStateOf(exp.companyName) }
        var paymentMethod by remember(exp) { mutableStateOf(exp.paymentMethod) }
        var notes by remember(exp) { mutableStateOf(exp.notes) }

        AlertDialog(
            onDismissRequest = {
                showEditDialog = false
                editingExpense = null
            },
            title = { Text("Edit Official Expense", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Reason / Description") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("Amount (₹)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = date,
                        onValueChange = { date = it },
                        label = { Text("Date (YYYY-MM-DD)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = companyName,
                        onValueChange = { companyName = it },
                        label = { Text("Company / Institution") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("Category (e.g. Travel, Food)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = paymentMethod,
                        onValueChange = { paymentMethod = it },
                        label = { Text("Payment Method") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notes (Optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = amountText.toDoubleOrNull() ?: 0.0
                        if (reason.isNotBlank() && amt > 0.0 && date.isNotBlank()) {
                            val updated = exp.copy(
                                reason = reason.trim(),
                                amount = amt,
                                date = date.trim(),
                                companyName = companyName.trim(),
                                category = category.trim(),
                                paymentMethod = paymentMethod.trim(),
                                notes = notes.trim(),
                                updatedAt = System.currentTimeMillis()
                            )
                            viewModel.updateCompanyExpense(updated)
                            showEditDialog = false
                            editingExpense = null
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showEditDialog = false
                        editingExpense = null
                    }
                ) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CompanyExpenseCard(
    expense: CompanyExpenseEntity,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    isSelected: Boolean = false,
    onToggleReimburse: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = { onLongClick?.invoke() }
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = expense.reason,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = CompanySky.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "${expense.companyName} • ${expense.category}",
                            style = MaterialTheme.typography.labelSmall,
                            color = CompanySky,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = DateUtils.formatShortDisplay(expense.date),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = CurrencyFormatter.formatInr(expense.amount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    color = if (expense.isReimbursed) IncomeGreen.copy(alpha = 0.15f) else OutstandingAmber.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.clickable { onToggleReimburse() }
                ) {
                    Text(
                        text = if (expense.isReimbursed) "Reimbursed ✓" else "Pending Claim",
                        color = if (expense.isReimbursed) IncomeGreen else OutstandingAmber,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}
