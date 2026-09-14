package com.example.features.lending

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.TransactionEntity
import com.example.data.local.entity.TransactionType
import com.example.features.transactions.FinanceViewModel
import com.example.ui.components.EmptyState
import com.example.ui.components.MetricCard
import com.example.ui.theme.*
import com.example.utils.CurrencyFormatter
import com.example.utils.DateUtils

@Composable
fun BorrowingScreen(
    viewModel: FinanceViewModel,
    onAddBorrowClick: () -> Unit,
    onTransactionClick: (TransactionEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val allTransactions by viewModel.allTransactions.collectAsState()

    val borrowTxs = remember(allTransactions) {
        allTransactions.filter {
            it.transactionType == TransactionType.BORROWING ||
                    it.categoryName.contains("Borrow", ignoreCase = true) ||
                    it.categoryName.contains("Debt", ignoreCase = true)
        }
    }

    val totalBorrowed = remember(borrowTxs) {
        borrowTxs.sumOf { if (it.creditAmount > 0.0) it.creditAmount else it.amount }
    }

    var selectedFilter by remember { mutableStateOf("All") }

    val filteredList = remember(borrowTxs, selectedFilter) {
        when (selectedFilter) {
            "Active" -> borrowTxs.filter { !it.notes.contains("#SETTLED", ignoreCase = true) }
            "Settled" -> borrowTxs.filter { it.notes.contains("#SETTLED", ignoreCase = true) }
            else -> borrowTxs
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("borrowing_screen"),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        // 1. Header & Hero Metric Cards
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "Money Borrowed & Debts",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Track borrowed funds from contacts or lenders and settlements made.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        title = "Total Borrowed",
                        amount = totalBorrowed,
                        icon = Icons.Default.CallReceived,
                        iconColor = OutstandingAmber,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Records",
                        amount = borrowTxs.size.toDouble(),
                        icon = Icons.Default.ReceiptLong,
                        iconColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 2. Action Bar & Filter Chips
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
                            selected = selectedFilter == "All",
                            onClick = { selectedFilter = "All" },
                            label = { Text("All (${borrowTxs.size})") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedFilter == "Active",
                            onClick = { selectedFilter = "Active" },
                            label = { Text("Active") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedFilter == "Settled",
                            onClick = { selectedFilter = "Settled" },
                            label = { Text("Settled") }
                        )
                    }
                }

                Button(
                    onClick = onAddBorrowClick,
                    modifier = Modifier.height(38.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = OutstandingAmber),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Borrow", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        // 3. Borrow List
        if (filteredList.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Outlined.CallReceived,
                    title = "No Borrow Records",
                    description = "When you borrow money from friends, contacts, or institutions, record it here with unique #BORROW-X IDs.",
                    actionText = "Record Borrowing",
                    onActionClick = onAddBorrowClick
                )
            }
        } else {
            items(filteredList, key = { it.id }) { tx ->
                val isSettled = tx.notes.contains("#SETTLED", ignoreCase = true)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onTransactionClick(tx) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val borrowTag = tx.advanceId?.takeIf { it.isNotBlank() } ?: tx.referenceNumber.takeIf { it.isNotBlank() }
                                if (borrowTag != null) {
                                    Surface(
                                        color = OutstandingAmber.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = if (borrowTag.startsWith("#")) borrowTag else "#$borrowTag",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = OutstandingAmber,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(
                                    text = tx.description.ifBlank { "Borrowed Amount" },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = tx.categoryName.ifBlank { "Borrowing" },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = DateUtils.formatShortDisplay(tx.transactionDate),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                            if (tx.notes.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = tx.notes,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "+${CurrencyFormatter.formatInr(if (tx.creditAmount > 0) tx.creditAmount else tx.amount)}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = OutstandingAmber
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                color = if (isSettled) IncomeGreen.copy(alpha = 0.15f) else OutstandingAmber.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = if (isSettled) "Settled" else "Outstanding",
                                    color = if (isSettled) IncomeGreen else OutstandingAmber,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
