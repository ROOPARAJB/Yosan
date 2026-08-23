package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun GuideScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Yosan User Walkthrough",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Follow this end-to-end guide to configure accounts, import statements, set up auto-rules, and generate reports.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Step 1
        WalkthroughStepCard(
            stepNumber = "1",
            title = "Configure Bank Accounts",
            icon = Icons.Default.AccountBalance,
            iconColor = MaterialTheme.colorScheme.primary,
            steps = listOf(
                "Navigate to Settings (More tab) → tap 'Bank Accounts'.",
                "Tap '+ Add Account / Wallet' to record your banking institutions (e.g. HDFC, SBI, ICICI, GPay).",
                "Select account type (Savings, Current, Wallet, Credit Card) and enter the institution name.",
                "Accounts configured here will be used for auto-detecting and mapping imported statements."
            )
        )

        // Step 2
        WalkthroughStepCard(
            stepNumber = "2",
            title = "Import Excel Bank Statements",
            icon = Icons.Default.FileUpload,
            iconColor = IncomeGreen,
            steps = listOf(
                "Go to the Statements tab and tap the 'Import' button on the top right.",
                "Tap 'Select Excel File (.xlsx, .xls)' and pick your downloaded bank statement.",
                "Review the Validation Summary (Total Rows, Valid Transactions, and Duplicates).",
                "Check the 'Assign Statement to Bank Account' card: Yosan automatically pre-selects the matching bank name.",
                "Tap 'Confirm & Import' to save transactions with verified running balance computations."
            )
        )

        // Step 3
        WalkthroughStepCard(
            stepNumber = "3",
            title = "Auto-Categorization & Smart Rules",
            icon = Icons.Default.AutoAwesome,
            iconColor = LendingIndigo,
            steps = listOf(
                "Open Settings → tap 'Categorization' → switch to the 'Auto-Rules' tab.",
                "Tap '+ Add Rule': as you type keywords, real-time suggestions from your imported statements appear as tappable chips.",
                "Select the target Category and save the rule.",
                "When viewing any transaction in Statement Records, tap 'Categorisation' to change categories — Yosan will only prompt to create a rule if one does not already exist."
            )
        )

        // Step 4
        WalkthroughStepCard(
            stepNumber = "4",
            title = "Official Corporate Expenses",
            icon = Icons.Default.BusinessCenter,
            iconColor = OutstandingAmber,
            steps = listOf(
                "Any transaction description containing the keyword 'company' is automatically categorized under 'Official Expense'.",
                "During bulk statement imports, a single one-click prompt lets you 'Log All' corporate expenses at once.",
                "View and manage corporate claims from the Dashboard or Settings → long-press items to select, delete, or mark as reimbursed."
            )
        )

        // Step 5
        WalkthroughStepCard(
            stepNumber = "5",
            title = "Dashboard Drill-Down & Reports",
            icon = Icons.Default.Assessment,
            iconColor = ExpenseRed,
            steps = listOf(
                "On the Dashboard, review your real-time Current Balance synced from your bank's latest statement records.",
                "In 'Spending by Category', tap any category in the chart legend to jump straight into filtered statement records for that category.",
                "Navigate to Reports to view monthly trends, spending distribution, and tap 'View PDF' or 'Share PDF' to export clean financial statements."
            )
        )

        // Step 6
        WalkthroughStepCard(
            stepNumber = "6",
            title = "Theme & Data Management",
            icon = Icons.Default.Settings,
            iconColor = MaterialTheme.colorScheme.secondary,
            steps = listOf(
                "Go to Settings → Appearance to toggle between Dark Mode and Light Mode anytime.",
                "Under Data & Privacy, use the two-step verification wizard if you ever need to reset or delete local data."
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "App Creators",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Designed & Developed by GokulRaj & Rooparaj",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Spacer(modifier = Modifier.height(30.dp))
    }
}

@Composable
fun WalkthroughStepCard(
    stepNumber: String,
    title: String,
    icon: ImageVector,
    iconColor: Color,
    steps: List<String>
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = iconColor.copy(alpha = 0.12f),
                    shape = CircleShape,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = stepNumber,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = iconColor
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            steps.forEachIndexed { index, stepText ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = iconColor,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = stepText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}