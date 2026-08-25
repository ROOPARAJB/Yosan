package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.LoanEntity
import com.example.data.local.entity.TransactionEntity
import com.example.features.reports.FinancialInsight
import com.example.features.reports.InsightType
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.features.transactions.FinanceViewModel
import com.example.ui.models.DashboardCardConfigManager
import com.example.ui.models.DashboardCardType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: FinanceViewModel,
    onNavigateToTransactions: () -> Unit,
    onNavigateToImport: () -> Unit,
    onOpenAddSheet: () -> Unit,
    onTransactionClick: (TransactionEntity) -> Unit,
    onNavigateToCompanyExpenses: () -> Unit,
    modifier: Modifier = Modifier
) {
    val summary by viewModel.dashboardSummary.collectAsState()
    val allTransactions by viewModel.allTransactions.collectAsState()
    val loans by viewModel.loans.collectAsState()
    val categoryBreakdown by viewModel.categoryBreakdown.collectAsState()
    val monthlyTrends by viewModel.monthlyTrends.collectAsState()
    val insights by viewModel.financialInsights.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val companyExpenses by viewModel.companyExpenses.collectAsState()
    val isRevealed by viewModel.isAmountTemporarilyRevealed.collectAsState()
    val privacyEnabled = userProfile?.isPrivacyBlurEnabled ?: true

    val recentTransactions = remember(allTransactions) { allTransactions.take(5) }
    val activeLoans = remember(loans) { loans.filter { it.remainingAmount > 0 }.take(4) }

    val cardsConfig = remember(userProfile?.dashboardCardsConfig) {
        DashboardCardConfigManager.parse(userProfile?.dashboardCardsConfig)
    }

    val greeting = remember {
        val calendar = java.util.Calendar.getInstance()
        when (calendar.get(java.util.Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Good Morning,"
            in 12..16 -> "Good Afternoon,"
            in 17..21 -> "Good Evening,"
            else -> "Good Night,"
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("dashboard_screen"),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        // Top Header & User Greeting
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = greeting,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = userProfile?.name ?: "Personal Finance",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
        }

        // If no transactions exist, only show the import suggestion box
        if (allTransactions.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(20.dp)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.RocketLaunch,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Get Started with Yosan",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Import your Excel bank statements or add transactions manually to populate your financial trends, category analytics, and smart insights.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onNavigateToImport,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Import Excel", maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = onOpenAddSheet,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add Record", maxLines = 1)
                            }
                        }
                    }
                }
            }
        } else {
            // Dynamic Dashboard Cards rendering based on user configuration when data exists
            cardsConfig.forEach { cardItem ->
                if (cardItem.isEnabled) {
                    when (cardItem.type) {
                        DashboardCardType.CURRENT_BALANCE -> {
                            item {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp)
                                        .clip(RoundedCornerShape(28.dp))
                                        .testTag("current_balance_card"),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                Brush.verticalGradient(
                                                    colors = listOf(
                                                        HeroContainerBlue,
                                                        Color(0xFFE2EDFF)
                                                    )
                                                )
                                            )
                                            .padding(24.dp)
                                    ) {
                                        Column {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(28.dp)
                                                            .clip(CircleShape)
                                                            .background(HeroOnContainerNavy.copy(alpha = 0.1f)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.AccountBalanceWallet,
                                                            contentDescription = "Balance",
                                                            tint = HeroOnContainerNavy,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                    Text(
                                                        text = "Current Balance",
                                                        style = MaterialTheme.typography.labelLarge,
                                                        color = HeroOnContainerNavy.copy(alpha = 0.8f),
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }

                                                if (privacyEnabled) {
                                                    IconButton(
                                                        onClick = { viewModel.toggleAmountReveal() },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = if (isRevealed) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                            contentDescription = "Toggle Balance Privacy",
                                                            tint = HeroOnContainerNavy.copy(alpha = 0.8f),
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(12.dp))
                                            PrivacyAmountText(
                                                amount = summary.currentBalance,
                                                style = MaterialTheme.typography.displayLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = HeroOnContainerNavy,
                                                isRevealed = isRevealed,
                                                isPrivacyEnabled = privacyEnabled,
                                                onTap = { viewModel.revealAmountsTemporarily() }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        DashboardCardType.OFFICIAL_EXPENSES -> {
                            item {
                                val outstandingClaims = remember(companyExpenses) { companyExpenses.filter { !it.isReimbursed }.sumOf { it.amount } }
                                val reimbursedClaims = remember(companyExpenses) { companyExpenses.filter { it.isReimbursed }.sumOf { it.amount } }
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp)
                                        .clip(RoundedCornerShape(20.dp)),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.BusinessCenter,
                                                    contentDescription = "Official Expenses",
                                                    tint = LendingIndigo,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "Official Expenses",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            IconButton(onClick = onNavigateToCompanyExpenses) {
                                                Icon(
                                                    imageVector = Icons.Default.ArrowForward,
                                                    contentDescription = "Manage claims",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column {
                                                Text(
                                                    text = "Outstanding Claims",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                PrivacyAmountText(
                                                    amount = outstandingClaims,
                                                    style = MaterialTheme.typography.titleLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    color = OutstandingAmber,
                                                    isRevealed = isRevealed,
                                                    isPrivacyEnabled = privacyEnabled,
                                                    onTap = { viewModel.revealAmountsTemporarily() }
                                                )
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text(
                                                    text = "Reimbursed",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                PrivacyAmountText(
                                                    amount = reimbursedClaims,
                                                    style = MaterialTheme.typography.titleLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    color = IncomeGreen,
                                                    isRevealed = isRevealed,
                                                    isPrivacyEnabled = privacyEnabled,
                                                    onTap = { viewModel.revealAmountsTemporarily() }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        DashboardCardType.SPENDING_CATEGORY -> {
                            if (categoryBreakdown.isNotEmpty()) {
                                item {
                                    DonutCategoryChart(
                                        items = categoryBreakdown,
                                        onCategoryClick = { catName ->
                                            viewModel.setCategoryFilter(catName)
                                            onNavigateToTransactions()
                                        },
                                        isRevealed = isRevealed,
                                        isPrivacyEnabled = privacyEnabled,
                                        onToggleReveal = { viewModel.revealAmountsTemporarily() },
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        DashboardCardType.MONTHLY_PERFORMANCE -> {
                            if (monthlyTrends.isNotEmpty()) {
                                item {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 6.dp)
                                            .clip(RoundedCornerShape(16.dp)),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text(
                                                text = "Monthly Performance Breakdown",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))

                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Month",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.weight(1.2f)
                                                )
                                                Text(
                                                    text = "Income",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = IncomeGreen,
                                                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                                    modifier = Modifier.weight(1.5f)
                                                )
                                                Text(
                                                    text = "Expense",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = ExpenseRed,
                                                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                                    modifier = Modifier.weight(1.5f)
                                                )
                                            }

                                            HorizontalDivider(
                                                modifier = Modifier.padding(vertical = 6.dp),
                                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                            )

                                            monthlyTrends.forEach { trend ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 6.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = trend.monthLabel,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.Medium,
                                                        modifier = Modifier.weight(1.2f)
                                                    )
                                                    Box(
                                                        modifier = Modifier.weight(1.5f),
                                                        contentAlignment = Alignment.CenterEnd
                                                    ) {
                                                        PrivacyAmountText(
                                                            amount = trend.income,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = IncomeGreen,
                                                            isRevealed = isRevealed,
                                                            isPrivacyEnabled = privacyEnabled,
                                                            onTap = { viewModel.revealAmountsTemporarily() }
                                                        )
                                                    }
                                                    Box(
                                                        modifier = Modifier.weight(1.5f),
                                                        contentAlignment = Alignment.CenterEnd
                                                    ) {
                                                        PrivacyAmountText(
                                                            amount = trend.expense,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = ExpenseRed,
                                                            isRevealed = isRevealed,
                                                            isPrivacyEnabled = privacyEnabled,
                                                            onTap = { viewModel.revealAmountsTemporarily() }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        DashboardCardType.FINANCIAL_INSIGHTS -> {
                            if (insights.isNotEmpty()) {
                                item {
                                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                                        SectionHeader(title = "Smart Insights")
                                        insights.take(2).forEach { insight ->
                                            InsightCard(insight = insight, modifier = Modifier.padding(vertical = 4.dp))
                                        }
                                    }
                                }
                            }
                        }

                        DashboardCardType.ACTIVE_LOANS -> {
                            if (activeLoans.isNotEmpty()) {
                                item {
                                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                                        SectionHeader(
                                            title = "Active Lending & Loans",
                                            actionText = if (loans.size > 4) "View All" else null,
                                            onActionClick = onNavigateToTransactions
                                        )
                                        activeLoans.forEach { loan ->
                                            PersonLoanCard(
                                                loan = loan,
                                                onRepayClick = { },
                                                modifier = Modifier.padding(vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        DashboardCardType.RECENT_TRANSACTIONS -> {
                            if (recentTransactions.isNotEmpty()) {
                                item {
                                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                                        SectionHeader(
                                            title = "Recent Activity",
                                            actionText = "View All",
                                            onActionClick = onNavigateToTransactions
                                        )
                                        recentTransactions.forEach { tx ->
                                            TransactionItemCard(
                                                transaction = tx,
                                                onClick = { onTransactionClick(tx) },
                                                isRevealed = isRevealed,
                                                isPrivacyEnabled = privacyEnabled,
                                                onToggleReveal = { viewModel.revealAmountsTemporarily() },
                                                modifier = Modifier.padding(vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ActionChip(
    label: String,
    icon: ImageVector,
    iconColor: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() },
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 1.dp,
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun InsightCard(
    insight: FinancialInsight,
    modifier: Modifier = Modifier
) {
    val (bgColor, iconColor, icon) = when (insight.type) {
        InsightType.POSITIVE -> Triple(Color(0xFFECFDF5), IncomeGreen, Icons.Default.CheckCircle)
        InsightType.WARNING -> Triple(Color(0xFFFEF3C7), OutstandingAmber, Icons.Default.Warning)
        InsightType.INFO -> Triple(Color(0xFFEEF2FF), LendingIndigo, Icons.Default.Info)
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 1.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = insight.title,
                    tint = iconColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = insight.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = insight.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
