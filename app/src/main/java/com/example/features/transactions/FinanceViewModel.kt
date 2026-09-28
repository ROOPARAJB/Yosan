package com.example.features.transactions

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.entity.*
import com.example.features.auth.data.AuthRepository
import com.example.features.auth.GetUserProfileUseCase
import com.example.features.auth.UpdateUserProfileUseCase
import com.example.features.import.ImportPreviewResult
import com.example.features.lending.GetLoansUseCase
import com.example.features.lending.data.LendingRepository
import com.example.features.reports.*
import com.example.features.reports.data.ReportsRepository
import com.example.features.rules.CategorizationEngine
import com.example.features.rules.GetRulesUseCase
import com.example.features.rules.data.RulesRepository
import com.example.features.sync.ExcelTransactionRow
import com.example.features.sync.SyncConflict
import com.example.features.sync.SyncResult
import com.example.features.transactions.coordinator.*
import com.example.features.transactions.data.TransactionRepository
import com.example.repository.FinanceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

data class UndoSnackbarData(
    val message: String,
    val actionId: String
)

data class SmartRulePrompt(
    val keyword: String,
    val categoryId: Long?,
    val categoryName: String,
    val transactionType: TransactionType,
    val transactionDescription: String = ""
)

data class CompanyExpensePrompt(
    val transactionId: Long,
    val description: String,
    val amount: Double,
    val date: String,
    val isReimbursement: Boolean,
    val matchingExpenseId: Long? = null
)

data class AdvanceSummary(
    val advanceId: String,
    val inflowTransaction: TransactionEntity?,
    val totalReceived: Double,
    val totalSpent: Double,
    val remainingBalance: Double,
    val linkedTransactions: List<TransactionEntity>,
    val linkedCompanyExpenses: List<CompanyExpenseEntity>
) {
    val isFullySpent: Boolean get() = remainingBalance <= 0.0 && totalSpent > 0.0
    val progress: Float get() = if (totalReceived > 0) (totalSpent / totalReceived).toFloat().coerceIn(0f, 1f) else 0f
}

private data class FilterParams(
    val txs: List<TransactionEntity>,
    val query: String,
    val typeFilter: TransactionType?,
    val catFilter: String?,
    val accFilter: Long?
)

class FinanceViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application, viewModelScope)
    val repository = FinanceRepository(database)

    private val transactionRepository = TransactionRepository(database)
    private val lendingRepository = LendingRepository(database)
    private val rulesRepository = RulesRepository(database)
    private val reportsRepository = ReportsRepository(database)
    private val authRepository = AuthRepository(database)

    // Base Use Cases
    private val getTransactionsUseCase = GetTransactionsUseCase(transactionRepository)
    private val getAccountsUseCase = GetAccountsUseCase(transactionRepository)
    private val getCategoriesUseCase = GetCategoriesUseCase(transactionRepository)
    private val getLoansUseCase = GetLoansUseCase(lendingRepository)
    private val getRulesUseCase = GetRulesUseCase(rulesRepository)
    private val getCompanyExpensesUseCase = GetCompanyExpensesUseCase(reportsRepository)
    private val getUserProfileUseCase = GetUserProfileUseCase(authRepository)

    // Base Flows from DB
    val userProfile: StateFlow<UserProfileEntity?> = getUserProfileUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val accounts: StateFlow<List<AccountEntity>> = getAccountsUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTransactions: StateFlow<List<TransactionEntity>> = getTransactionsUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI Message
    private val _uiMessage = MutableStateFlow<String?>(null)
    val uiMessage: StateFlow<String?> = _uiMessage.asStateFlow()

    fun showMessage(msg: String) { _uiMessage.value = msg }
    fun dismissMessage() { _uiMessage.value = null }

    // Privacy Mode (Hide Financial Values)
    private val _isAmountTemporarilyRevealed = MutableStateFlow(false)
    val isAmountTemporarilyRevealed = _isAmountTemporarilyRevealed.asStateFlow()
    private var revealJob: Job? = null

    fun revealAmountsTemporarily() {
        val timeout = userProfile.value?.blurTimeoutSeconds ?: 5
        revealJob?.cancel()
        _isAmountTemporarilyRevealed.value = true
        revealJob = viewModelScope.launch {
            delay(timeout * 1000L)
            _isAmountTemporarilyRevealed.value = false
        }
    }

    fun toggleAmountReveal() {
        if (_isAmountTemporarilyRevealed.value) {
            revealJob?.cancel()
            _isAmountTemporarilyRevealed.value = false
        } else {
            revealAmountsTemporarily()
        }
    }

    fun updatePrivacyBlurPreference(enabled: Boolean, timeoutSeconds: Int) {
        viewModelScope.launch {
            database.userProfileDao().updatePrivacyBlurPreference(enabled, timeoutSeconds)
        }
    }

    fun completeOnboarding(isDarkMode: Boolean, privacyBlurEnabled: Boolean, timeoutSeconds: Int) {
        viewModelScope.launch {
            val current = userProfile.value ?: UserProfileEntity(id = 1)
            val updated = current.copy(
                isDarkMode = isDarkMode,
                isPrivacyBlurEnabled = privacyBlurEnabled,
                blurTimeoutSeconds = timeoutSeconds,
                isOnboardingCompleted = true
            )
            database.userProfileDao().insertOrUpdateProfile(updated)
            database.userProfileDao().updateOnboardingCompleted(true)
        }
    }

    fun resetOnboarding() {
        viewModelScope.launch {
            database.userProfileDao().updateOnboardingCompleted(false)
        }
    }

    fun updateUserProfile(name: String, currencySymbol: String) {
        viewModelScope.launch {
            val current = userProfile.value ?: UserProfileEntity(id = 1, name = name, email = "", currencySymbol = currencySymbol)
            repository.updateProfile(current.copy(name = name, currencySymbol = currencySymbol))
        }
    }

    fun toggleDarkMode(isDark: Boolean) {
        viewModelScope.launch {
            repository.updateThemePreference(isDark)
        }
    }

    fun updateDashboardCardsConfig(config: String) {
        viewModelScope.launch {
            try {
                repository.updateDashboardCardsConfig(config)
            } catch (e: Exception) {
                if (com.example.BuildConfig.DEBUG) {
                    android.util.Log.e("FinanceViewModel", "Failed to update dashboard config", e)
                }
            }
        }
    }

    // Domain Coordinators
    private val undoCoordinator: UndoCoordinator = UndoCoordinator(
        database = database,
        repository = repository,
        scope = viewModelScope,
        showMessage = ::showMessage
    )

    private val categoryRuleCoordinator: CategoryRuleCoordinator = CategoryRuleCoordinator(
        database = database,
        repository = repository,
        scope = viewModelScope,
        getCategoriesUseCase = getCategoriesUseCase,
        getRulesUseCase = getRulesUseCase,
        undoCoordinator = undoCoordinator,
        showMessage = ::showMessage
    )

    private val companyExpenseCoordinator: CompanyExpenseCoordinator = CompanyExpenseCoordinator(
        database = database,
        repository = repository,
        scope = viewModelScope,
        getCompanyExpensesUseCase = getCompanyExpensesUseCase,
        showMessage = ::showMessage
    )

    private val advanceCoordinator: AdvanceCoordinator = AdvanceCoordinator(
        database = database,
        repository = repository,
        scope = viewModelScope,
        allTransactionsFlow = allTransactions,
        companyExpensesFlow = companyExpenseCoordinator.companyExpenses,
        showMessage = ::showMessage
    )

    private val lendingCoordinator: LendingCoordinator = LendingCoordinator(
        database = database,
        repository = repository,
        scope = viewModelScope,
        getLoansUseCase = getLoansUseCase,
        allTransactionsFlow = allTransactions,
        accountsFlow = accounts,
        showMessage = ::showMessage
    )

    private val statementImportCoordinator: StatementImportCoordinator = StatementImportCoordinator(
        database = database,
        repository = repository,
        transactionRepository = transactionRepository,
        scope = viewModelScope,
        allTransactionsFlow = allTransactions,
        showMessage = ::showMessage,
        onImportSuccess = {
            companyExpenseCoordinator.scanForCompanyExpenses()
        }
    )

    private val transactionCoordinator: TransactionCoordinator = TransactionCoordinator(
        database = database,
        repository = repository,
        transactionRepository = transactionRepository,
        scope = viewModelScope,
        undoCoordinator = undoCoordinator,
        allTransactionsFlow = allTransactions,
        accountsFlow = accounts,
        showMessage = ::showMessage,
        onTransactionMutated = { companyExpenseCoordinator.scanForCompanyExpenses() },
        onSmartRuleSuggested = { prompt -> categoryRuleCoordinator.setSmartRulePrompt(prompt) },
        onAllDataCleared = { onComplete ->
            statementImportCoordinator.clearImportPreview()
            _isAmountTemporarilyRevealed.value = false
            companyExpenseCoordinator.clearPrompts()
            categoryRuleCoordinator.dismissSmartRule()
            clearFilters()
            onComplete?.invoke()
        }
    )

    private val excelSyncCoordinator: ExcelSyncCoordinator = ExcelSyncCoordinator(
        database = database,
        transactionRepository = transactionRepository,
        scope = viewModelScope,
        showMessage = ::showMessage,
        onSyncFinished = { companyExpenseCoordinator.scanForCompanyExpenses() }
    )

    // Exposed Flows from Coordinators
    val categories: StateFlow<List<CategoryEntity>> = categoryRuleCoordinator.categories
    val rules: StateFlow<List<CategorizationRuleEntity>> = categoryRuleCoordinator.rules
    val smartRulePrompt: StateFlow<SmartRulePrompt?> = categoryRuleCoordinator.smartRulePrompt

    val loans: StateFlow<List<LoanEntity>> = lendingCoordinator.loans
    val personLendingSummaries: StateFlow<List<PersonLendingSummary>> = lendingCoordinator.personLendingSummaries

    val companyExpenses: StateFlow<List<CompanyExpenseEntity>> = companyExpenseCoordinator.companyExpenses
    val companyExpensePrompt: StateFlow<CompanyExpensePrompt?> = companyExpenseCoordinator.companyExpensePrompt
    val pendingCompanyExpenses: StateFlow<List<CompanyExpensePrompt>> = companyExpenseCoordinator.pendingCompanyExpenses

    val advanceSummaries: StateFlow<List<AdvanceSummary>> = advanceCoordinator.advanceSummaries

    val undoHistory: StateFlow<List<UndoHistoryEntity>> = undoCoordinator.undoHistory
    val undoSnackbarEvent: SharedFlow<UndoSnackbarData> = undoCoordinator.undoSnackbarEvent

    val importPreview: StateFlow<ImportPreviewResult?> = statementImportCoordinator.importPreview
    val isImporting: StateFlow<Boolean> = statementImportCoordinator.isImporting

    val selectedExcelUri: StateFlow<String?> = excelSyncCoordinator.selectedExcelUri
    val selectedExcelFileName: StateFlow<String?> = excelSyncCoordinator.selectedExcelFileName
    val lastSyncTime: StateFlow<Long?> = excelSyncCoordinator.lastSyncTime
    val syncStatus: StateFlow<String> = excelSyncCoordinator.syncStatus
    val isSyncing: StateFlow<Boolean> = excelSyncCoordinator.isSyncing
    val lastSyncResult: StateFlow<SyncResult?> = excelSyncCoordinator.lastSyncResult
    val activeConflicts: StateFlow<List<SyncConflict>> = excelSyncCoordinator.activeConflicts
    val excelPreviewRows: StateFlow<List<ExcelTransactionRow>> = excelSyncCoordinator.excelPreviewRows

    // Derived Financial Analytics
    val dashboardSummary: StateFlow<DashboardSummary> = combine(
        allTransactions,
        loans,
        companyExpenses,
        accounts
    ) { txs, lns, compExp, accs ->
        FinancialCalculationService.calculateSummary(txs, lns, compExp, accs)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardSummary())

    val categoryBreakdown: StateFlow<List<CategoryExpenseItem>> = combine(
        allTransactions,
        categories
    ) { txs, cats ->
        FinancialCalculationService.calculateCategoryExpenseBreakdown(txs, cats)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categoryIncomeBreakdown: StateFlow<List<CategoryExpenseItem>> = combine(
        allTransactions,
        categories
    ) { txs, cats ->
        FinancialCalculationService.calculateCategoryIncomeBreakdown(txs, cats)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val monthlyTrends: StateFlow<List<MonthlyTrendItem>> = allTransactions.map { txs ->
        FinancialCalculationService.calculateMonthlyTrends(txs)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val financialInsights: StateFlow<List<FinancialInsight>> = combine(
        dashboardSummary,
        categoryBreakdown,
        monthlyTrends
    ) { summary, breakdown, trends ->
        FinancialCalculationService.generateInsights(summary, breakdown, trends)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI Search & Filters
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedTypeFilter = MutableStateFlow<TransactionType?>(null)
    val selectedTypeFilter: StateFlow<TransactionType?> = _selectedTypeFilter.asStateFlow()

    private val _selectedCategoryFilter = MutableStateFlow<String?>(null)
    val selectedCategoryFilter: StateFlow<String?> = _selectedCategoryFilter.asStateFlow()

    private val _selectedAccountFilter = MutableStateFlow<Long?>(null)
    val selectedAccountFilter: StateFlow<Long?> = _selectedAccountFilter.asStateFlow()

    private val _selectedMonthFilter = MutableStateFlow<String?>(null)
    val selectedMonthFilter: StateFlow<String?> = _selectedMonthFilter.asStateFlow()

    val filteredTransactions: StateFlow<List<TransactionEntity>> = combine(
        allTransactions,
        searchQuery,
        selectedTypeFilter,
        selectedCategoryFilter,
        selectedAccountFilter
    ) { txs, query, typeFilter, catFilter, accFilter ->
        FilterParams(txs, query, typeFilter, catFilter, accFilter)
    }.combine(selectedMonthFilter) { params, monthFilter ->
        params.txs.filter { tx ->
            val matchesQuery = params.query.isBlank() ||
                    tx.description.contains(params.query, ignoreCase = true) ||
                    tx.categoryName.contains(params.query, ignoreCase = true) ||
                    tx.referenceNumber.contains(params.query, ignoreCase = true) ||
                    (tx.advanceId != null && tx.advanceId.contains(params.query, ignoreCase = true))
            val matchesType = params.typeFilter == null || tx.transactionType == params.typeFilter
            val matchesCat = params.catFilter == null || tx.categoryName.equals(params.catFilter, ignoreCase = true)
            val matchesAcc = params.accFilter == null || tx.accountId == params.accFilter
            val matchesMonth = monthFilter == null || tx.transactionDate.startsWith(monthFilter)
            matchesQuery && matchesType && matchesCat && matchesAcc && matchesMonth
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun setTypeFilter(type: TransactionType?) { _selectedTypeFilter.value = type }
    fun setCategoryFilter(category: String?) { _selectedCategoryFilter.value = category }
    fun setAccountFilter(accountId: Long?) { _selectedAccountFilter.value = accountId }
    fun setMonthFilter(month: String?) { _selectedMonthFilter.value = month }
    fun clearFilters() {
        _searchQuery.value = ""
        _selectedTypeFilter.value = null
        _selectedCategoryFilter.value = null
        _selectedAccountFilter.value = null
        _selectedMonthFilter.value = null
    }

    // Consolidated Sequential Initialization
    init {
        viewModelScope.launch {
            try {
                excelSyncCoordinator.loadSyncMetadata()

                val existingCats = database.categoryDao().getAllCategoriesList()
                if (existingCats.isEmpty()) {
                    database.categoryDao().insertCategories(CategoryEntity.DEFAULT_CATEGORIES)
                } else {
                    val seenNames = mutableSetOf<String>()
                    for (cat in existingCats) {
                        val key = "${cat.name.trim().lowercase()}_${cat.type}"
                        if (!seenNames.add(key)) {
                            database.categoryDao().deleteCategory(cat.id)
                        }
                    }
                    if (database.categoryDao().getCategoryByName("EMI") == null) {
                        database.categoryDao().insertCategory(
                            CategoryEntity(name = "EMI", type = CategoryType.EXPENSE, colorHex = "#E11D48", iconName = "receipt_long", isSystem = true)
                        )
                    }
                    if (database.categoryDao().getCategoryByName("Advance") == null) {
                        database.categoryDao().insertCategory(
                            CategoryEntity(name = "Advance", type = CategoryType.INCOME, colorHex = "#0284C7", iconName = "account_balance_wallet", isSystem = true)
                        )
                    }
                }

                val activeRules = database.categorizationRuleDao().getActiveRulesList()
                val txs = database.transactionDao().getAllTransactionsList()
                val uncategorized = txs.filter { it.categoryName.equals("Uncategorized", ignoreCase = true) }
                uncategorized.forEach { tx ->
                    val result = CategorizationEngine.categorize(tx.description, activeRules, tx.creditAmount > 0.0)
                    if (result.matchedRule != null) {
                        database.transactionDao().updateTransactionCategory(tx.id, result.categoryId, result.categoryName, result.transactionType)
                    }
                }
            } catch (_: Exception) {}

            companyExpenseCoordinator.scanForCompanyExpenses()
        }
    }

    // Delegated Transactions Methods
    fun addManualTransaction(
        date: String, description: String, amount: Double, type: TransactionType,
        categoryId: Long?, categoryName: String, accountId: Long, notes: String, advanceId: String? = null
    ) = transactionCoordinator.addManualTransaction(date, description, amount, type, categoryId, categoryName, accountId, notes, advanceId)

    fun updateTransactionCategory(tx: TransactionEntity, newCategory: CategoryEntity) =
        transactionCoordinator.updateTransactionCategory(tx, newCategory)

    fun updateTransactionType(tx: TransactionEntity, newType: TransactionType) =
        transactionCoordinator.updateTransactionType(tx, newType)

    fun updateTransactionsCategory(txIds: List<Long>, newCategory: CategoryEntity) =
        transactionCoordinator.updateTransactionsCategory(txIds, newCategory)

    fun updateTransactionDescription(txId: Long, newDescription: String) =
        transactionCoordinator.updateTransactionDescription(txId, newDescription)

    fun addTransferTransaction(fromAccountId: Long, toAccountId: Long, amount: Double, date: String, notes: String = "") =
        transactionCoordinator.addTransferTransaction(fromAccountId, toAccountId, amount, date, notes)

    fun deleteTransaction(id: Long) = transactionCoordinator.deleteTransaction(id)
    fun deleteTransactions(ids: List<Long>) = transactionCoordinator.deleteTransactions(ids)
    fun deleteAllTransactions() = transactionCoordinator.deleteAllTransactions()
    fun deleteInvestmentSet(categoryName: String, deleteTxs: Boolean = false) = transactionCoordinator.deleteInvestmentSet(categoryName, deleteTxs)
    fun clearAllUserData(onComplete: (() -> Unit)? = null) = transactionCoordinator.clearAllUserData(onComplete)
    fun recalculateAllAccountBalances() {
        transactionCoordinator.recalculateAllAccountBalances()
    }

    // Delegated Account Methods
    fun addAccount(account: AccountEntity) = transactionCoordinator.addAccount(account)
    fun addAccount(name: String, bank: String, maskedNumber: String, type: AccountType, openingBalance: Double) =
        transactionCoordinator.addAccount(name, bank, maskedNumber, type, openingBalance)
    fun updateAccount(account: AccountEntity) = transactionCoordinator.updateAccount(account)
    fun deleteAccount(id: Long) = transactionCoordinator.deleteAccount(id)

    // Delegated Local Backup Methods
    fun createBackup(context: Context, onDone: (File?) -> Unit) = transactionCoordinator.createBackup(context, onDone)
    fun saveBackupToStorageUri(context: Context, uri: Uri, onDone: (Boolean) -> Unit) = transactionCoordinator.saveBackupToStorageUri(context, uri, onDone)
    fun saveBackupToDownloads(context: Context, onDone: (Boolean) -> Unit) = transactionCoordinator.saveBackupToDownloads(context, onDone)
    fun restoreBackup(context: Context, uri: Uri, onDone: (Boolean) -> Unit) = transactionCoordinator.restoreBackup(context, uri, onDone)

    // Delegated Advance Methods
    fun generateNextAdvanceId(): String = advanceCoordinator.generateNextAdvanceId()
    fun isAdvanceIdUnique(id: String, excludeTxId: Long? = null): Boolean = advanceCoordinator.isAdvanceIdUnique(id, excludeTxId)
    fun updateTransactionAdvanceId(txId: Long, advanceId: String?) = advanceCoordinator.updateTransactionAdvanceId(txId, advanceId)
    fun linkTransactionToAdvance(txId: Long, advanceId: String?) = advanceCoordinator.updateTransactionAdvanceId(txId, advanceId)
    fun deleteAdvanceSet(advanceId: String, deleteInflowTx: Boolean = false) = advanceCoordinator.deleteAdvanceSet(advanceId, deleteInflowTx)

    // Delegated Lending & Borrowing Methods
    fun generateNextLendId(): String = lendingCoordinator.generateNextLendId()
    fun generateNextBorrowId(): String = lendingCoordinator.generateNextBorrowId()
    fun isLendIdUnique(id: String, excludeTxId: Long? = null, excludeLoanId: Long? = null): Boolean = lendingCoordinator.isLendIdUnique(id, excludeTxId, excludeLoanId)
    fun isBorrowIdUnique(id: String, excludeTxId: Long? = null, excludeLoanId: Long? = null): Boolean = lendingCoordinator.isBorrowIdUnique(id, excludeTxId, excludeLoanId)
    fun addLoan(personName: String, phone: String, amount: Double, lentDate: String, expectedDate: String?, notes: String, lendId: String? = null) =
        lendingCoordinator.addLoan(personName, phone, amount, lentDate, expectedDate, notes, lendId)
    fun addBorrowRecord(personName: String, phone: String = "", amount: Double, borrowDate: String, expectedDate: String? = null, accountId: Long = accounts.value.firstOrNull()?.id ?: 1, borrowId: String? = null, notes: String = "") =
        lendingCoordinator.addBorrowRecord(personName, phone, amount, borrowDate, expectedDate, accountId, borrowId, notes)
    fun recordRepayment(loanId: Long, amount: Double, date: String, method: String, notes: String) =
        lendingCoordinator.recordRepayment(loanId, amount, date, method, notes)
    fun deleteRepayment(repaymentId: Long, loanId: Long) = lendingCoordinator.deleteRepayment(repaymentId, loanId)
    fun deleteLoan(id: Long) = lendingCoordinator.deleteLoan(id)
    fun deleteBorrowSet(borrowId: String, deleteInflowTx: Boolean = false) = lendingCoordinator.deleteBorrowSet(borrowId, deleteInflowTx)
    fun deleteLendSet(lendTag: String, loanId: Long? = null, deleteLendTx: Boolean = false) = lendingCoordinator.deleteLendSet(lendTag, loanId, deleteLendTx)

    // Delegated Company Expense Methods
    fun scanForCompanyExpenses() = companyExpenseCoordinator.scanForCompanyExpenses()
    fun logAllPendingCompanyExpenses() = companyExpenseCoordinator.logAllPendingCompanyExpenses()
    fun dismissPendingCompanyExpenses() = companyExpenseCoordinator.dismissPendingCompanyExpenses()
    fun acceptCompanyExpensePrompt(prompt: CompanyExpensePrompt) = companyExpenseCoordinator.acceptCompanyExpensePrompt(prompt)
    fun acceptReimbursementPrompt(prompt: CompanyExpensePrompt) = companyExpenseCoordinator.acceptReimbursementPrompt(prompt)
    fun dismissCompanyExpensePrompt() = companyExpenseCoordinator.dismissCompanyExpensePrompt()
    fun addCompanyExpense(date: String, amount: Double, reason: String, category: String, company: String, method: String, notes: String, advanceId: String? = null) =
        companyExpenseCoordinator.addCompanyExpense(date, amount, reason, category, company, method, notes, advanceId)
    fun linkCompanyExpenseToAdvance(expenseId: Long, advanceId: String?) = companyExpenseCoordinator.linkCompanyExpenseToAdvance(expenseId, advanceId)
    fun updateCompanyExpense(expense: CompanyExpenseEntity) = companyExpenseCoordinator.updateCompanyExpense(expense)
    fun toggleCompanyReimbursement(id: Long, currentStatus: Boolean) = companyExpenseCoordinator.toggleCompanyReimbursement(id, currentStatus)
    fun markCompanyExpenseApplied(id: Long, applied: Boolean) = companyExpenseCoordinator.markCompanyExpenseApplied(id, applied)
    fun markCompanyExpenseReimbursed(id: Long, reimbursed: Boolean) = companyExpenseCoordinator.markCompanyExpenseReimbursed(id, reimbursed)
    fun deleteCompanyExpense(id: Long) = companyExpenseCoordinator.deleteCompanyExpense(id)

    // Delegated Category & Rule Methods
    fun acceptSmartRule(prompt: SmartRulePrompt) = categoryRuleCoordinator.acceptSmartRule(prompt)
    fun dismissSmartRule() = categoryRuleCoordinator.dismissSmartRule()
    fun addRule(keyword: String, category: CategoryEntity, priority: Int, matchType: MatchType) = categoryRuleCoordinator.addRule(keyword, category, priority, matchType)
    fun toggleRule(id: Long, isActive: Boolean) = categoryRuleCoordinator.toggleRule(id, isActive)
    fun deleteRule(id: Long) = categoryRuleCoordinator.deleteRule(id)
    fun updateRule(rule: CategorizationRuleEntity) = categoryRuleCoordinator.updateRule(rule)
    fun addCategory(name: String, type: CategoryType, colorHex: String) = categoryRuleCoordinator.addCategory(name, type, colorHex)
    fun deleteCategory(id: Long) = categoryRuleCoordinator.deleteCategory(id)
    fun updateCategory(category: CategoryEntity) = categoryRuleCoordinator.updateCategory(category)

    // Delegated Undo Methods
    suspend fun recordUndoAction(
        actionType: String, description: String, previousTxs: List<TransactionEntity> = emptyList(),
        newTxs: List<TransactionEntity> = emptyList(), affectedIds: List<Long> = emptyList(),
        relatedRuleId: Long? = null, ruleSnapshot: CategorizationRuleEntity? = null
    ): String = undoCoordinator.recordUndoAction(actionType, description, previousTxs, newTxs, affectedIds, relatedRuleId, ruleSnapshot)

    fun undoLastAction() = undoCoordinator.undoLastAction()
    fun undoAction(actionId: String) = undoCoordinator.undoAction(actionId)
    fun clearUndoHistory() = undoCoordinator.clearUndoHistory()

    // Delegated Statement Import Methods
    fun previewStatement(content: String, fileName: String = "bank_statement.csv") = statementImportCoordinator.previewStatement(content, fileName)
    fun parseStatementUri(context: Context, uri: Uri, fileName: String = "statement.csv") = statementImportCoordinator.parseStatementUri(context, uri, fileName)
    fun confirmImport(skipDuplicates: Boolean = false, targetAccountId: Long = 1) = statementImportCoordinator.confirmImport(skipDuplicates, targetAccountId)
    fun clearImportPreview() = statementImportCoordinator.clearImportPreview()

    // Export Reports
    fun getTransactionsCsvExport(): String = ExportService.exportTransactionsCsv(allTransactions.value)
    fun getLoansCsvExport(): String = ExportService.exportLoansCsv(loans.value)
    fun getCompanyExpensesCsvExport(): String = ExportService.exportCompanyExpensesCsv(companyExpenses.value)
    fun getFullReportSummaryText(): String = ExportService.exportFinancialReportSummary(
        dashboardSummary.value, categoryBreakdown.value, monthlyTrends.value
    )

    // Delegated Excel Sync Methods
    fun loadSyncMetadata() = excelSyncCoordinator.loadSyncMetadata()
    fun setSelectedExcelFile(uriString: String, fileName: String) = excelSyncCoordinator.setSelectedExcelFile(uriString, fileName)
    fun loadExcelPreview(context: Context, uri: Uri) = excelSyncCoordinator.loadExcelPreview(context, uri)
    fun exportToExcel(context: Context, uri: Uri) = excelSyncCoordinator.exportToExcel(context, uri)
    fun importChangesFromExcel(context: Context, uri: Uri) = excelSyncCoordinator.importChangesFromExcel(context, uri)
    fun performTwoWaySync(context: Context, uri: Uri) = excelSyncCoordinator.performTwoWaySync(context, uri)
    fun resolveConflict(conflict: SyncConflict, keepApp: Boolean, context: Context, uri: Uri?) = excelSyncCoordinator.resolveConflict(conflict, keepApp, context, uri)
}
