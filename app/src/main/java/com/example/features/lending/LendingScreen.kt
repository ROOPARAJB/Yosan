package com.example.features.lending

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
import com.example.data.local.entity.LoanEntity
import com.example.data.local.entity.LoanStatus
import com.example.ui.components.EmptyState
import com.example.ui.components.MetricCard
import com.example.ui.components.PersonLoanCard
import com.example.ui.theme.*
import com.example.features.transactions.FinanceViewModel
import com.example.utils.CurrencyFormatter

@Composable
fun LendingScreen(
    viewModel: FinanceViewModel,
    onAddLoanClick: () -> Unit,
    onRepayLoanClick: (LoanEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val loans by viewModel.loans.collectAsState()
    val summary by viewModel.dashboardSummary.collectAsState()

    var statusFilter by remember { mutableStateOf<LoanStatus?>(null) }

    val filteredLoans = remember(loans, statusFilter) {
        if (statusFilter == null) loans else loans.filter { it.status == statusFilter }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("lending_screen"),
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
                    text = "Money Lent & Outstanding",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Track loans given to friends and repayments received.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        title = "Total Lent",
                        amount = summary.moneyLent,
                        icon = Icons.Default.Handshake,
                        iconColor = LendingIndigo,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Repaid",
                        amount = summary.totalRepaid,
                        icon = Icons.Default.PriceCheck,
                        iconColor = IncomeGreen,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Outstanding",
                        amount = summary.totalOutstanding,
                        icon = Icons.Default.HourglassBottom,
                        iconColor = OutstandingAmber,
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
                            selected = statusFilter == null,
                            onClick = { statusFilter = null },
                            label = { Text("All (${loans.size})") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = statusFilter == LoanStatus.ACTIVE,
                            onClick = { statusFilter = if (statusFilter == LoanStatus.ACTIVE) null else LoanStatus.ACTIVE },
                            label = { Text("Active") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = statusFilter == LoanStatus.PARTIALLY_PAID,
                            onClick = { statusFilter = if (statusFilter == LoanStatus.PARTIALLY_PAID) null else LoanStatus.PARTIALLY_PAID },
                            label = { Text("Partial") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = statusFilter == LoanStatus.PAID,
                            onClick = { statusFilter = if (statusFilter == LoanStatus.PAID) null else LoanStatus.PAID },
                            label = { Text("Settled") }
                        )
                    }
                }

                Button(
                    onClick = onAddLoanClick,
                    modifier = Modifier.height(38.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = LendingIndigo),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Lend", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        // 3. Loans List
        if (filteredLoans.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Outlined.Handshake,
                    title = "No Loans Found",
                    description = "When you lend money to friends or contacts, record it here to track outstanding amounts separately from expenses.",
                    actionText = "Lend Money",
                    onActionClick = onAddLoanClick
                )
            }
        } else {
            items(filteredLoans, key = { it.id }) { loan ->
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    PersonLoanCard(
                        loan = loan,
                        onRepayClick = { onRepayLoanClick(loan) }
                    )
                }
            }
        }
    }
}
