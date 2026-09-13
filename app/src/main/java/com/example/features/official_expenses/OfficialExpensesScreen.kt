package com.example.features.official_expenses

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import com.example.data.local.entity.CompanyExpenseEntity
import com.example.features.transactions.FinanceViewModel
import com.example.ui.components.EmptyState
import com.example.ui.components.MetricCard
import com.example.ui.theme.*
import com.example.utils.CurrencyFormatter
import com.example.utils.DateUtils

enum class ExpenseSectionTab(val label: String) {
    PENDING_CLAIMS("Pending Claims"),
    REIMBURSEMENTS("Reimbursements")
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OfficialExpensesScreen(
    viewModel: FinanceViewModel,
    onAddExpenseClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val companyExpenses by viewModel.companyExpenses.collectAsState()
    val selectedExpenseIds = remember { mutableStateListOf<Long>() }
    var editingExpense by remember { mutableStateOf<CompanyExpenseEntity?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    var currentSectionTab by remember { mutableStateOf(ExpenseSectionTab.PENDING_CLAIMS) }
    var selectedCategoryFilter by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val totalAmount = remember(companyExpenses) { companyExpenses.sumOf { it.amount } }
    val reimbursedList = remember(companyExpenses) { companyExpenses.filter { it.isReimbursed } }
    val pendingList = remember(companyExpenses) { companyExpenses.filterNot { it.isReimbursed } }

    val reimbursedAmount = remember(reimbursedList) { reimbursedList.sumOf { it.amount } }
    val pendingAmount = remember(pendingList) { pendingList.sumOf { it.amount } }

    val activeList = if (currentSectionTab == ExpenseSectionTab.PENDING_CLAIMS) pendingList else reimbursedList

    val categories = remember(companyExpenses) {
        companyExpenses.map { it.category }.filter { it.isNotBlank() }.distinct()
    }

    val filteredExpenses = remember(activeList, selectedCategoryFilter, searchQuery) {
        activeList.filter { exp ->
            val matchesCat = selectedCategoryFilter == null || exp.category.equals(selectedCategoryFilter, ignoreCase = true)
            val matchesQuery = searchQuery.isBlank() ||
                    exp.reason.contains(searchQuery, ignoreCase = true) ||
                    exp.companyName.contains(searchQuery, ignoreCase = true) ||
                    exp.notes.contains(searchQuery, ignoreCase = true)
            matchesCat && matchesQuery
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("official_expenses_screen"),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        // 1. KPI Metric Summary Cards
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "Track official work expenses and manage settled reimbursement claims.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        title = "Pending Claim",
                        amount = pendingAmount,
                        icon = Icons.Default.PendingActions,
                        iconColor = OutstandingAmber,
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
                        title = "Total Official",
                        amount = totalAmount,
                        icon = Icons.Default.BusinessCenter,
                        iconColor = CompanySky,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 2. Section Navigation Tabs (Pending Claims vs Settled Reimbursements)
        item {
            TabRow(
                selectedTabIndex = currentSectionTab.ordinal,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                contentColor = MaterialTheme.colorScheme.primary,
                divider = {}
            ) {
                Tab(
                    selected = currentSectionTab == ExpenseSectionTab.PENDING_CLAIMS,
                    onClick = { currentSectionTab = ExpenseSectionTab.PENDING_CLAIMS },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Pending Claims",
                                fontWeight = if (currentSectionTab == ExpenseSectionTab.PENDING_CLAIMS) FontWeight.Bold else FontWeight.Normal
                            )
                            if (pendingList.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = OutstandingAmber.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        text = "${pendingList.size}",
                                        color = OutstandingAmber,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                )
                Tab(
                    selected = currentSectionTab == ExpenseSectionTab.REIMBURSEMENTS,
                    onClick = { currentSectionTab = ExpenseSectionTab.REIMBURSEMENTS },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Reimbursements",
                                fontWeight = if (currentSectionTab == ExpenseSectionTab.REIMBURSEMENTS) FontWeight.Bold else FontWeight.Normal
                            )
                            if (reimbursedList.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = IncomeGreen.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        text = "${reimbursedList.size}",
                                        color = IncomeGreen,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                )
            }
        }

        // 3. Search & Category Filters + Add Action
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search description or company...", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                    )

                    Button(
                        onClick = onAddExpenseClick,
                        modifier = Modifier.height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CompanySky),
                        contentPadding = PaddingValues(horizontal = 14.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add", style = MaterialTheme.typography.labelMedium)
                    }
                }

                if (categories.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        item {
                            FilterChip(
                                selected = selectedCategoryFilter == null,
                                onClick = { selectedCategoryFilter = null },
                                label = { Text("All Categories") }
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
                }
            }
        }

        // 4. Batch Selection Bar
        if (selectedExpenseIds.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { selectedExpenseIds.clear() }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear Selection")
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${selectedExpenseIds.size} Selected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Row {
                        TextButton(
                            onClick = {
                                val targetReimbursed = currentSectionTab == ExpenseSectionTab.PENDING_CLAIMS
                                selectedExpenseIds.forEach { id ->
                                    viewModel.toggleCompanyReimbursement(id, !targetReimbursed)
                                }
                                selectedExpenseIds.clear()
                            }
                        ) {
                            Text(if (currentSectionTab == ExpenseSectionTab.PENDING_CLAIMS) "Mark Reimbursed" else "Mark Pending")
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
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }

        // 5. Items List or Empty State
        if (filteredExpenses.isEmpty()) {
            item {
                if (currentSectionTab == ExpenseSectionTab.PENDING_CLAIMS) {
                    EmptyState(
                        icon = Icons.Outlined.CheckCircleOutline,
                        title = "No Pending Claims",
                        description = "All official expenses have been reimbursed or no pending expenses recorded.",
                        actionText = "Log Official Expense",
                        onActionClick = onAddExpenseClick
                    )
                } else {
                    EmptyState(
                        icon = Icons.Outlined.ReceiptLong,
                        title = "No Settled Reimbursements",
                        description = "Reimbursed claims will appear here once marked as received.",
                        actionText = "Add Expense",
                        onActionClick = onAddExpenseClick
                    )
                }
            }
        } else {
            items(filteredExpenses, key = { it.id }) { expense ->
                val isSelected = selectedExpenseIds.contains(expense.id)
                OfficialExpenseCard(
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

    // Edit Expense Dialog
    if (showEditDialog && editingExpense != null) {
        val exp = editingExpense!!
        var reason by remember(exp) { mutableStateOf(exp.reason) }
        var amountText by remember(exp) { mutableStateOf(exp.amount.toString()) }
        var date by remember(exp) { mutableStateOf(exp.date) }
        var category by remember(exp) { mutableStateOf(exp.category) }
        var companyName by remember(exp) { mutableStateOf(exp.companyName) }
        var paymentMethod by remember(exp) { mutableStateOf(exp.paymentMethod) }
        var isReimbursed by remember(exp) { mutableStateOf(exp.isReimbursed) }
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
                        label = { Text("Company / Client Name") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("Category (e.g. Travel, Client Meal, Supplies)") },
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

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isReimbursed = !isReimbursed }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = isReimbursed, onCheckedChange = { isReimbursed = it })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isReimbursed) "Reimbursed / Settled" else "Pending Reimbursement Claim",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }

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
                                isReimbursed = isReimbursed,
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
fun OfficialExpenseCard(
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
                            text = "${expense.companyName.ifBlank { "Corporate" }} • ${expense.category.ifBlank { "Official" }}",
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = if (expense.isReimbursed) Icons.Default.CheckCircle else Icons.Default.Schedule,
                            contentDescription = null,
                            tint = if (expense.isReimbursed) IncomeGreen else OutstandingAmber,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (expense.isReimbursed) "Reimbursed" else "Pending Claim",
                            color = if (expense.isReimbursed) IncomeGreen else OutstandingAmber,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}
