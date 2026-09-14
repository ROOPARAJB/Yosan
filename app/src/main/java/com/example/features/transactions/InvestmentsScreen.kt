package com.example.features.transactions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.TransactionEntity
import com.example.data.local.entity.TransactionType
import com.example.ui.components.EmptyState
import com.example.ui.components.MetricCard
import com.example.ui.components.TransactionItemCard
import com.example.ui.theme.*
import com.example.utils.CurrencyFormatter

@Composable
fun InvestmentsScreen(
    viewModel: FinanceViewModel,
    onAddInvestmentClick: () -> Unit,
    onTransactionClick: (TransactionEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val allTransactions by viewModel.allTransactions.collectAsState()

    val investTxs = remember(allTransactions) {
        allTransactions.filter {
            it.transactionType == TransactionType.INVESTMENT ||
                    it.categoryName.contains("Investment", ignoreCase = true) ||
                    it.categoryName.contains("Stock", ignoreCase = true) ||
                    it.categoryName.contains("Mutual", ignoreCase = true)
        }
    }

    val investTotal = remember(investTxs) {
        investTxs.sumOf { if (it.debitAmount > 0.0) it.debitAmount else it.amount }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("investments_screen"),
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
                    text = "Investments & Assets",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Track your capital allocations, mutual funds, stocks, and wealth assets.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        title = "Total Invested Assets",
                        amount = investTotal,
                        icon = Icons.Default.TrendingUp,
                        iconColor = Color(0xFF10B981),
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Allocations",
                        amount = investTxs.size.toDouble(),
                        icon = Icons.Default.PieChart,
                        iconColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 2. Action Bar
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Asset Allocations (${investTxs.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                Button(
                    onClick = onAddInvestmentClick,
                    modifier = Modifier.height(38.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Invest", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        // 3. Transactions List
        if (investTxs.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Outlined.TrendingUp,
                    title = "No Investments Logged",
                    description = "Record stocks, mutual funds, SIPs, gold, or deposits to keep track of asset investments.",
                    actionText = "Log Investment",
                    onActionClick = onAddInvestmentClick
                )
            }
        } else {
            items(investTxs, key = { it.id }) { tx ->
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    TransactionItemCard(
                        transaction = tx,
                        onClick = { onTransactionClick(tx) }
                    )
                }
            }
        }
    }
}
