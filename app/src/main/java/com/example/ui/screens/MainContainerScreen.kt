package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.entity.LoanEntity
import com.example.data.local.entity.TransactionEntity
import com.example.ui.components.SmartRulePromptDialog
import com.example.ui.components.OnboardingPreferencesDialog
import com.example.ui.theme.*
import com.example.features.auth.AuthViewModel
import com.example.features.dashboard.DashboardScreen
import com.example.features.settings.SettingsScreen
import com.example.features.sync.ExcelSyncScreen
import com.example.features.transactions.FinanceViewModel
import com.example.features.transactions.CompanyExpensePrompt
import com.example.features.transactions.AddTab
import com.example.features.transactions.TransactionsScreen
import com.example.features.transactions.AddTransactionSheet
import com.example.features.transactions.TransactionDetailSheet
import com.example.features.rules.RulesScreen
import com.example.features.official_expenses.OfficialExpensesScreen
import com.example.features.reports.ReportsScreen
import com.example.features.import.ImportStatementScreen
import com.example.features.lending.LendingScreen
import kotlinx.coroutines.launch

enum class MainTab(
    val title: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    val testTag: String
) {
    DASHBOARD("Dashboard", Icons.Outlined.Dashboard, Icons.Default.Dashboard, "tab_dashboard"),
    TRANSACTIONS("Statement", Icons.Outlined.ReceiptLong, Icons.Default.ReceiptLong, "tab_transactions"),
    REPORTS("Reports", Icons.Outlined.Assessment, Icons.Default.Assessment, "tab_reports"),
    SETTINGS("Settings", Icons.Outlined.Settings, Icons.Default.Settings, "tab_settings")
}



enum class SubScreen {
    NONE, RULES, COMPANY_EXPENSES, REPORTS, IMPORT_STATEMENT, LENDING, EXCEL_SYNC
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContainerScreen(
    viewModel: FinanceViewModel = viewModel(),
    authViewModel: AuthViewModel? = null
) {
    var currentTab by remember { mutableStateOf(MainTab.DASHBOARD) }
    var currentSubScreen by remember { mutableStateOf(SubScreen.NONE) }

    // Dialog & Sheet States
    var showAddSheet by remember { mutableStateOf(false) }
    var addSheetTab by remember { mutableStateOf(AddTab.EXPENSE) }
    var preselectedLoanForRepay by remember { mutableStateOf<LoanEntity?>(null) }
    var selectedTransactionForDetail by remember { mutableStateOf<TransactionEntity?>(null) }

    val smartRulePrompt by viewModel.smartRulePrompt.collectAsState()
    val uiMessage by viewModel.uiMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(uiMessage) {
        uiMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissMessage()
        }
    }

    val companyExpensePrompt by viewModel.companyExpensePrompt.collectAsState()

    companyExpensePrompt?.let { prompt ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissCompanyExpensePrompt() },
            title = {
                Text(
                    text = if (prompt.isReimbursement) "Detect Reimbursement" else "Detect Official Expense",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (prompt.isReimbursement) {
                        "We detected a potential reimbursement of ₹${prompt.amount} on ${prompt.date}: '${prompt.description}'. Would you like to mark the matching outstanding official expense as reimbursed?"
                    } else {
                        "We detected a transaction containing the 'company' keyword of ₹${prompt.amount} on ${prompt.date}: '${prompt.description}'. Would you like to log it as an Official Expense?"
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (prompt.isReimbursement) {
                            viewModel.acceptReimbursementPrompt(prompt)
                        } else {
                            viewModel.acceptCompanyExpensePrompt(prompt)
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissCompanyExpensePrompt() }) {
                    Text("Skip")
                }
            }
        )
    }

    val isBackNavEnabled = currentSubScreen != SubScreen.NONE || currentTab != MainTab.DASHBOARD || showAddSheet || selectedTransactionForDetail != null

    BackHandler(enabled = isBackNavEnabled) {
        when {
            selectedTransactionForDetail != null -> selectedTransactionForDetail = null
            showAddSheet -> {
                showAddSheet = false
                preselectedLoanForRepay = null
            }
            currentSubScreen != SubScreen.NONE -> currentSubScreen = SubScreen.NONE
            currentTab != MainTab.DASHBOARD -> currentTab = MainTab.DASHBOARD
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),

        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (currentSubScreen == SubScreen.NONE && (currentTab == MainTab.DASHBOARD || currentTab == MainTab.TRANSACTIONS)) {
                FloatingActionButton(
                    onClick = {
                        addSheetTab = AddTab.EXPENSE
                        preselectedLoanForRepay = null
                        showAddSheet = true
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Transaction")
                }
            }
        },
        topBar = {

            if (currentSubScreen != SubScreen.NONE) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = when (currentSubScreen) {
                                    SubScreen.RULES -> "Categorization"
                                    SubScreen.COMPANY_EXPENSES -> "Official Expenses"
                                    SubScreen.REPORTS -> "Reports"
                                    SubScreen.IMPORT_STATEMENT -> "Import Statement"
                                    SubScreen.LENDING -> "Loans & Lending"
                                    SubScreen.EXCEL_SYNC -> "Excel Two-Way Sync"
                                    SubScreen.NONE -> ""
                                },
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            val subtitle = when (currentSubScreen) {
                                SubScreen.RULES -> "Automatically categorize transactions using your rules."
                                SubScreen.COMPANY_EXPENSES -> "Spent for Company expense which can be claimed later as reimbursement"
                                SubScreen.REPORTS -> "Monthly trends & category spending breakdowns"
                                SubScreen.IMPORT_STATEMENT -> "Upload bank statements in Excel (.xlsx, .xls) or PDF (.pdf) formats"
                                SubScreen.LENDING -> "Track money you have lent and repayments received"
                                SubScreen.EXCEL_SYNC -> "Bidirectional synchronization with Microsoft Excel (.xlsx)"
                                SubScreen.NONE -> ""
                            }
                            if (subtitle.isNotEmpty()) {
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { currentSubScreen = SubScreen.NONE }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        },
        bottomBar = {
            if (currentSubScreen == SubScreen.NONE) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 0.dp
                ) {
                    MainTab.values().forEach { tab ->
                        val isSelected = currentTab == tab
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                currentTab = tab
                            },
                            icon = {
                                Icon(
                                    imageVector = if (isSelected) tab.selectedIcon else tab.icon,
                                    contentDescription = tab.title,
                                    modifier = Modifier.size(24.dp)
                                )
                            },

                            label = {
                                Text(
                                    text = tab.title,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 11.sp
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.testTag(tab.testTag)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (currentSubScreen != SubScreen.NONE) {
                when (currentSubScreen) {
                    SubScreen.RULES -> RulesScreen(viewModel = viewModel)
                    SubScreen.COMPANY_EXPENSES -> OfficialExpensesScreen(
                        viewModel = viewModel,
                        onAddExpenseClick = {
                            addSheetTab = AddTab.COMPANY_EXPENSE
                            showAddSheet = true
                        }
                    )
                    SubScreen.REPORTS -> ReportsScreen(viewModel = viewModel)
                    SubScreen.IMPORT_STATEMENT -> ImportStatementScreen(
                        viewModel = viewModel,
                        onImportSuccess = {
                            currentSubScreen = SubScreen.NONE
                            currentTab = MainTab.TRANSACTIONS
                        }
                    )
                    SubScreen.LENDING -> LendingScreen(
                        viewModel = viewModel,
                        onAddLoanClick = {
                            addSheetTab = AddTab.LEND_MONEY
                            preselectedLoanForRepay = null
                            showAddSheet = true
                        },
                        onRepayLoanClick = { loan ->
                            preselectedLoanForRepay = loan
                            addSheetTab = AddTab.LEND_MONEY
                            showAddSheet = true
                        }
                    )
                    SubScreen.EXCEL_SYNC -> ExcelSyncScreen(viewModel = viewModel)
                    SubScreen.NONE -> {}
                }
            } else {
                AnimatedContent(
                    targetState = currentTab,
                    transitionSpec = {
                        val initialIndex = initialState.ordinal
                        val targetIndex = targetState.ordinal
                        if (targetIndex > initialIndex) {
                            (slideInHorizontally(animationSpec = tween(400)) { width -> width } + fadeIn(animationSpec = tween(400))).togetherWith(
                                slideOutHorizontally(animationSpec = tween(400)) { width -> -width } + fadeOut(animationSpec = tween(400))
                            )
                        } else {
                            (slideInHorizontally(animationSpec = tween(400)) { width -> -width } + fadeIn(animationSpec = tween(400))).togetherWith(
                                slideOutHorizontally(animationSpec = tween(400)) { width -> width } + fadeOut(animationSpec = tween(400))
                            )
                        }.using(
                            SizeTransform(clip = false)
                        )
                    },
                    label = "TabTransition"
                ) { tab ->
                    when (tab) {
                        MainTab.DASHBOARD -> DashboardScreen(
                            viewModel = viewModel,
                            onNavigateToTransactions = { currentTab = MainTab.TRANSACTIONS },
                            onNavigateToImport = { currentSubScreen = SubScreen.IMPORT_STATEMENT },
                            onOpenAddSheet = {
                                addSheetTab = AddTab.EXPENSE
                                preselectedLoanForRepay = null
                                showAddSheet = true
                            },
                            onTransactionClick = { tx -> selectedTransactionForDetail = tx },
                            onNavigateToCompanyExpenses = { currentSubScreen = SubScreen.COMPANY_EXPENSES }
                        )

                        MainTab.TRANSACTIONS -> TransactionsScreen(
                            viewModel = viewModel,
                            onTransactionClick = { tx -> selectedTransactionForDetail = tx },
                            onNavigateToImport = { currentSubScreen = SubScreen.IMPORT_STATEMENT }
                        )

                        MainTab.SETTINGS -> SettingsScreen(
                            viewModel = viewModel,
                            authViewModel = authViewModel,
                            onNavigateToRules = { currentSubScreen = SubScreen.RULES },
                            onNavigateToCompanyExpenses = { currentSubScreen = SubScreen.COMPANY_EXPENSES },
                            onNavigateToReports = { currentTab = MainTab.REPORTS },
                            onNavigateToImport = { currentSubScreen = SubScreen.IMPORT_STATEMENT },
                            onNavigateToLending = { currentSubScreen = SubScreen.LENDING },
                            onNavigateToExcelSync = { currentSubScreen = SubScreen.EXCEL_SYNC }
                        )

                        MainTab.REPORTS -> ReportsScreen(viewModel = viewModel)
                    }
                }



            }

            // Sheets & Dialogs
            if (showAddSheet) {
                AddTransactionSheet(
                    viewModel = viewModel,
                    initialTab = addSheetTab,
                    preselectedLoan = preselectedLoanForRepay,
                    onDismiss = {
                        showAddSheet = false
                        preselectedLoanForRepay = null
                    }
                )
            }

            val allTransactionsList by viewModel.allTransactions.collectAsState()
            val activeDetailTx = remember(allTransactionsList, selectedTransactionForDetail) {
                allTransactionsList.find { it.id == selectedTransactionForDetail?.id } ?: selectedTransactionForDetail
            }

            if (activeDetailTx != null) {
                TransactionDetailSheet(
                    transaction = activeDetailTx,
                    viewModel = viewModel,
                    onDismiss = { selectedTransactionForDetail = null }
                )
            }

            smartRulePrompt?.let { prompt ->
                SmartRulePromptDialog(
                    prompt = prompt,
                    onAccept = { editedKeyword ->
                        viewModel.acceptSmartRule(prompt.copy(keyword = editedKeyword))
                    },
                    onDismiss = { viewModel.dismissSmartRule() }
                )
            }
        }
    }
}


