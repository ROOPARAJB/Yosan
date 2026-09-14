package com.example.features.transactions

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.entity.*
import com.example.repository.FinanceRepository
import com.example.features.auth.*
import com.example.features.auth.data.AuthRepository
import com.example.features.rules.*
import com.example.features.rules.data.RulesRepository
import com.example.features.reports.*
import com.example.features.reports.data.ReportsRepository
import com.example.features.import.*
import com.example.features.sync.*
import com.example.features.transactions.data.TransactionRepository
import com.example.features.lending.*
import com.example.features.lending.data.LendingRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.utils.UndoJsonHelper

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


class FinanceViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application, viewModelScope)
    val repository = FinanceRepository(database)

    private val transactionRepository = TransactionRepository(database)
    private val lendingRepository = LendingRepository(database)
    private val rulesRepository = RulesRepository(database)
    private val reportsRepository = ReportsRepository(database)
    private val authRepository = AuthRepository(database)

    // Use Cases
    private val getTransactionsUseCase = GetTransactionsUseCase(transactionRepository)
    private val getAccountsUseCase = GetAccountsUseCase(transactionRepository)
    private val getCategoriesUseCase = GetCategoriesUseCase(transactionRepository)
    private val addTransactionUseCase = AddTransactionUseCase(transactionRepository)
    private val deleteTransactionUseCase = DeleteTransactionUseCase(transactionRepository)

    private val getLoansUseCase = GetLoansUseCase(lendingRepository)
    private val getActiveLoansUseCase = GetActiveLoansUseCase(lendingRepository)
    private val addLoanUseCase = AddLoanUseCase(lendingRepository)
    private val deleteLoanUseCase = DeleteLoanUseCase(lendingRepository)
    private val recordRepaymentUseCase = RecordRepaymentUseCase(lendingRepository)

    private val getRulesUseCase = GetRulesUseCase(rulesRepository)
    private val addRuleUseCase = AddRuleUseCase(rulesRepository)
    private val toggleRuleUseCase = ToggleRuleUseCase(rulesRepository)
    private val deleteRuleUseCase = DeleteRuleUseCase(rulesRepository)

    private val getCompanyExpensesUseCase = GetCompanyExpensesUseCase(reportsRepository)
    private val addCompanyExpenseUseCase = AddCompanyExpenseUseCase(reportsRepository)
    private val toggleReimbursementUseCase = ToggleReimbursementUseCase(reportsRepository)
    private val deleteCompanyExpenseUseCase = DeleteCompanyExpenseUseCase(reportsRepository)

    private val getUserProfileUseCase = GetUserProfileUseCase(authRepository)
    private val updateUserProfileUseCase = UpdateUserProfileUseCase(authRepository)

    // Base Flows from DB
    val userProfile: StateFlow<UserProfileEntity?> = getUserProfileUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val accounts: StateFlow<List<AccountEntity>> = getAccountsUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<CategoryEntity>> = getCategoriesUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val rules: StateFlow<List<CategorizationRuleEntity>> = getRulesUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTransactions: StateFlow<List<TransactionEntity>> = getTransactionsUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val loans: StateFlow<List<LoanEntity>> = getLoansUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val companyExpenses: StateFlow<List<CompanyExpenseEntity>> = getCompanyExpensesUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val advanceSummaries: StateFlow<List<AdvanceSummary>> = combine(
        allTransactions,
        companyExpenses
    ) { txs, compExpenses ->
        val advanceIds = mutableSetOf<String>()
        for (tx in txs) {
            tx.advanceId?.trim()?.takeIf { it.isNotBlank() }?.let { advanceIds.add(it) }
        }
        for (comp in compExpenses) {
            val match = Regex("""#(ADV[-_ ]*\d+)""", RegexOption.IGNORE_CASE).find(comp.notes)
                ?: Regex("""\b(ADV[-_ ]*\d+)\b""", RegexOption.IGNORE_CASE).find(comp.notes)
            match?.groupValues?.get(1)?.let { advanceIds.add(it.uppercase().replace(" ", "-")) }
        }

        advanceIds.map { advId ->
            val cleanAdvId = advId.trim()
            val inflows = txs.filter { it.advanceId?.trim().equals(cleanAdvId, ignoreCase = true) && (it.transactionType == TransactionType.INCOME || it.creditAmount > 0) }
            val primaryInflow = inflows.firstOrNull()
            val totalReceived = inflows.sumOf { if (it.creditAmount > 0) it.creditAmount else it.amount }

            val spentTxs = txs.filter { 
                it.advanceId?.trim().equals(cleanAdvId, ignoreCase = true) && 
                it.transactionType != TransactionType.INCOME && 
                it.creditAmount <= 0 
            }
            val spentComp = compExpenses.filter { 
                it.notes.contains(cleanAdvId, ignoreCase = true) || it.notes.contains("#$cleanAdvId", ignoreCase = true)
            }

            val totalSpent = spentTxs.sumOf { it.amount } + spentComp.sumOf { it.amount }
            val remaining = totalReceived - totalSpent

            AdvanceSummary(
                advanceId = cleanAdvId,
                inflowTransaction = primaryInflow,
                totalReceived = totalReceived,
                totalSpent = totalSpent,
                remainingBalance = remaining,
                linkedTransactions = spentTxs,
                linkedCompanyExpenses = spentComp
            )
        }.sortedByDescending { it.inflowTransaction?.transactionDate ?: it.advanceId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val undoHistory: StateFlow<List<UndoHistoryEntity>> = repository.undoHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _undoSnackbarEvent = MutableSharedFlow<UndoSnackbarData>(extraBufferCapacity = 1)
    val undoSnackbarEvent = _undoSnackbarEvent.asSharedFlow()

    // UI Search & Filters
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedTypeFilter = MutableStateFlow<TransactionType?>(null)
    val selectedTypeFilter = _selectedTypeFilter.asStateFlow()

    private val _selectedCategoryFilter = MutableStateFlow<String?>(null)
    val selectedCategoryFilter = _selectedCategoryFilter.asStateFlow()

    private val _selectedAccountFilter = MutableStateFlow<Long?>(null)
    val selectedAccountFilter = _selectedAccountFilter.asStateFlow()

    private val _selectedMonthFilter = MutableStateFlow<String?>(null) // format YYYY-MM
    val selectedMonthFilter = _selectedMonthFilter.asStateFlow()

    // Smart Rule Prompt state
    private val _smartRulePrompt = MutableStateFlow<SmartRulePrompt?>(null)
    val smartRulePrompt = _smartRulePrompt.asStateFlow()

    // Company Expense Prompt state (single, for incremental detection)
    private val _companyExpensePrompt = MutableStateFlow<CompanyExpensePrompt?>(null)
    val companyExpensePrompt = _companyExpensePrompt.asStateFlow()

    // Bulk company expense list — populated after import when multiple matches found
    private val _pendingCompanyExpenses = MutableStateFlow<List<CompanyExpensePrompt>>(emptyList())
    val pendingCompanyExpenses = _pendingCompanyExpenses.asStateFlow()

    // Import Preview state
    private val _importPreview = MutableStateFlow<ImportPreviewResult?>(null)
    val importPreview = _importPreview.asStateFlow()

    init {
        recalculateAllAccountBalances()
        loadSyncMetadata()
    }

    // Privacy Mode (Hide Financial Values)
    private val _isAmountTemporarilyRevealed = MutableStateFlow(false)
    val isAmountTemporarilyRevealed = _isAmountTemporarilyRevealed.asStateFlow()
    private var revealJob: kotlinx.coroutines.Job? = null

    fun revealAmountsTemporarily() {
        val timeout = userProfile.value?.blurTimeoutSeconds ?: 5
        revealJob?.cancel()
        _isAmountTemporarilyRevealed.value = true
        revealJob = viewModelScope.launch {
            kotlinx.coroutines.delay(timeout * 1000L)
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

    private val _isImporting = MutableStateFlow(false)
    val isImporting = _isImporting.asStateFlow()

    // Toast/Snackbar message
    private val _uiMessage = MutableStateFlow<String?>(null)
    val uiMessage = _uiMessage.asStateFlow()

    // Derived Dashboard Summary
    val dashboardSummary: StateFlow<DashboardSummary> = combine(
        allTransactions,
        loans,
        companyExpenses,
        accounts
    ) { txs, lns, compExp, accs ->
        FinancialCalculationService.calculateSummary(txs, lns, compExp, accs)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardSummary())

    // Derived Category Breakdown (Expense)
    val categoryBreakdown: StateFlow<List<CategoryExpenseItem>> = combine(
        allTransactions,
        categories
    ) { txs, cats ->
        FinancialCalculationService.calculateCategoryExpenseBreakdown(txs, cats)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Derived Category Breakdown (Income)
    val categoryIncomeBreakdown: StateFlow<List<CategoryExpenseItem>> = combine(
        allTransactions,
        categories
    ) { txs, cats ->
        FinancialCalculationService.calculateCategoryIncomeBreakdown(txs, cats)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Derived Monthly Trends
    val monthlyTrends: StateFlow<List<MonthlyTrendItem>> = allTransactions.map { txs ->
        FinancialCalculationService.calculateMonthlyTrends(txs)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Derived Lending Summaries
    val personLendingSummaries: StateFlow<List<PersonLendingSummary>> = loans.map { lns ->
        FinancialCalculationService.calculatePersonLendingSummaries(lns)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Derived Insights
    val financialInsights: StateFlow<List<FinancialInsight>> = combine(
        dashboardSummary,
        categoryBreakdown,
        monthlyTrends
    ) { summary, breakdown, trends ->
        FinancialCalculationService.generateInsights(summary, breakdown, trends)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filtered Transactions
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

    init {
        viewModelScope.launch {
            try {
                // Ensure essential default categories exist and deduplicate any duplicates
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
                    val hasEmi = database.categoryDao().getCategoryByName("EMI")
                    if (hasEmi == null) {
                        database.categoryDao().insertCategory(
                            CategoryEntity(
                                name = "EMI",
                                type = CategoryType.EXPENSE,
                                colorHex = "#E11D48",
                                iconName = "receipt_long",
                                isSystem = true
                            )
                        )
                    }
                    val hasAdvance = database.categoryDao().getCategoryByName("Advance")
                    if (hasAdvance == null) {
                        database.categoryDao().insertCategory(
                            CategoryEntity(
                                name = "Advance",
                                type = CategoryType.INCOME,
                                colorHex = "#0284C7",
                                iconName = "account_balance_wallet",
                                isSystem = true
                            )
                        )
                    }
                }

                val rules = database.categorizationRuleDao().getActiveRulesList()
                val transactions = database.transactionDao().getAllTransactionsList()
                val uncategorized = transactions.filter { it.categoryName.equals("Uncategorized", ignoreCase = true) }
                uncategorized.forEach { tx ->
                    val result = CategorizationEngine.categorize(tx.description, rules, tx.creditAmount > 0.0)
                    if (result.matchedRule != null) {
                        database.transactionDao().updateTransactionCategory(
                            tx.id,
                            result.categoryId,
                            result.categoryName,
                            result.transactionType
                        )
                    }
                }
            } catch (_: Exception) {}
            scanForCompanyExpenses()
        }
    }

    fun scanForCompanyExpenses() {
        viewModelScope.launch {
            try {
                val transactions = database.transactionDao().getAllTransactionsList()
                val companyExpenses = database.companyExpenseDao().getAllCompanyExpensesList()

                // Check for unreimbursed credits matching outstanding company expense amount (single prompt)
                val credits = transactions.filter {
                    (it.creditAmount > 0.0 || (it.amount > 0.0 && it.transactionType == TransactionType.INCOME)) &&
                    it.description.contains("company", ignoreCase = true)
                }
                val outstandingExpenses = companyExpenses.filter { !it.isReimbursed }

                for (credit in credits) {
                    val creditAmt = if (credit.creditAmount > 0.0) credit.creditAmount else credit.amount
                    val match = outstandingExpenses.firstOrNull { Math.abs(it.amount - creditAmt) < 0.01 }
                    if (match != null) {
                        _companyExpensePrompt.value = CompanyExpensePrompt(
                            transactionId = credit.id,
                            description = credit.description,
                            amount = creditAmt,
                            date = credit.transactionDate,
                            isReimbursement = true,
                            matchingExpenseId = match.id
                        )
                        return@launch
                    }
                }

                // Collect ALL unlogged debits with "company" keyword for bulk handling
                val debits = transactions.filter {
                    (it.debitAmount > 0.0 || (it.amount > 0.0 && it.transactionType == TransactionType.EXPENSE)) &&
                    it.description.contains("company", ignoreCase = true)
                }
                val unlogged = debits.filter { debit ->
                    val debitAmt = if (debit.debitAmount > 0.0) debit.debitAmount else debit.amount
                    !companyExpenses.any { it.date == debit.transactionDate && Math.abs(it.amount - debitAmt) < 0.01 }
                }.map { debit ->
                    val debitAmt = if (debit.debitAmount > 0.0) debit.debitAmount else debit.amount
                    CompanyExpensePrompt(
                        transactionId = debit.id,
                        description = debit.description,
                        amount = debitAmt,
                        date = debit.transactionDate,
                        isReimbursement = false
                    )
                }

                when {
                    unlogged.size > 1 -> {
                        // Multiple matches — show bulk dialog
                        _pendingCompanyExpenses.value = unlogged
                    }
                    unlogged.size == 1 -> {
                        // Single match — use single prompt as before
                        _companyExpensePrompt.value = unlogged.first()
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun logAllPendingCompanyExpenses() {
        viewModelScope.launch {
            try {
                val pending = _pendingCompanyExpenses.value
                pending.forEach { prompt ->
                    val expense = CompanyExpenseEntity(
                        date = prompt.date,
                        amount = prompt.amount,
                        reason = prompt.description,
                        companyName = "Corporate",
                        category = "Official Expense",
                        paymentMethod = "Corporate Card / UPI"
                    )
                    repository.insertCompanyExpense(expense)
                }
                _pendingCompanyExpenses.value = emptyList()
                showMessage("${pending.size} official expenses logged successfully")
            } catch (_: Exception) {}
        }
    }

    fun dismissPendingCompanyExpenses() {
        _pendingCompanyExpenses.value = emptyList()
    }

    fun acceptCompanyExpensePrompt(prompt: CompanyExpensePrompt) {
        viewModelScope.launch {
            try {
                val expense = CompanyExpenseEntity(
                    date = prompt.date,
                    amount = prompt.amount,
                    reason = prompt.description,
                    companyName = "Corporate",
                    category = "Travel",
                    paymentMethod = "Corporate Card / UPI"
                )
                repository.insertCompanyExpense(expense)
                _companyExpensePrompt.value = null
                showMessage("Official expense of ${prompt.amount} logged")
                scanForCompanyExpenses()
            } catch (_: Exception) {}
        }
    }

    fun acceptReimbursementPrompt(prompt: CompanyExpensePrompt) {
        viewModelScope.launch {
            try {
                if (prompt.matchingExpenseId != null) {
                    repository.updateReimbursementStatus(prompt.matchingExpenseId, true)
                    _companyExpensePrompt.value = null
                    showMessage("Expense marked as reimbursed")
                    scanForCompanyExpenses()
                }
            } catch (_: Exception) {}
        }
    }

    fun dismissCompanyExpensePrompt() {
        _companyExpensePrompt.value = null
    }

    // Filter setters
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

    fun dismissMessage() { _uiMessage.value = null }
    fun showMessage(msg: String) { _uiMessage.value = msg }

    // Toggle Dark Mode
    fun toggleDarkMode(isDark: Boolean) {
        viewModelScope.launch {
            repository.updateThemePreference(isDark)
        }
    }

    // Transaction Actions
    fun addManualTransaction(
        date: String,
        description: String,
        amount: Double,
        type: TransactionType,
        categoryId: Long?,
        categoryName: String,
        accountId: Long,
        notes: String,
        advanceId: String? = null
    ) {
        viewModelScope.launch {
            val containsCompany = description.contains("company", ignoreCase = true)
            var finalCatId = categoryId
            var finalCatName = categoryName
            var finalType = type

            if (containsCompany) {
                val catName = "Official Expense"
                val existingCat = database.categoryDao().getCategoryByName(catName)
                val catId = if (existingCat != null) {
                    existingCat.id
                } else {
                    database.categoryDao().insertCategory(
                        CategoryEntity(
                            name = catName,
                            type = com.example.data.local.entity.CategoryType.EXPENSE,
                            colorHex = "#06B6D4"
                        )
                    )
                }
                finalCatId = catId
                finalCatName = catName
                finalType = TransactionType.EXPENSE
            }

            val debit = if (finalType == TransactionType.EXPENSE || finalType == TransactionType.LENDING || finalType == TransactionType.INVESTMENT) amount else 0.0
            val credit = if (finalType == TransactionType.INCOME || finalType == TransactionType.REFUND) amount else 0.0

            val cleanAdvanceId = advanceId?.trim()?.takeIf { it.isNotBlank() }
            val finalAdvId = if (cleanAdvanceId != null) {
                if (isAdvanceIdUnique(cleanAdvanceId)) cleanAdvanceId else generateNextAdvanceId()
            } else null

            val tx = TransactionEntity(
                accountId = accountId,
                transactionDate = date,
                description = description,
                debitAmount = debit,
                creditAmount = credit,
                amount = amount,
                transactionType = finalType,
                categoryId = finalCatId,
                categoryName = finalCatName,
                source = "MANUAL",
                notes = notes,
                advanceId = finalAdvId,
                isManual = true,
                isCategorized = true,
                categorizationConfidence = 1.0f
            )
            repository.insertTransaction(tx)
            showMessage("Transaction added successfully${if (finalAdvId != null) " (ID: $finalAdvId)" else ""}")
            scanForCompanyExpenses()
        }
    }

    fun generateNextAdvanceId(): String {
        val txs = allTransactions.value
        val existingNums = mutableSetOf<Int>()
        for (tx in txs) {
            val id = tx.advanceId ?: continue
            val match = Regex("""(?:ADV|advance[_\s-]*id|advance)[_\s-]*(\d+)""", RegexOption.IGNORE_CASE).find(id)
            if (match != null) {
                match.groupValues[1].toIntOrNull()?.let { existingNums.add(it) }
            } else {
                id.trim().toIntOrNull()?.let { existingNums.add(it) }
            }
        }
        var nextNum = 1
        while (existingNums.contains(nextNum) || !isAdvanceIdUnique("ADV-$nextNum")) {
            nextNum++
        }
        return "ADV-$nextNum"
    }

    fun generateNextLendId(): String {
        val txs = allTransactions.value
        val loansList = loans.value
        val existingNums = mutableSetOf<Int>()
        for (str in (txs.mapNotNull { it.advanceId ?: it.referenceNumber } + loansList.map { it.notes })) {
            val match = Regex("""(?:LEND)[_\s-]*(\d+)""", RegexOption.IGNORE_CASE).find(str)
            if (match != null) {
                match.groupValues[1].toIntOrNull()?.let { existingNums.add(it) }
            }
        }
        var nextNum = 1
        while (existingNums.contains(nextNum) || !isLendIdUnique("LEND-$nextNum")) {
            nextNum++
        }
        return "LEND-$nextNum"
    }

    fun generateNextBorrowId(): String {
        val txs = allTransactions.value
        val existingNums = mutableSetOf<Int>()
        for (str in txs.mapNotNull { it.advanceId ?: it.referenceNumber }) {
            val match = Regex("""(?:BORROW)[_\s-]*(\d+)""", RegexOption.IGNORE_CASE).find(str)
            if (match != null) {
                match.groupValues[1].toIntOrNull()?.let { existingNums.add(it) }
            }
        }
        var nextNum = 1
        while (existingNums.contains(nextNum) || !isBorrowIdUnique("BORROW-$nextNum")) {
            nextNum++
        }
        return "BORROW-$nextNum"
    }

    fun isAdvanceIdUnique(id: String, excludeTxId: Long? = null): Boolean {
        if (id.isBlank()) return true
        val clean = id.trim()
        val txs = allTransactions.value
        return txs.none { 
            it.id != excludeTxId && 
            (it.transactionType == TransactionType.INCOME || it.creditAmount > 0) && 
            it.advanceId?.trim().equals(clean, ignoreCase = true) 
        }
    }

    fun isLendIdUnique(id: String, excludeTxId: Long? = null, excludeLoanId: Long? = null): Boolean {
        if (id.isBlank()) return true
        val clean = id.trim()
        val txs = allTransactions.value
        val loansList = loans.value
        val inTxs = txs.any { it.id != excludeTxId && (it.advanceId?.trim().equals(clean, ignoreCase = true) || it.referenceNumber.trim().equals(clean, ignoreCase = true)) }
        val inLoans = loansList.any { it.id != excludeLoanId && (it.notes.contains(clean, ignoreCase = true) || it.notes.contains("#$clean", ignoreCase = true)) }
        return !inTxs && !inLoans
    }

    fun isBorrowIdUnique(id: String, excludeTxId: Long? = null, excludeLoanId: Long? = null): Boolean {
        if (id.isBlank()) return true
        val clean = id.trim()
        val txs = allTransactions.value
        val loansList = loans.value
        val inTxs = txs.any { it.id != excludeTxId && (it.advanceId?.trim().equals(clean, ignoreCase = true) || it.referenceNumber.trim().equals(clean, ignoreCase = true)) }
        val inLoans = loansList.any { it.id != excludeLoanId && (it.notes.contains(clean, ignoreCase = true) || it.notes.contains("#$clean", ignoreCase = true)) }
        return !inTxs && !inLoans
    }

    fun updateTransactionAdvanceId(txId: Long, advanceId: String?) {
        viewModelScope.launch {
            val tx = repository.getTransactionById(txId)
            if (tx != null) {
                val cleanId = advanceId?.trim()?.takeIf { it.isNotBlank() }
                if (cleanId != null) {
                    val isUnique = when (tx.transactionType) {
                        TransactionType.LENDING -> isLendIdUnique(cleanId, excludeTxId = txId)
                        TransactionType.BORROWING -> isBorrowIdUnique(cleanId, excludeTxId = txId)
                        TransactionType.INCOME -> isAdvanceIdUnique(cleanId, excludeTxId = txId)
                        else -> true // EXPENSE transactions link to existing Advance IDs!
                    }
                    if (!isUnique) {
                        showMessage("ID '$cleanId' is already assigned to another inflow/loan! Must be unique.")
                        return@launch
                    }
                }
                val updated = tx.copy(advanceId = cleanId, updatedAt = System.currentTimeMillis())
                repository.updateTransaction(updated)
                showMessage(if (cleanId != null) "ID set to '$cleanId'" else "ID cleared")
            }
        }
    }

    fun linkTransactionToAdvance(txId: Long, advanceId: String?) {
        updateTransactionAdvanceId(txId, advanceId)
    }

    // Undo & Recovery Engine
    suspend fun recordUndoAction(
        actionType: String,
        description: String,
        previousTxs: List<TransactionEntity> = emptyList(),
        newTxs: List<TransactionEntity> = emptyList(),
        affectedIds: List<Long> = emptyList(),
        relatedRuleId: Long? = null,
        ruleSnapshot: CategorizationRuleEntity? = null
    ): String {
        val ids = if (affectedIds.isNotEmpty()) affectedIds else (previousTxs.map { it.id } + newTxs.map { it.id }).distinct()
        val action = UndoHistoryEntity(
            actionType = actionType,
            description = description,
            timestamp = System.currentTimeMillis(),
            affectedTransactionIds = UndoJsonHelper.serializeIds(ids),
            previousStateJson = UndoJsonHelper.serializeTransactions(previousTxs),
            newStateJson = UndoJsonHelper.serializeTransactions(newTxs),
            relatedRuleId = relatedRuleId,
            ruleSnapshotJson = ruleSnapshot?.let { UndoJsonHelper.serializeRule(it) }
        )
        repository.insertUndoAction(action)
        _undoSnackbarEvent.tryEmit(UndoSnackbarData(description, action.actionId))
        return action.actionId
    }

    fun undoLastAction() {
        viewModelScope.launch {
            val latest = repository.getLatestUndoAction()
            if (latest != null) {
                undoAction(latest.actionId)
            }
        }
    }

    fun undoAction(actionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val action = repository.getUndoActionById(actionId) ?: return@launch
                val prevTxs = UndoJsonHelper.deserializeTransactions(action.previousStateJson)
                val newTxs = UndoJsonHelper.deserializeTransactions(action.newStateJson)
                val ruleSnapshot = UndoJsonHelper.deserializeRule(action.ruleSnapshotJson)
                val newTxMap = newTxs.associateBy { it.id }

                when (action.actionType) {
                    "CHANGE_CATEGORY", "CHANGE_TYPE", "CHANGE_DESCRIPTION", "EDIT_TRANSACTION" -> {
                        for (prev in prevTxs) {
                            val restored = prev.copy(updatedAt = System.currentTimeMillis())
                            database.transactionDao().updateTransaction(restored)
                        }
                    }
                    "DELETE_TRANSACTION", "BULK_DELETE" -> {
                        for (prev in prevTxs) {
                            val restored = prev.copy(updatedAt = System.currentTimeMillis())
                            database.transactionDao().insertTransaction(restored)
                            if (restored.syncId.isNotBlank()) {
                                database.syncDao().removeDeletedTransaction(restored.syncId)
                            }
                        }
                    }
                    "APPLY_RULE", "BULK_CATEGORIZE" -> {
                        // Manual Change Preservation Principle:
                        // Only revert transactions that were not subsequently changed by the user
                        for (prev in prevTxs) {
                            val current = database.transactionDao().getTransactionById(prev.id) ?: continue
                            val expectedNew = newTxMap[prev.id]
                            val wasUnmodifiedSince = expectedNew == null || current.categoryName.equals(expectedNew.categoryName, ignoreCase = true)
                            if (wasUnmodifiedSince) {
                                val restored = current.copy(
                                    categoryId = prev.categoryId,
                                    categoryName = prev.categoryName,
                                    transactionType = prev.transactionType,
                                    isCategorized = prev.isCategorized,
                                    categorizationConfidence = prev.categorizationConfidence,
                                    updatedAt = System.currentTimeMillis()
                                )
                                database.transactionDao().updateTransaction(restored)
                            }
                        }
                    }
                    "ADD_RULE" -> {
                        if (action.relatedRuleId != null) {
                            database.categorizationRuleDao().deleteRule(action.relatedRuleId)
                        }
                        // Revert rule's applied transactions while preserving any manual overrides
                        for (prev in prevTxs) {
                            val current = database.transactionDao().getTransactionById(prev.id) ?: continue
                            val expectedNew = newTxMap[prev.id]
                            val wasUnmodifiedSince = expectedNew == null || current.categoryName.equals(expectedNew.categoryName, ignoreCase = true)
                            if (wasUnmodifiedSince) {
                                val restored = current.copy(
                                    categoryId = prev.categoryId,
                                    categoryName = prev.categoryName,
                                    transactionType = prev.transactionType,
                                    isCategorized = prev.isCategorized,
                                    categorizationConfidence = prev.categorizationConfidence,
                                    updatedAt = System.currentTimeMillis()
                                )
                                database.transactionDao().updateTransaction(restored)
                            }
                        }
                    }
                    "DELETE_RULE" -> {
                        if (ruleSnapshot != null) {
                            database.categorizationRuleDao().insertRule(ruleSnapshot)
                        }
                    }
                    "EDIT_RULE" -> {
                        if (ruleSnapshot != null) {
                            database.categorizationRuleDao().updateRule(ruleSnapshot)
                        }
                    }
                }

                repository.deleteUndoAction(actionId)
                withContext(Dispatchers.Main) {
                    showMessage("Undone: ${action.description}")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showMessage("Failed to undo: ${e.message}")
                }
            }
        }
    }

    fun clearUndoHistory() {
        viewModelScope.launch {
            repository.clearUndoHistory()
            showMessage("Undo history cleared")
        }
    }

    fun updateTransactionCategory(tx: TransactionEntity, newCategory: CategoryEntity) {
        viewModelScope.launch {
            val isCredit = tx.creditAmount > 0 || tx.transactionType == TransactionType.INCOME || tx.transactionType == TransactionType.REFUND
            val resolvedType = when {
                newCategory.type == com.example.data.local.entity.CategoryType.INCOME -> TransactionType.INCOME
                newCategory.type == com.example.data.local.entity.CategoryType.INVESTMENT -> TransactionType.INVESTMENT
                newCategory.name.equals("Transfer", ignoreCase = true) || newCategory.type == com.example.data.local.entity.CategoryType.OTHER -> TransactionType.TRANSFER
                newCategory.name.equals("Advance", ignoreCase = true) -> if (isCredit) TransactionType.INCOME else TransactionType.EXPENSE
                else -> TransactionType.EXPENSE
            }
            val amount = if (tx.amount > 0) tx.amount else maxOf(tx.debitAmount, tx.creditAmount)
            val newCredit = if (resolvedType == TransactionType.INCOME || resolvedType == TransactionType.REFUND || (resolvedType == TransactionType.TRANSFER && isCredit)) amount else 0.0
            val newDebit = if (resolvedType == TransactionType.EXPENSE || resolvedType == TransactionType.LENDING || resolvedType == TransactionType.INVESTMENT || (resolvedType == TransactionType.TRANSFER && !isCredit)) amount else 0.0

            val oldTx = tx
            val updatedTx = tx.copy(
                categoryId = newCategory.id,
                categoryName = newCategory.name,
                transactionType = resolvedType,
                creditAmount = newCredit,
                debitAmount = newDebit,
                amount = amount,
                isCategorized = true,
                categorizationConfidence = 1.0f,
                updatedAt = System.currentTimeMillis()
            )
            repository.updateTransaction(updatedTx)
            recordUndoAction(
                actionType = "CHANGE_CATEGORY",
                description = "Changed category of '${tx.description.take(24)}' to ${newCategory.name}",
                previousTxs = listOf(oldTx),
                newTxs = listOf(updatedTx)
            )
            showMessage("Category updated to ${newCategory.name}")

            // Smart Rule Check: Don't prompt if a rule already covers this description or category
            val activeRules = repository.getActiveRulesList()
            val alreadyCovered = activeRules.any { rule ->
                (rule.categoryId == newCategory.id && tx.description.contains(rule.keyword, ignoreCase = true)) ||
                rule.keyword.equals(tx.description.trim(), ignoreCase = true)
            }

            if (!alreadyCovered) {
                val keyword = CategorizationEngine.suggestKeyword(tx.description)
                val keywordAlreadyExists = activeRules.any { it.keyword.equals(keyword, ignoreCase = true) }
                if (keyword.isNotBlank() && !keywordAlreadyExists) {
                    _smartRulePrompt.value = SmartRulePrompt(
                        keyword = keyword,
                        categoryId = newCategory.id,
                        categoryName = newCategory.name,
                        transactionType = resolvedType,
                        transactionDescription = tx.description
                    )
                }
            }
        }
    }

    fun updateTransactionType(tx: TransactionEntity, newType: TransactionType) {
        viewModelScope.launch {
            val oldTx = tx
            val amount = if (tx.amount > 0) tx.amount else maxOf(tx.debitAmount, tx.creditAmount)
            val updated = when (newType) {
                TransactionType.INCOME, TransactionType.REFUND -> {
                    tx.copy(
                        transactionType = newType,
                        creditAmount = amount,
                        debitAmount = 0.0,
                        amount = amount,
                        updatedAt = System.currentTimeMillis()
                    )
                }
                TransactionType.TRANSFER -> {
                    val wasCredit = tx.creditAmount > 0 || tx.transactionType == TransactionType.INCOME || tx.transactionType == TransactionType.REFUND
                    if (wasCredit) {
                        tx.copy(
                            transactionType = newType,
                            creditAmount = amount,
                            debitAmount = 0.0,
                            amount = amount,
                            updatedAt = System.currentTimeMillis()
                        )
                    } else {
                        tx.copy(
                            transactionType = newType,
                            debitAmount = amount,
                            creditAmount = 0.0,
                            amount = amount,
                            updatedAt = System.currentTimeMillis()
                        )
                    }
                }
                else -> {
                    tx.copy(
                        transactionType = newType,
                        debitAmount = amount,
                        creditAmount = 0.0,
                        amount = amount,
                        updatedAt = System.currentTimeMillis()
                    )
                }
            }
            repository.updateTransaction(updated)
            recordUndoAction(
                actionType = "CHANGE_TYPE",
                description = "Changed type of '${tx.description.take(24)}' to ${newType.name}",
                previousTxs = listOf(oldTx),
                newTxs = listOf(updated)
            )
            if (newType == TransactionType.LENDING) {
                val existingLoans = database.loanDao().getAllLoansList()
                val alreadyLinked = existingLoans.any { it.notes.contains(tx.description, ignoreCase = true) || it.amount == amount && it.lentDate == tx.transactionDate }
                if (!alreadyLinked) {
                    val cleanPersonName = tx.description.replace(Regex("(?i)upi/|imps/|neft/|transfer to|to |[0-9]"), "").trim().ifBlank { "Lending Contact" }
                    database.loanDao().insertLoan(
                        LoanEntity(
                            personName = cleanPersonName,
                            amount = amount,
                            amountRepaid = 0.0,
                            remainingAmount = amount,
                            lentDate = tx.transactionDate,
                            expectedRepaymentDate = null,
                            notes = tx.description,
                            status = LoanStatus.ACTIVE
                        )
                    )
                }
            }
            showMessage("Transaction type changed to ${newType.name}")
        }
    }

    fun updateTransactionsCategory(txIds: List<Long>, newCategory: CategoryEntity) {
        viewModelScope.launch {
            val prevList = mutableListOf<TransactionEntity>()
            val newList = mutableListOf<TransactionEntity>()
            txIds.forEach { id ->
                val tx = repository.getTransactionById(id)
                if (tx != null) {
                    val isCredit = tx.creditAmount > 0 || tx.transactionType == TransactionType.INCOME || tx.transactionType == TransactionType.REFUND
                    val resolvedType = when {
                        newCategory.type == com.example.data.local.entity.CategoryType.INCOME -> TransactionType.INCOME
                        newCategory.type == com.example.data.local.entity.CategoryType.INVESTMENT -> TransactionType.INVESTMENT
                        newCategory.name.equals("Transfer", ignoreCase = true) || newCategory.type == com.example.data.local.entity.CategoryType.OTHER -> TransactionType.TRANSFER
                        newCategory.name.equals("Advance", ignoreCase = true) -> if (isCredit) TransactionType.INCOME else TransactionType.EXPENSE
                        else -> TransactionType.EXPENSE
                    }
                    val amount = if (tx.amount > 0) tx.amount else maxOf(tx.debitAmount, tx.creditAmount)
                    val newCredit = if (resolvedType == TransactionType.INCOME || resolvedType == TransactionType.REFUND || (resolvedType == TransactionType.TRANSFER && isCredit)) amount else 0.0
                    val newDebit = if (resolvedType == TransactionType.EXPENSE || resolvedType == TransactionType.LENDING || resolvedType == TransactionType.INVESTMENT || (resolvedType == TransactionType.TRANSFER && !isCredit)) amount else 0.0

                    prevList.add(tx)
                    val updatedTx = tx.copy(
                        categoryId = newCategory.id,
                        categoryName = newCategory.name,
                        transactionType = resolvedType,
                        creditAmount = newCredit,
                        debitAmount = newDebit,
                        amount = amount,
                        updatedAt = System.currentTimeMillis()
                    )
                    repository.updateTransaction(updatedTx)
                    newList.add(updatedTx)
                }
            }
            recordUndoAction(
                actionType = "BULK_CATEGORIZE",
                description = "Categorized ${txIds.size} transactions as '${newCategory.name}'",
                previousTxs = prevList,
                newTxs = newList
            )
            showMessage("${txIds.size} transactions categorized as '${newCategory.name}'")
        }
    }

    fun updateTransactionDescription(txId: Long, newDescription: String) {
        viewModelScope.launch {
            val tx = repository.getTransactionById(txId)
            if (tx != null) {
                val oldTx = tx
                val containsCompany = newDescription.contains("company", ignoreCase = true)
                var updatedTx = tx.copy(description = newDescription, updatedAt = System.currentTimeMillis())
                if (containsCompany) {
                    val catName = "Official Expense"
                    val existingCat = database.categoryDao().getCategoryByName(catName)
                    val catId = if (existingCat != null) {
                        existingCat.id
                    } else {
                        database.categoryDao().insertCategory(
                            CategoryEntity(
                                name = catName,
                                type = com.example.data.local.entity.CategoryType.EXPENSE,
                                colorHex = "#06B6D4"
                            )
                        )
                    }
                    updatedTx = updatedTx.copy(
                        categoryId = catId,
                        categoryName = catName,
                        transactionType = TransactionType.EXPENSE
                    )
                }
                repository.updateTransaction(updatedTx)
                recordUndoAction(
                    actionType = "CHANGE_DESCRIPTION",
                    description = "Updated description for '${tx.description.take(24)}'",
                    previousTxs = listOf(oldTx),
                    newTxs = listOf(updatedTx)
                )
                showMessage("Description updated successfully")
                scanForCompanyExpenses()
            }
        }
    }

    private suspend fun applyRuleToExistingTransactions(keyword: String, categoryId: Long?, categoryName: String, transactionType: TransactionType): Pair<List<TransactionEntity>, List<TransactionEntity>> {
        val prevList = mutableListOf<TransactionEntity>()
        val newList = mutableListOf<TransactionEntity>()
        try {
            val list = database.transactionDao().getAllTransactionsList()
            val normalizedKeyword = CategorizationEngine.normalize(keyword)
            if (normalizedKeyword.isBlank()) return Pair(emptyList(), emptyList())
            list.forEach { tx ->
                val normalizedDesc = CategorizationEngine.normalize(tx.description)
                if (normalizedDesc.contains(normalizedKeyword)) {
                    prevList.add(tx)
                    database.transactionDao().updateTransactionCategory(tx.id, categoryId, categoryName, transactionType)
                    newList.add(tx.copy(categoryId = categoryId, categoryName = categoryName, transactionType = transactionType, updatedAt = System.currentTimeMillis()))
                }
            }
        } catch (_: Exception) {}
        return Pair(prevList, newList)
    }

    fun acceptSmartRule(prompt: SmartRulePrompt) {
        viewModelScope.launch {
            val rule = CategorizationRuleEntity(
                keyword = prompt.keyword,
                categoryId = prompt.categoryId ?: 1,
                categoryName = prompt.categoryName,
                transactionType = prompt.transactionType,
                priority = 10,
                matchType = MatchType.CONTAINS,
                isActive = true
            )
            val ruleId = repository.insertRule(rule)
            val (prev, new) = applyRuleToExistingTransactions(prompt.keyword, prompt.categoryId, prompt.categoryName, prompt.transactionType)
            recordUndoAction(
                actionType = "ADD_RULE",
                description = "Created auto-rule for '${prompt.keyword}' (applied to ${prev.size} txs)",
                previousTxs = prev,
                newTxs = new,
                relatedRuleId = ruleId,
                ruleSnapshot = rule.copy(id = ruleId)
            )
            _smartRulePrompt.value = null
            showMessage("Auto-categorization rule created and applied for '${prompt.keyword}'")
        }
    }

    fun dismissSmartRule() {
        _smartRulePrompt.value = null
    }

    fun addTransferTransaction(
        fromAccountId: Long,
        toAccountId: Long,
        amount: Double,
        date: String,
        notes: String = ""
    ) {
        viewModelScope.launch {
            if (fromAccountId == toAccountId) {
                showMessage("Source and destination accounts must be different")
                return@launch
            }
            val fromAcc = repository.getAccountById(fromAccountId)
            val toAcc = repository.getAccountById(toAccountId)
            val transferId = java.util.UUID.randomUUID().toString()

            val fromTx = TransactionEntity(
                accountId = fromAccountId,
                transactionDate = date,
                description = "Transfer to ${toAcc?.accountName ?: "Account"}",
                debitAmount = amount,
                creditAmount = 0.0,
                amount = amount,
                transactionType = TransactionType.TRANSFER,
                categoryName = "Transfer",
                transferId = transferId,
                notes = notes,
                isManual = true
            )
            val toTx = TransactionEntity(
                accountId = toAccountId,
                transactionDate = date,
                description = "Transfer from ${fromAcc?.accountName ?: "Account"}",
                debitAmount = 0.0,
                creditAmount = amount,
                amount = amount,
                transactionType = TransactionType.TRANSFER,
                categoryName = "Transfer",
                transferId = transferId,
                notes = notes,
                isManual = true
            )

            database.transactionDao().insertTransactions(listOf(fromTx, toTx))
            transactionRepository.recalculateAccountBalance(fromAccountId)
            transactionRepository.recalculateAccountBalance(toAccountId)
            recalculateAllAccountBalances()
            showMessage("Transfer of ₹$amount recorded")
        }
    }

    fun deleteTransaction(id: Long) {
        viewModelScope.launch {
            val tx = repository.getTransactionById(id)
            if (tx != null) {
                val affectedAccountIds = mutableSetOf(tx.accountId)
                val txsToDelete = mutableListOf(tx)

                // Handle paired transfer deletion
                if (!tx.transferId.isNullOrBlank()) {
                    val paired = database.transactionDao().getTransactionsByTransferId(tx.transferId)
                    paired.forEach { p ->
                        if (p.id != tx.id) {
                            txsToDelete.add(p)
                            affectedAccountIds.add(p.accountId)
                        }
                    }
                }

                txsToDelete.forEach { currentTx ->
                    if (currentTx.syncId.isNotBlank()) {
                        try {
                            database.syncDao().insertDeletedTransaction(DeletedTransactionEntity(currentTx.syncId))
                        } catch (_: Exception) {}
                    }
                    val allExpenses = database.companyExpenseDao().getAllCompanyExpensesList()
                    val txAmt = if (currentTx.amount > 0) currentTx.amount else maxOf(currentTx.debitAmount, currentTx.creditAmount)
                    val matchedExp = allExpenses.filter { it.date == currentTx.transactionDate && Math.abs(it.amount - txAmt) < 0.01 }
                    matchedExp.forEach { database.companyExpenseDao().deleteCompanyExpense(it.id) }

                    // Cascade delete linked loans and repayments
                    currentTx.linkedLoanId?.let { loanId ->
                        database.loanDao().deleteLoan(loanId)
                        database.loanRepaymentDao().deleteRepaymentsForLoan(loanId)
                    }
                    if (currentTx.transactionType == TransactionType.LENDING) {
                        val matchingLoans = database.loanDao().getAllLoansList().filter {
                            it.lentDate == currentTx.transactionDate && Math.abs(it.amount - txAmt) < 0.01
                        }
                        matchingLoans.forEach { loan ->
                            database.loanDao().deleteLoan(loan.id)
                            database.loanRepaymentDao().deleteRepaymentsForLoan(loan.id)
                        }
                    }
                }

                database.transactionDao().deleteTransactions(txsToDelete.map { it.id })
                affectedAccountIds.forEach { transactionRepository.recalculateAccountBalance(it) }

                recordUndoAction(
                    actionType = "DELETE_TRANSACTION",
                    description = if (txsToDelete.size > 1) "Deleted paired transfer '${tx.description.take(24)}'" else "Deleted transaction '${tx.description.take(24)}'",
                    previousTxs = txsToDelete
                )
                showMessage(if (txsToDelete.size > 1) "Paired transfer deleted" else "Transaction deleted")
            }
        }
    }

    fun deleteTransactions(ids: List<Long>) {
        viewModelScope.launch {
            val allExpenses = database.companyExpenseDao().getAllCompanyExpensesList()
            val allLoans = database.loanDao().getAllLoansList()
            val deletedList = mutableListOf<TransactionEntity>()
            val affectedAccountIds = mutableSetOf<Long>()

            ids.forEach { id ->
                val tx = repository.getTransactionById(id)
                if (tx != null && !deletedList.any { it.id == tx.id }) {
                    deletedList.add(tx)
                    affectedAccountIds.add(tx.accountId)

                    // Include paired transfer if any
                    if (!tx.transferId.isNullOrBlank()) {
                        val paired = database.transactionDao().getTransactionsByTransferId(tx.transferId)
                        paired.forEach { p ->
                            if (!deletedList.any { it.id == p.id }) {
                                deletedList.add(p)
                                affectedAccountIds.add(p.accountId)
                            }
                        }
                    }
                }
            }

            deletedList.forEach { tx ->
                if (tx.syncId.isNotBlank()) {
                    try {
                        database.syncDao().insertDeletedTransaction(DeletedTransactionEntity(tx.syncId))
                    } catch (_: Exception) {}
                }
                val txAmt = if (tx.amount > 0) tx.amount else maxOf(tx.debitAmount, tx.creditAmount)
                val matchedExp = allExpenses.filter { it.date == tx.transactionDate && Math.abs(it.amount - txAmt) < 0.01 }
                matchedExp.forEach { database.companyExpenseDao().deleteCompanyExpense(it.id) }

                // Cascade delete linked loans and repayments
                tx.linkedLoanId?.let { loanId ->
                    database.loanDao().deleteLoan(loanId)
                    database.loanRepaymentDao().deleteRepaymentsForLoan(loanId)
                }
                if (tx.transactionType == TransactionType.LENDING) {
                    val matchingLoans = allLoans.filter {
                        it.lentDate == tx.transactionDate && Math.abs(it.amount - txAmt) < 0.01
                    }
                    matchingLoans.forEach { loan ->
                        database.loanDao().deleteLoan(loan.id)
                        database.loanRepaymentDao().deleteRepaymentsForLoan(loan.id)
                    }
                }
            }

            database.transactionDao().deleteTransactions(deletedList.map { it.id })
            affectedAccountIds.forEach { transactionRepository.recalculateAccountBalance(it) }

            recordUndoAction(
                actionType = "BULK_DELETE",
                description = "Deleted ${deletedList.size} transactions",
                previousTxs = deletedList
            )
            showMessage("${deletedList.size} transactions deleted")
        }
    }

    fun deleteAllTransactions() {
        viewModelScope.launch {
            database.transactionDao().deleteAllTransactions()
            database.companyExpenseDao().deleteAllCompanyExpenses()
            database.loanDao().deleteAllLoans()
            database.loanRepaymentDao().deleteAllRepayments()
            database.syncDao().clearAllDeletedTransactions()
            _companyExpensePrompt.value = null
            _pendingCompanyExpenses.value = emptyList()
            showMessage("All transactions, statements, and lend/borrow records deleted")
        }
    }

    fun clearAllUserData(onComplete: (() -> Unit)? = null) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // 1. Purge all transactions, accounts, loans, official expenses, rules, undo history, sync data
                database.transactionDao().deleteAllTransactions()
                database.loanDao().deleteAllLoans()
                database.loanRepaymentDao().deleteAllRepayments()
                database.companyExpenseDao().deleteAllCompanyExpenses()
                database.accountDao().deleteAllAccounts()
                database.categorizationRuleDao().deleteAllRules()
                database.undoDao().clearUndoHistory()
                database.syncDao().clearAllDeletedTransactions()

                // 2. Re-seed clean essential default categories so they are NEVER lost on delete account
                database.categoryDao().deleteAllCategories()
                database.categoryDao().insertCategories(CategoryEntity.DEFAULT_CATEGORIES)

                // 3. Reset user profile to un-onboarded initial state
                val resetProfile = UserProfileEntity(
                    id = 1,
                    name = "User",
                    email = "",
                    profilePictureUrl = "",
                    currencySymbol = "₹",
                    isDarkMode = false,
                    isPrivacyBlurEnabled = true,
                    blurTimeoutSeconds = 5,
                    isOnboardingCompleted = false
                )
                database.userProfileDao().insertOrUpdateProfile(resetProfile)

                // 4. Reset in-memory transient states
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    _importPreview.value = null
                    _isAmountTemporarilyRevealed.value = false
                    _companyExpensePrompt.value = null
                    _smartRulePrompt.value = null
                    _pendingCompanyExpenses.value = emptyList()
                    _searchQuery.value = ""
                    _selectedTypeFilter.value = null
                    _selectedCategoryFilter.value = null
                    _selectedAccountFilter.value = null
                    _selectedMonthFilter.value = null
                    showMessage("All local data cleared.")
                    onComplete?.invoke()
                }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    showMessage("Error clearing data: ${e.message}")
                    onComplete?.invoke()
                }
            }
        }
    }

    fun createBackup(context: android.content.Context, onDone: (java.io.File?) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val file = com.example.utils.BackupService.createBackupJson(context, database)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    showMessage("Backup created successfully")
                    android.widget.Toast.makeText(context, "Backup file prepared", android.widget.Toast.LENGTH_SHORT).show()
                    onDone(file)
                }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    val err = "Failed to create backup: ${e.message}"
                    showMessage(err)
                    android.widget.Toast.makeText(context, err, android.widget.Toast.LENGTH_LONG).show()
                    onDone(null)
                }
            }
        }
    }

    fun saveBackupToStorageUri(context: android.content.Context, uri: android.net.Uri, onDone: (Boolean) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val success = com.example.utils.BackupService.saveBackupToStorageUri(context, uri, database)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                if (success) {
                    val msg = "Backup saved to storage successfully!"
                    showMessage(msg)
                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    val msg = "Failed to save backup to storage"
                    showMessage(msg)
                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                }
                onDone(success)
            }
        }
    }

    fun saveBackupToDownloads(context: android.content.Context, onDone: (Boolean) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val path = com.example.utils.BackupService.saveBackupToDownloads(context, database)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                if (path != null) {
                    val msg = "Backup saved to: $path"
                    showMessage(msg)
                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                    onDone(true)
                } else {
                    val msg = "Failed to save backup to Downloads"
                    showMessage(msg)
                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                    onDone(false)
                }
            }
        }
    }

    fun restoreBackup(context: android.content.Context, uri: android.net.Uri, onDone: (Boolean) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val jsonString = context.contentResolver.openInputStream(uri)?.use { it.reader(Charsets.UTF_8).readText() }
                if (jsonString.isNullOrBlank()) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        val msg = "Invalid or empty backup file"
                        showMessage(msg)
                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                        onDone(false)
                    }
                    return@launch
                }
                val result = com.example.utils.BackupService.restoreBackupJson(jsonString, database)
                if (result.success) {
                    recalculateAllAccountBalances()
                    scanForCompanyExpenses()
                }
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    showMessage(result.message)
                    android.widget.Toast.makeText(context, result.message, android.widget.Toast.LENGTH_LONG).show()
                    onDone(result.success)
                }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    val msg = "Error restoring backup: ${e.message}"
                    showMessage(msg)
                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                    onDone(false)
                }
            }
        }
    }

    // Loans Actions
    fun addLoan(
        personName: String,
        phone: String,
        amount: Double,
        lentDate: String,
        expectedDate: String?,
        notes: String,
        lendId: String? = null
    ) {
        viewModelScope.launch {
            val cleanLendId = lendId?.trim()?.takeIf { it.isNotBlank() }
            val finalLendId = if (cleanLendId != null) {
                if (isLendIdUnique(cleanLendId)) cleanLendId else generateNextLendId()
            } else generateNextLendId()

            val combinedNotes = if (notes.isNotBlank()) "#$finalLendId • $notes" else "#$finalLendId"
            val loan = LoanEntity(
                personName = personName,
                personPhone = phone,
                amount = amount,
                lentDate = lentDate,
                expectedRepaymentDate = expectedDate,
                amountRepaid = 0.0,
                remainingAmount = amount,
                status = LoanStatus.ACTIVE,
                notes = combinedNotes
            )
            val loanId = repository.insertLoan(loan)

            // Also create a linked transaction for accurate cash flow
            val primaryAcc = accounts.value.firstOrNull()?.id ?: 1
            repository.insertTransaction(
                TransactionEntity(
                    accountId = primaryAcc,
                    transactionDate = lentDate,
                    description = "Lent to $personName",
                    debitAmount = amount,
                    creditAmount = 0.0,
                    amount = amount,
                    transactionType = TransactionType.LENDING,
                    categoryName = "Friend - $personName",
                    linkedLoanId = loanId,
                    advanceId = finalLendId,
                    referenceNumber = finalLendId,
                    notes = notes
                )
            )
            showMessage("Lend record created ($finalLendId)")
        }
    }

    fun addBorrowRecord(
        personName: String,
        phone: String = "",
        amount: Double,
        borrowDate: String,
        expectedDate: String? = null,
        accountId: Long = accounts.value.firstOrNull()?.id ?: 1,
        borrowId: String? = null,
        notes: String = ""
    ) {
        viewModelScope.launch {
            val cleanBorrowId = borrowId?.trim()?.takeIf { it.isNotBlank() }
            val finalBorrowId = if (cleanBorrowId != null) {
                if (isBorrowIdUnique(cleanBorrowId)) cleanBorrowId else generateNextBorrowId()
            } else generateNextBorrowId()

            repository.insertTransaction(
                TransactionEntity(
                    accountId = accountId,
                    transactionDate = borrowDate,
                    description = "Borrowed from ${personName.trim()}",
                    debitAmount = 0.0,
                    creditAmount = amount,
                    amount = amount,
                    transactionType = TransactionType.BORROWING,
                    categoryName = "Borrow - ${personName.trim()}",
                    advanceId = finalBorrowId,
                    referenceNumber = finalBorrowId,
                    notes = notes
                )
            )
            showMessage("Borrow record created ($finalBorrowId)")
        }
    }

    fun recordRepayment(loanId: Long, amount: Double, date: String, method: String, notes: String) {
        viewModelScope.launch {
            repository.recordLoanRepayment(loanId, amount, date, method, notes)
            val loan = repository.getLoanById(loanId)
            val primaryAcc = accounts.value.firstOrNull()?.id ?: 1
            repository.insertTransaction(
                TransactionEntity(
                    accountId = primaryAcc,
                    transactionDate = date,
                    description = "Loan repayment from ${loan?.personName ?: "Friend"}",
                    debitAmount = 0.0,
                    creditAmount = amount,
                    amount = amount,
                    transactionType = TransactionType.INCOME,
                    categoryName = "Loan Repayment",
                    linkedLoanId = loanId,
                    notes = notes
                )
            )
            showMessage("Repayment of ₹$amount recorded")
        }
    }

    fun deleteRepayment(repaymentId: Long, loanId: Long) {
        viewModelScope.launch {
            repository.deleteRepayment(repaymentId, loanId)
            showMessage("Repayment deleted and loan balance updated")
        }
    }

    fun deleteLoan(id: Long) {
        viewModelScope.launch {
            database.loanRepaymentDao().deleteRepaymentsForLoan(id)
            val linkedTxs = database.transactionDao().getAllTransactionsList().filter { it.linkedLoanId == id }
            if (linkedTxs.isNotEmpty()) {
                database.transactionDao().deleteTransactions(linkedTxs.map { it.id })
            }
            repository.deleteLoan(id)
            showMessage("Loan and linked records deleted")
        }
    }

    // Company Expense Actions
    fun addCompanyExpense(
        date: String,
        amount: Double,
        reason: String,
        category: String,
        company: String,
        method: String,
        notes: String,
        advanceId: String? = null
    ) {
        viewModelScope.launch {
            val finalNotes = if (!advanceId.isNullOrBlank()) {
                val cleanAdv = advanceId.trim()
                val tag = if (cleanAdv.startsWith("#")) cleanAdv else "#$cleanAdv"
                if (notes.contains(cleanAdv, ignoreCase = true)) notes else if (notes.isBlank()) tag else "$notes $tag"
            } else {
                notes
            }
            val exp = CompanyExpenseEntity(
                date = date,
                amount = amount,
                reason = reason,
                category = category,
                companyName = company,
                paymentMethod = method,
                notes = finalNotes
            )
            repository.insertCompanyExpense(exp)
            showMessage("Company expense recorded" + (if (!advanceId.isNullOrBlank()) " ($advanceId)" else ""))
        }
    }

    fun linkCompanyExpenseToAdvance(expenseId: Long, advanceId: String?) {
        viewModelScope.launch {
            val exp = companyExpenses.value.find { it.id == expenseId }
                ?: database.companyExpenseDao().getAllCompanyExpensesList().find { it.id == expenseId }
            if (exp != null) {
                val cleanNotes = exp.notes.replace(Regex("""#(ADV[-_ ]*\d+)""", RegexOption.IGNORE_CASE), "").trim()
                val newNotes = if (!advanceId.isNullOrBlank()) {
                    val tag = if (advanceId.startsWith("#")) advanceId else "#$advanceId"
                    if (cleanNotes.isBlank()) tag else "$cleanNotes $tag"
                } else {
                    cleanNotes
                }
                val updated = exp.copy(notes = newNotes, updatedAt = System.currentTimeMillis())
                repository.updateCompanyExpense(updated)
                showMessage(if (!advanceId.isNullOrBlank()) "Expense linked to $advanceId" else "Unlinked from Advance")
            }
        }
    }

    fun updateCompanyExpense(expense: CompanyExpenseEntity) {
        viewModelScope.launch {
            repository.updateCompanyExpense(expense)
            showMessage("Company expense updated")
        }
    }

    fun toggleCompanyReimbursement(id: Long, currentStatus: Boolean) {
        viewModelScope.launch {
            repository.updateReimbursementStatus(id, !currentStatus)
            showMessage(if (!currentStatus) "Marked as reimbursed" else "Marked as pending reimbursement")
        }
    }

    fun markCompanyExpenseApplied(id: Long, applied: Boolean) {
        viewModelScope.launch {
            val exp = companyExpenses.value.find { it.id == id }
                ?: database.companyExpenseDao().getAllCompanyExpensesList().find { it.id == id }
            if (exp != null) {
                val cleanNotes = exp.notes.replace("#APPLIED", "", ignoreCase = true).replace("[APPLIED]", "", ignoreCase = true).trim()
                val newNotes = if (applied) {
                    if (cleanNotes.isBlank()) "#APPLIED" else "$cleanNotes #APPLIED"
                } else {
                    cleanNotes
                }
                val updated = exp.copy(isReimbursed = false, notes = newNotes, updatedAt = System.currentTimeMillis())
                repository.updateCompanyExpense(updated)
                showMessage(if (applied) "Claim marked as Applied" else "Claim moved to Pending")
            }
        }
    }

    fun markCompanyExpenseReimbursed(id: Long, reimbursed: Boolean) {
        viewModelScope.launch {
            val exp = companyExpenses.value.find { it.id == id }
                ?: database.companyExpenseDao().getAllCompanyExpensesList().find { it.id == id }
            if (exp != null) {
                val updated = exp.copy(isReimbursed = reimbursed, updatedAt = System.currentTimeMillis())
                repository.updateCompanyExpense(updated)
                showMessage(if (reimbursed) "Marked as Reimbursed" else "Moved to Pending Claims")
            }
        }
    }

    fun deleteCompanyExpense(id: Long) {
        viewModelScope.launch {
            repository.deleteCompanyExpense(id)
            showMessage("Company expense removed")
        }
    }

    // Rules Actions
    fun addRule(keyword: String, category: CategoryEntity, priority: Int, matchType: MatchType) {
        viewModelScope.launch {
            val resolvedType = when (category.type) {
                CategoryType.INCOME -> TransactionType.INCOME
                CategoryType.INVESTMENT -> TransactionType.INVESTMENT
                else -> TransactionType.EXPENSE
            }
            val rule = CategorizationRuleEntity(
                keyword = keyword.trim(),
                categoryId = category.id,
                categoryName = category.name,
                transactionType = resolvedType,
                priority = priority,
                matchType = matchType,
                isActive = true
            )
            val ruleId = repository.insertRule(rule)
            val (prev, new) = applyRuleToExistingTransactions(keyword.trim(), category.id, category.name, resolvedType)
            recordUndoAction(
                actionType = "ADD_RULE",
                description = "Added rule for '${keyword.trim()}' (applied to ${prev.size} txs)",
                previousTxs = prev,
                newTxs = new,
                relatedRuleId = ruleId,
                ruleSnapshot = rule.copy(id = ruleId)
            )
            showMessage("Rule added and applied for '$keyword'")
        }
    }

    fun toggleRule(id: Long, isActive: Boolean) {
        viewModelScope.launch {
            repository.toggleRule(id, isActive)
        }
    }

    fun deleteRule(id: Long) {
        viewModelScope.launch {
            try {
                val rule = database.categorizationRuleDao().getRuleById(id)
                if (rule != null) {
                    repository.deleteRule(id)
                    recordUndoAction(
                        actionType = "DELETE_RULE",
                        description = "Deleted rule '${rule.keyword}'",
                        relatedRuleId = id,
                        ruleSnapshot = rule
                    )
                }
            } catch (_: Exception) {}
            showMessage("Rule deleted")
        }
    }

    fun updateRule(rule: CategorizationRuleEntity) {
        viewModelScope.launch {
            try {
                val oldRule = database.categorizationRuleDao().getRuleById(rule.id)
                repository.updateRule(rule)
                val (prev, new) = applyRuleToExistingTransactions(rule.keyword, rule.categoryId, rule.categoryName, rule.transactionType)
                recordUndoAction(
                    actionType = "EDIT_RULE",
                    description = "Updated rule '${rule.keyword}'",
                    previousTxs = prev,
                    newTxs = new,
                    relatedRuleId = rule.id,
                    ruleSnapshot = oldRule ?: rule
                )
            } catch (_: Exception) {}
            showMessage("Rule updated")
        }
    }

    // Categories Actions
    fun addCategory(name: String, type: CategoryType, colorHex: String) {
        viewModelScope.launch {
            val cat = CategoryEntity(
                name = name.trim(),
                type = type,
                colorHex = colorHex
            )
            repository.insertCategory(cat)
            showMessage("Category '$name' created")
        }
    }

    fun deleteCategory(id: Long) {
        viewModelScope.launch {
            try {
                val cat = database.categoryDao().getCategoryById(id)
                if (cat != null) {
                    repository.deleteCategory(id)
                    // Revert matching transactions to Uncategorized
                    val list = database.transactionDao().getAllTransactionsList()
                    list.forEach { tx ->
                        if (tx.categoryId == id || tx.categoryName.equals(cat.name, ignoreCase = true)) {
                            // Preserve the transaction's existing type — do NOT derive from credit/debit amounts
                            database.transactionDao().updateTransactionCategory(tx.id, null, "Uncategorized", tx.transactionType)
                        }
                    }
                    // Delete any rules associated with this category
                    val rulesList = database.categorizationRuleDao().getActiveRulesList()
                    rulesList.forEach { rule ->
                        if (rule.categoryId == id || rule.categoryName.equals(cat.name, ignoreCase = true)) {
                            repository.deleteRule(rule.id)
                        }
                    }
                }
            } catch (_: Exception) {}
            showMessage("Category deleted")
        }
    }

    fun updateCategory(category: CategoryEntity) {
        viewModelScope.launch {
            try {
                repository.updateCategory(category)
                // Cascade update to transactions
                val list = database.transactionDao().getAllTransactionsList()
                list.forEach { tx ->
                    if (tx.categoryId == category.id) {
                        val resolvedType = when (category.type) {
                            com.example.data.local.entity.CategoryType.INCOME -> TransactionType.INCOME
                            com.example.data.local.entity.CategoryType.INVESTMENT -> TransactionType.INVESTMENT
                            else -> TransactionType.EXPENSE
                        }
                        database.transactionDao().updateTransactionCategory(tx.id, category.id, category.name, resolvedType)
                    }
                }
                // Cascade update to rules
                val rulesList = database.categorizationRuleDao().getActiveRulesList()
                rulesList.forEach { rule ->
                    if (rule.categoryId == category.id) {
                        val updatedRule = rule.copy(
                            categoryName = category.name,
                            transactionType = when (category.type) {
                                com.example.data.local.entity.CategoryType.INCOME -> TransactionType.INCOME
                                com.example.data.local.entity.CategoryType.INVESTMENT -> TransactionType.INVESTMENT
                                else -> TransactionType.EXPENSE
                            }
                        )
                        repository.updateRule(updatedRule)
                    }
                }
            } catch (_: Exception) {}
            showMessage("Category updated")
        }
    }

    // Account Actions
    fun recalculateAllAccountBalances() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val accList = database.accountDao().getAllAccountsList()
                accList.forEach { acc ->
                    transactionRepository.recalculateAccountBalance(acc.id)
                }
            } catch (_: Exception) {}
        }
    }

    fun addAccount(account: AccountEntity) {
        viewModelScope.launch {
            repository.insertAccount(account)
            recalculateAllAccountBalances()
            showMessage("Account '${account.accountName}' created")
        }
    }

    fun deleteAccount(id: Long) {
        viewModelScope.launch {
            repository.deleteAccount(id)
            recalculateAllAccountBalances()
            showMessage("Account deleted")
        }
    }

    fun updateAccount(account: com.example.data.local.entity.AccountEntity) {
        viewModelScope.launch {
            repository.updateAccount(account)
            recalculateAllAccountBalances()
            showMessage("Account updated")
        }
    }

    fun addAccount(name: String, bank: String, maskedNumber: String, type: AccountType, openingBalance: Double) {
        viewModelScope.launch {
            val acc = AccountEntity(
                accountName = name,
                bankName = bank,
                accountNumberMasked = maskedNumber,
                accountType = type,
                openingBalance = openingBalance,
                currentBalance = openingBalance
            )
            repository.insertAccount(acc)
            recalculateAllAccountBalances()
            showMessage("Account '$name' created")
        }
    }

    fun updateUserProfile(name: String, currencySymbol: String) {
        viewModelScope.launch {
            val current = userProfile.value ?: UserProfileEntity(id = 1, name = name, email = "", currencySymbol = currencySymbol)
            repository.updateProfile(current.copy(name = name, currencySymbol = currencySymbol))
        }
    }



    // Statement Import Workflow
    fun previewStatement(content: String, fileName: String = "bank_statement.csv") {
        viewModelScope.launch {
            val currentRules = repository.getActiveRulesList()
            val existingTxs = allTransactions.value
            val preview = StatementImportService.parseStatementText(content, currentRules, existingTxs, fileName)
            _importPreview.value = preview
        }
    }

    fun parseStatementUri(context: android.content.Context, uri: android.net.Uri, fileName: String = "statement.csv") {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                _isImporting.value = true
                val maxFileSize = 25 * 1024 * 1024 // 25MB maximum statement file size
                val bytes = context.contentResolver.openInputStream(uri)?.use { stream ->
                    val buffer = java.io.ByteArrayOutputStream()
                    val chunk = ByteArray(8192)
                    var totalRead = 0
                    var read: Int
                    while (stream.read(chunk).also { read = it } != -1) {
                        totalRead += read
                        if (totalRead > maxFileSize) {
                            throw IllegalArgumentException("File exceeds maximum allowed size of 25MB.")
                        }
                        buffer.write(chunk, 0, read)
                    }
                    buffer.toByteArray()
                }
                if (bytes == null || bytes.isEmpty()) {
                    showMessage("Could not read file. File appears to be empty.")
                    return@launch
                }
                val currentRules = repository.getActiveRulesList()
                val existingTxs = allTransactions.value
                val resolvedFileName = if (fileName.contains('.')) fileName else {
                    when {
                        bytes.size >= 4 && bytes[0] == 0x25.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x44.toByte() && bytes[3] == 0x46.toByte() -> "$fileName.pdf"
                        bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() && bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte() -> "$fileName.xlsx"
                        bytes.size >= 4 && bytes[0] == 0xD0.toByte() && bytes[1] == 0xCF.toByte() && bytes[2] == 0x11.toByte() && bytes[3] == 0xE0.toByte() -> "$fileName.xls"
                        else -> "$fileName.csv"
                    }
                }
                val preview = StatementImportService.parseStatementBytes(context, bytes, resolvedFileName, currentRules, existingTxs)
                if (preview.validRows.isEmpty()) {
                    showMessage("No valid transaction rows found in '$resolvedFileName'. Please ensure it's a supported bank statement.")
                }
                _importPreview.value = preview
            } catch (e: Exception) {
                if (com.example.BuildConfig.DEBUG) {
                    android.util.Log.e("FinanceViewModel", "Statement import failed", e)
                }
                showMessage("Could not parse statement: ${e.localizedMessage ?: "Invalid or corrupted format"}")
            } finally {
                _isImporting.value = false
            }
        }
    }

    fun confirmImport(skipDuplicates: Boolean = false, targetAccountId: Long = 1) {
        val preview = _importPreview.value ?: return
        viewModelScope.launch {
            try {
                _isImporting.value = true
                val rowsToImport = if (skipDuplicates) preview.validRows.filterNot { it.isDuplicate } else preview.validRows

                val txEntities = rowsToImport.map { row ->
                    val containsCompany = row.description.contains("company", ignoreCase = true)
                    var finalCatId = row.suggestedCategoryId
                    var finalCatName = row.suggestedCategory
                    var finalType = row.suggestedType

                    if (containsCompany) {
                        val catName = "Official Expense"
                        val existingCat = database.categoryDao().getCategoryByName(catName)
                        val categoryId = if (existingCat != null) {
                            existingCat.id
                        } else {
                            database.categoryDao().insertCategory(
                                CategoryEntity(
                                    name = catName,
                                    type = com.example.data.local.entity.CategoryType.EXPENSE,
                                    colorHex = "#06B6D4"
                                )
                            )
                        }
                        finalCatId = categoryId
                        finalCatName = catName
                        finalType = TransactionType.EXPENSE
                    }

                    TransactionEntity(
                        accountId = targetAccountId,
                        transactionDate = row.date,
                        description = row.description,
                        debitAmount = row.debitAmount,
                        creditAmount = row.creditAmount,
                        amount = row.amount,
                        transactionType = finalType,
                        balanceAfterTransaction = row.balance,
                        categoryId = finalCatId,
                        categoryName = finalCatName,
                        source = "IMPORT_EXCEL",
                        referenceNumber = row.referenceNumber,
                        isManual = false,
                        isCategorized = if (containsCompany) true else (row.confidence > 0f),
                        categorizationConfidence = if (containsCompany) 1.0f else row.confidence
                    )
                }

                repository.insertTransactions(txEntities)
                transactionRepository.recalculateAccountBalance(targetAccountId)
                recalculateAllAccountBalances()
                _importPreview.value = null
                showMessage("${txEntities.size} transactions imported successfully!")
                scanForCompanyExpenses()
            } catch (e: Exception) {
                if (com.example.BuildConfig.DEBUG) {
                    android.util.Log.e("FinanceViewModel", "Confirm import failed", e)
                }
                showMessage("Import failed: ${e.localizedMessage ?: "Database error"}")
            } finally {
                _isImporting.value = false
            }
        }
    }

    fun clearImportPreview() {
        _importPreview.value = null
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

    // Export Reports
    fun getTransactionsCsvExport(): String = ExportService.exportTransactionsCsv(allTransactions.value)
    fun getLoansCsvExport(): String = ExportService.exportLoansCsv(loans.value)
    fun getCompanyExpensesCsvExport(): String = ExportService.exportCompanyExpensesCsv(companyExpenses.value)
    fun getFullReportSummaryText(): String = ExportService.exportFinancialReportSummary(
        dashboardSummary.value,
        categoryBreakdown.value,
        monthlyTrends.value
    )

    // ==========================================
    // EXCEL TWO-WAY SYNC STATE & METHODS
    // ==========================================
    private val _selectedExcelUri = MutableStateFlow<String?>(null)
    val selectedExcelUri: StateFlow<String?> = _selectedExcelUri.asStateFlow()

    private val _selectedExcelFileName = MutableStateFlow<String?>(null)
    val selectedExcelFileName: StateFlow<String?> = _selectedExcelFileName.asStateFlow()

    private val _lastSyncTime = MutableStateFlow<Long?>(null)
    val lastSyncTime: StateFlow<Long?> = _lastSyncTime.asStateFlow()

    private val _syncStatus = MutableStateFlow<String>("Idle")
    val syncStatus: StateFlow<String> = _syncStatus.asStateFlow()

    private val _isSyncing = MutableStateFlow<Boolean>(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncResult = MutableStateFlow<SyncResult?>(null)
    val lastSyncResult: StateFlow<SyncResult?> = _lastSyncResult.asStateFlow()

    private val _activeConflicts = MutableStateFlow<List<SyncConflict>>(emptyList())
    val activeConflicts: StateFlow<List<SyncConflict>> = _activeConflicts.asStateFlow()

    private val _excelPreviewRows = MutableStateFlow<List<ExcelTransactionRow>>(emptyList())
    val excelPreviewRows: StateFlow<List<ExcelTransactionRow>> = _excelPreviewRows.asStateFlow()

    fun loadSyncMetadata() {
        viewModelScope.launch {
            try {
                _selectedExcelUri.value = database.syncDao().getMetadataValue("selected_excel_uri")
                _selectedExcelFileName.value = database.syncDao().getMetadataValue("selected_excel_filename")
                val lastTs = database.syncDao().getMetadataValue("last_sync_timestamp")?.toLongOrNull()
                _lastSyncTime.value = lastTs
            } catch (_: Exception) {}
        }
    }

    fun setSelectedExcelFile(uriString: String, fileName: String) {
        viewModelScope.launch {
            _selectedExcelUri.value = uriString
            _selectedExcelFileName.value = fileName
            try {
                database.syncDao().setMetadata(SyncMetadataEntity("selected_excel_uri", uriString))
                database.syncDao().setMetadata(SyncMetadataEntity("selected_excel_filename", fileName))
            } catch (_: Exception) {}
        }
    }

    fun loadExcelPreview(context: android.content.Context, uri: android.net.Uri) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null && bytes.isNotEmpty()) {
                    val rows = ExcelSyncService.parseExcelWorkbook(bytes)
                    _excelPreviewRows.value = rows
                }
            } catch (e: Exception) {
                if (com.example.BuildConfig.DEBUG) {
                    android.util.Log.e("FinanceViewModel", "Preview Excel failed", e)
                }
            }
        }
    }

    fun exportToExcel(context: android.content.Context, uri: android.net.Uri) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _isSyncing.value = true
            _syncStatus.value = "Exporting full workbook (Transactions, Categories, Rules, Accounts)..."
            try {
                val transactions = database.transactionDao().getAllTransactionsList()
                val accounts = database.accountDao().getAllAccountsList()
                val categories = database.categoryDao().getAllCategoriesList()
                val rules = database.categorizationRuleDao().getAllRulesList()

                val bytes = ExcelSyncService.generateExcelWorkbook(transactions, accounts, categories, rules)
                context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                    out.write(bytes)
                    out.flush()
                }

                val now = System.currentTimeMillis()
                _lastSyncTime.value = now
                database.syncDao().setMetadata(SyncMetadataEntity("last_sync_timestamp", now.toString()))
                database.syncDao().clearAllDeletedTransactions()

                val result = SyncResult(
                    addedInExcel = transactions.size,
                    categoriesAdded = categories.size,
                    rulesAdded = rules.size,
                    message = "Exported ${transactions.size} transactions, ${categories.size} categories & ${rules.size} rules to Excel successfully."
                )
                _lastSyncResult.value = result
                _syncStatus.value = "Export completed successfully"
                showMessage("Exported full backup to Excel (${transactions.size} txs, ${categories.size} categories, ${rules.size} rules)")
            } catch (e: Exception) {
                if (com.example.BuildConfig.DEBUG) {
                    android.util.Log.e("FinanceViewModel", "Export to Excel failed", e)
                }
                _syncStatus.value = "Export failed: ${e.localizedMessage ?: "File write error"}"
                showMessage("Export failed: ${e.localizedMessage}")
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun importChangesFromExcel(context: android.content.Context, uri: android.net.Uri) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _isSyncing.value = true
            _syncStatus.value = "Importing changes from Excel..."
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes == null || bytes.isEmpty()) {
                    _syncStatus.value = "Failed to read Excel file"
                    showMessage("Selected file is empty")
                    return@launch
                }

                val parsedData = ExcelSyncService.parseFullWorkbook(bytes)
                val currentTxs = database.transactionDao().getAllTransactionsList()
                val deletedSyncIds = database.syncDao().getAllDeletedSyncIds()
                val accounts = database.accountDao().getAllAccountsList()
                val categories = database.categoryDao().getAllCategoriesList()
                val rules = database.categorizationRuleDao().getAllRulesList()
                val lastTs = _lastSyncTime.value ?: 0L

                val result = ExcelSyncService.reconcile(
                    appTransactions = currentTxs,
                    excelWorkbookData = parsedData,
                    deletedSyncIds = deletedSyncIds,
                    lastSyncTimestamp = lastTs,
                    accounts = accounts,
                    categories = categories,
                    rules = rules
                )

                if (result.conflicts.isNotEmpty()) {
                    _activeConflicts.value = result.conflicts
                    _lastSyncResult.value = result
                    _syncStatus.value = "${result.conflicts.size} conflicts detected"
                    showMessage("${result.conflicts.size} conflicts need resolution")
                } else {
                    applySyncResult(result, context, uri)
                }
            } catch (e: Exception) {
                if (com.example.BuildConfig.DEBUG) {
                    android.util.Log.e("FinanceViewModel", "Import from Excel failed", e)
                }
                _syncStatus.value = "Import failed: ${e.localizedMessage ?: "Parse error"}"
                showMessage("Import failed: ${e.localizedMessage}")
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun performTwoWaySync(context: android.content.Context, uri: android.net.Uri) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _isSyncing.value = true
            _syncStatus.value = "Synchronizing with Excel (Transactions, Categories, Rules)..."
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes == null || bytes.isEmpty()) {
                    exportToExcel(context, uri)
                    return@launch
                }

                val parsedData = ExcelSyncService.parseFullWorkbook(bytes)
                val currentTxs = database.transactionDao().getAllTransactionsList()
                val deletedSyncIds = database.syncDao().getAllDeletedSyncIds()
                val accounts = database.accountDao().getAllAccountsList()
                val categories = database.categoryDao().getAllCategoriesList()
                val rules = database.categorizationRuleDao().getAllRulesList()
                val lastTs = _lastSyncTime.value ?: 0L

                val result = ExcelSyncService.reconcile(
                    appTransactions = currentTxs,
                    excelWorkbookData = parsedData,
                    deletedSyncIds = deletedSyncIds,
                    lastSyncTimestamp = lastTs,
                    accounts = accounts,
                    categories = categories,
                    rules = rules
                )

                if (result.conflicts.isNotEmpty()) {
                    _activeConflicts.value = result.conflicts
                    _lastSyncResult.value = result
                    _syncStatus.value = "${result.conflicts.size} conflicts detected"
                    showMessage("${result.conflicts.size} conflicts need resolution")
                } else {
                    applySyncResult(result, context, uri)
                }
            } catch (e: Exception) {
                if (com.example.BuildConfig.DEBUG) {
                    android.util.Log.e("FinanceViewModel", "Two-way sync failed", e)
                }
                _syncStatus.value = "Sync failed: ${e.localizedMessage ?: "Sync error"}"
                showMessage("Sync failed: ${e.localizedMessage}")
            } finally {
                _isSyncing.value = false
            }
        }
    }

    private suspend fun applySyncResult(result: SyncResult, context: android.content.Context, uri: android.net.Uri) {
        // 1. Sync Categories
        if (result.newCategoriesForApp.isNotEmpty()) {
            database.categoryDao().insertCategories(result.newCategoriesForApp)
        }
        for (cat in result.updatedCategoriesForApp) {
            database.categoryDao().updateCategory(cat)
        }

        // 2. Sync Rules
        if (result.newRulesForApp.isNotEmpty()) {
            database.categorizationRuleDao().insertRules(result.newRulesForApp)
        }
        for (rule in result.updatedRulesForApp) {
            database.categorizationRuleDao().updateRule(rule)
        }

        // Fetch fresh categories from DB so we have all true autogenerated IDs
        val freshCategories = database.categoryDao().getAllCategoriesList()
        val catMap = freshCategories.associateBy { it.name.trim().lowercase() }

        // 3. Sync Transactions with resolved category IDs
        if (result.newTransactionsForApp.isNotEmpty()) {
            val resolvedNew = result.newTransactionsForApp.map { tx ->
                val realCatId = catMap[tx.categoryName.trim().lowercase()]?.id ?: tx.categoryId
                tx.copy(categoryId = realCatId)
            }
            database.transactionDao().insertTransactions(resolvedNew)
        }
        for (tx in result.updatedTransactionsForApp) {
            val realCatId = catMap[tx.categoryName.trim().lowercase()]?.id ?: tx.categoryId
            database.transactionDao().updateTransaction(tx.copy(categoryId = realCatId))
        }
        for (syncId in result.deleteSyncIdsFromApp) {
            val tx = database.transactionDao().getTransactionBySyncId(syncId)
            if (tx != null) {
                database.transactionDao().deleteTransaction(tx.id)
            }
        }

        // 4. Update balances for all accounts
        val freshRules = database.categorizationRuleDao().getAllRulesList()
        val freshAccounts = database.accountDao().getAllAccountsList()
        freshAccounts.forEach { acc ->
            transactionRepository.recalculateAccountBalance(acc.id)
        }

        // 5. Update in-memory preview with latest reconciled rows
        val allFreshTxs = database.transactionDao().getAllTransactionsList()
        val accMap = freshAccounts.associate { it.id to (it.bankName.ifBlank { it.accountName }) }
        val freshExcelRows = allFreshTxs.map { tx ->
            val accountName = accMap[tx.accountId] ?: "Primary Account"
            val actualAmount = if (tx.amount > 0) tx.amount else if (tx.debitAmount > 0) tx.debitAmount else tx.creditAmount
            ExcelTransactionRow(
                syncId = tx.syncId.ifBlank { tx.id.toString() },
                date = tx.transactionDate,
                description = tx.description,
                amount = actualAmount,
                type = tx.transactionType,
                category = tx.categoryName,
                account = accountName,
                notes = tx.notes,
                runningBalance = tx.balanceAfterTransaction,
                lastModified = tx.updatedAt
            )
        }

        // 6. Update sync metadata & preview
        val now = System.currentTimeMillis()
        _lastSyncTime.value = now
        database.syncDao().setMetadata(SyncMetadataEntity("last_sync_timestamp", now.toString()))
        _lastSyncResult.value = result
        _excelPreviewRows.value = freshExcelRows
        _activeConflicts.value = emptyList()
        _syncStatus.value = "Sync completed successfully"
        showMessage(result.message)
        scanForCompanyExpenses()
    }

    fun resolveConflict(conflict: SyncConflict, keepApp: Boolean, context: android.content.Context, uri: android.net.Uri?) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                if (keepApp) {
                    val updated = conflict.appTransaction.copy(updatedAt = System.currentTimeMillis())
                    database.transactionDao().updateTransaction(updated)
                } else {
                    val excelRow = conflict.excelRow
                    val isCredit = excelRow.type == TransactionType.INCOME || excelRow.type == TransactionType.REFUND
                    val accounts = database.accountDao().getAllAccountsList()
                    val categories = database.categoryDao().getAllCategoriesList()
                    val catId = categories.firstOrNull { it.name.equals(excelRow.category, ignoreCase = true) }?.id
                    val accId = accounts.firstOrNull { (it.bankName.ifBlank { it.accountName }).equals(excelRow.account, ignoreCase = true) }?.id ?: conflict.appTransaction.accountId

                    val updatedTx = conflict.appTransaction.copy(
                        transactionDate = excelRow.date,
                        description = excelRow.description,
                        amount = excelRow.amount,
                        debitAmount = if (isCredit) 0.0 else excelRow.amount,
                        creditAmount = if (isCredit) excelRow.amount else 0.0,
                        transactionType = excelRow.type,
                        categoryName = excelRow.category,
                        categoryId = catId ?: conflict.appTransaction.categoryId,
                        accountId = accId,
                        notes = excelRow.notes,
                        balanceAfterTransaction = excelRow.runningBalance ?: conflict.appTransaction.balanceAfterTransaction,
                        updatedAt = System.currentTimeMillis()
                    )
                    database.transactionDao().updateTransaction(updatedTx)
                }

                val remaining = _activeConflicts.value.filter { it.syncId != conflict.syncId }
                _activeConflicts.value = remaining

                if (remaining.isEmpty() && uri != null) {
                    performTwoWaySync(context, uri)
                }
            } catch (e: Exception) {
                if (com.example.BuildConfig.DEBUG) {
                    android.util.Log.e("FinanceViewModel", "Conflict resolution failed", e)
                }
            }
        }
    }
}

private data class FilterParams(
    val txs: List<TransactionEntity>,
    val query: String,
    val typeFilter: TransactionType?,
    val catFilter: String?,
    val accFilter: Long?
)
