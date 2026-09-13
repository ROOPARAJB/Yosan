package com.example.features.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.CategoryType
import com.example.data.local.entity.LoanEntity
import com.example.data.local.entity.TransactionType
import com.example.ui.components.CategoryChip
import com.example.ui.theme.*
import com.example.features.transactions.FinanceViewModel
import com.example.utils.DateUtils

enum class AddTab {
    EXPENSE, INCOME, LEND_MONEY, INVESTMENT, TRANSFER, COMPANY_EXPENSE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionSheet(
    viewModel: FinanceViewModel,
    initialTab: AddTab = AddTab.EXPENSE,
    preselectedLoan: LoanEntity? = null,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(initialTab) }
    var investmentName by remember { mutableStateOf("") }

    val accounts by viewModel.accounts.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val loans by viewModel.loans.collectAsState()
    val activeLoans = remember(loans) { loans.filter { it.remainingAmount > 0 } }

    var amountText by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf(DateUtils.getCurrentDate()) }
    var selectedCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    var selectedAccountId by remember { mutableStateOf(accounts.firstOrNull()?.id ?: 1L) }
    var targetAccountId by remember { mutableStateOf(accounts.getOrNull(1)?.id ?: accounts.firstOrNull()?.id ?: 1L) }
    var notes by remember { mutableStateOf("") }

    // Lending fields
    var personName by remember { mutableStateOf("") }
    var personPhone by remember { mutableStateOf("") }
    var expectedDate by remember { mutableStateOf("") }

    // Repayment fields
    var selectedLoanId by remember { mutableStateOf(preselectedLoan?.id ?: activeLoans.firstOrNull()?.id ?: 0L) }
    var paymentMethod by remember { mutableStateOf("UPI") }

    // Company Expense fields
    var companyName by remember { mutableStateOf("") }
    var companyCategory by remember { mutableStateOf("Travel") }

    // DatePicker states
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = System.currentTimeMillis()
    )

    var showExpectedDatePicker by remember { mutableStateOf(false) }
    val expectedDatePickerState = rememberDatePickerState(
        initialSelectedDateMillis = System.currentTimeMillis()
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Title Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "New Record",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Tab Selector
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedTab == AddTab.EXPENSE,
                        onClick = {
                            selectedTab = AddTab.EXPENSE
                            selectedCategory = null
                        },
                        label = { Text("Expense") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ExpenseRed.copy(alpha = 0.2f))
                    )
                }
                item {
                    FilterChip(
                        selected = selectedTab == AddTab.INCOME,
                        onClick = {
                            selectedTab = AddTab.INCOME
                            selectedCategory = null
                        },
                        label = { Text("Income") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = IncomeGreen.copy(alpha = 0.2f))
                    )
                }
                item {
                    FilterChip(
                        selected = selectedTab == AddTab.LEND_MONEY,
                        onClick = {
                            selectedTab = AddTab.LEND_MONEY
                            selectedCategory = null
                        },
                        label = { Text("Lend Money") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = LendingIndigo.copy(alpha = 0.2f))
                    )
                }
                item {
                    FilterChip(
                        selected = selectedTab == AddTab.INVESTMENT,
                        onClick = {
                            selectedTab = AddTab.INVESTMENT
                            selectedCategory = null
                        },
                        label = { Text("Investment") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = OutstandingAmber.copy(alpha = 0.2f))
                    )
                }
                item {
                    FilterChip(
                        selected = selectedTab == AddTab.COMPANY_EXPENSE,
                        onClick = {
                            selectedTab = AddTab.COMPANY_EXPENSE
                            selectedCategory = null
                        },
                        label = { Text("Company") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CompanySky.copy(alpha = 0.2f))
                    )
                }
                item {
                    FilterChip(
                        selected = selectedTab == AddTab.TRANSFER,
                        onClick = {
                            selectedTab = AddTab.TRANSFER
                            selectedCategory = null
                        },
                        label = { Text("Transfer") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = TransferSlate.copy(alpha = 0.2f))
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Amount Input
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                label = { Text("Amount") },
                prefix = { Text("₹ ", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("amount_input"),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Date Field
            if (showDatePicker) {
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                datePickerState.selectedDateMillis?.let { millis ->
                                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                                    dateText = sdf.format(java.util.Date(millis))
                                }
                                showDatePicker = false
                            }
                        ) {
                            Text("OK")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDatePicker = false }) {
                            Text("Cancel")
                        }
                    }
                ) {
                    DatePicker(state = datePickerState)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDatePicker = true }
            ) {
                OutlinedTextField(
                    value = dateText,
                    onValueChange = { },
                    label = { Text("Date") },
                    leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = false,
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Specific Fields according to selected tab
            when (selectedTab) {
                AddTab.EXPENSE, AddTab.INCOME -> {
                    // Description
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description (e.g. Food, Travel, Salary)") },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("description_input"),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Category Selector
                    Text(
                        text = "Select Category",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    val filteredCats = categories.filter {
                        if (selectedTab == AddTab.INCOME) it.type == CategoryType.INCOME else it.type == CategoryType.EXPENSE
                    }

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredCats) { cat ->
                            val isSelected = selectedCategory?.id == cat.id
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { selectedCategory = cat },
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = cat.name,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                AddTab.LEND_MONEY -> {
                    OutlinedTextField(
                        value = personName,
                        onValueChange = { personName = it },
                        label = { Text("Person Name") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = personPhone,
                        onValueChange = { personPhone = it },
                        label = { Text("Phone Number (Optional)") },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    if (showExpectedDatePicker) {
                        DatePickerDialog(
                            onDismissRequest = { showExpectedDatePicker = false },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        expectedDatePickerState.selectedDateMillis?.let { millis ->
                                            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                                            expectedDate = sdf.format(java.util.Date(millis))
                                        }
                                        showExpectedDatePicker = false
                                    }
                                ) {
                                    Text("OK")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showExpectedDatePicker = false }) {
                                    Text("Cancel")
                                }
                            }
                        ) {
                            DatePicker(state = expectedDatePickerState)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showExpectedDatePicker = true }
                    ) {
                        OutlinedTextField(
                            value = expectedDate,
                            onValueChange = { },
                            label = { Text("Expected Repayment Date (Optional)") },
                            leadingIcon = { Icon(Icons.Default.Event, contentDescription = null) },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                            enabled = false,
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                disabledBorderColor = MaterialTheme.colorScheme.outline,
                                disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }

                AddTab.INVESTMENT -> {
                    // Description of Investment tab with info box
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = "Info", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Track your assets like Mutual Funds, Stocks, Gold, Real Estate, FD, etc. This transaction will deduct the amount from your selected account.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }

                    // Investment Name / Type input field
                    OutlinedTextField(
                        value = investmentName,
                        onValueChange = { investmentName = it },
                        label = { Text("Investment Name / Type (e.g., Mutual Funds, HDFC FD)") },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                AddTab.COMPANY_EXPENSE -> {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Reason (e.g. Office Cab, Dinner with client, Train travel)") },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = companyCategory,
                        onValueChange = { companyCategory = it },
                        label = { Text("Category (Travel, Food, Cab, Office)") },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = companyName,
                        onValueChange = { companyName = it },
                        label = { Text("Company Name") },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                AddTab.TRANSFER -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SyncAlt, contentDescription = "Transfer", tint = TransferSlate, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Transfer funds between your accounts. Both balances will sync automatically.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }

                    Text("From Account", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(accounts) { acc ->
                            val isSelected = selectedAccountId == acc.id
                            Surface(
                                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { selectedAccountId = acc.id },
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = acc.accountName,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("To Account", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(accounts) { acc ->
                            val isSelected = targetAccountId == acc.id
                            Surface(
                                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { targetAccountId = acc.id },
                                color = if (isSelected) TransferSlate else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = acc.accountName,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                else -> {}
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Notes
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (Optional)") },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Submit Button
            val isValid = amountText.toDoubleOrNull() != null && amountText.toDoubleOrNull()!! > 0

            Button(
                onClick = {
                    val amt = amountText.toDoubleOrNull() ?: return@Button
                    when (selectedTab) {
                        AddTab.EXPENSE -> {
                            val cat = selectedCategory ?: categories.firstOrNull { it.type == com.example.data.local.entity.CategoryType.EXPENSE }
                            if (cat == null) {
                                viewModel.showMessage("Please create an Expense category in Settings first!")
                                return@Button
                            }
                            viewModel.addManualTransaction(
                                date = dateText,
                                description = if (description.isNotBlank()) description else cat.name,
                                amount = amt,
                                type = TransactionType.EXPENSE,
                                categoryId = cat.id,
                                categoryName = cat.name,
                                accountId = selectedAccountId,
                                notes = notes
                            )
                        }
                        AddTab.INCOME -> {
                            val cat = selectedCategory ?: categories.firstOrNull { it.type == com.example.data.local.entity.CategoryType.INCOME }
                            if (cat == null) {
                                viewModel.showMessage("Please create an Income category in Settings first!")
                                return@Button
                            }
                            viewModel.addManualTransaction(
                                date = dateText,
                                description = if (description.isNotBlank()) description else "Income Credit",
                                amount = amt,
                                type = TransactionType.INCOME,
                                categoryId = cat.id,
                                categoryName = cat.name,
                                accountId = selectedAccountId,
                                notes = notes
                            )
                        }
                        AddTab.LEND_MONEY -> {
                            if (personName.isBlank()) {
                                viewModel.showMessage("Please enter the borrower's name")
                                return@Button
                            }
                            viewModel.addLoan(
                                personName = personName.trim(),
                                phone = personPhone.trim(),
                                amount = amt,
                                lentDate = dateText,
                                expectedDate = expectedDate.takeIf { it.isNotBlank() },
                                notes = notes
                            )
                        }
                        AddTab.INVESTMENT -> {
                            if (investmentName.isBlank()) {
                                viewModel.showMessage("Please enter the Investment Name/Type")
                                return@Button
                            }
                            viewModel.addManualTransaction(
                                date = dateText,
                                description = "Investment: ${investmentName.trim()}",
                                amount = amt,
                                type = TransactionType.INVESTMENT,
                                categoryId = 0L,
                                categoryName = "Investment",
                                accountId = selectedAccountId,
                                notes = notes
                            )
                        }
                        AddTab.COMPANY_EXPENSE -> {
                            if (companyName.isBlank()) {
                                viewModel.showMessage("Please enter the company/employer name")
                                return@Button
                            }
                            viewModel.addCompanyExpense(
                                date = dateText,
                                amount = amt,
                                reason = if (description.isNotBlank()) description.trim() else "Company Expense",
                                category = companyCategory,
                                company = companyName.trim(),
                                method = paymentMethod,
                                notes = notes
                            )
                        }
                        AddTab.TRANSFER -> {
                            if (selectedAccountId == targetAccountId) {
                                viewModel.showMessage("From and To accounts cannot be the same!")
                                return@Button
                            }
                            viewModel.addTransferTransaction(
                                fromAccountId = selectedAccountId,
                                toAccountId = targetAccountId,
                                amount = amt,
                                date = dateText,
                                notes = notes
                            )
                        }
                        else -> {}
                    }
                    onDismiss()
                },
                enabled = isValid,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_transaction_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = when (selectedTab) {
                        AddTab.EXPENSE -> ExpenseRed
                        AddTab.INCOME -> IncomeGreen
                        AddTab.LEND_MONEY -> LendingIndigo
                        AddTab.INVESTMENT -> OutstandingAmber
                        AddTab.COMPANY_EXPENSE -> CompanySky
                        AddTab.TRANSFER -> TransferSlate
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
            ) {
                Text(
                    text = "Save Record",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
