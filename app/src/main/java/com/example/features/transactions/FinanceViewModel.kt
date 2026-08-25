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
import com.example.features.transactions.data.TransactionRepository
import com.example.features.lending.*
import com.example.features.lending.data.LendingRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

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

    // Derived Category Breakdown
    val categoryBreakdown: StateFlow<List<CategoryExpenseItem>> = combine(
        allTransactions,
        categories
    ) { txs, cats ->
        FinancialCalculationService.calculateCategoryExpenseBreakdown(txs, cats)
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
                    tx.referenceNumber.contains(params.query, ignoreCase = true)
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
        notes: String
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
                isManual = true,
                isCategorized = true,
                categorizationConfidence = 1.0f
            )
            repository.insertTransaction(tx)
            showMessage("Transaction added successfully")
            scanForCompanyExpenses()
        }
    }

    fun updateTransactionCategory(tx: TransactionEntity, newCategory: CategoryEntity) {
        viewModelScope.launch {
            val resolvedType = when (newCategory.type) {
                com.example.data.local.entity.CategoryType.INCOME -> TransactionType.INCOME
                com.example.data.local.entity.CategoryType.INVESTMENT -> TransactionType.INVESTMENT
                else -> TransactionType.EXPENSE
            }
            repository.updateTransactionCategory(tx.id, newCategory.id, newCategory.name, resolvedType)
            showMessage("Category updated to ${newCategory.name}")

            // Smart Rule Check: Don't prompt if a rule already covers this description or category
            val activeRules = repository.

            getActiveRulesList()
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

    fun updateTransactionDescription(txId: Long, newDescription: String) {
        viewModelScope.launch {
            val tx = repository.getTransactionById(txId)
            if (tx != null) {
                val containsCompany = newDescription.contains("company", ignoreCase = true)
                var updatedTx = tx.copy(description = newDescription)
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
                showMessage("Description updated successfully")
                scanForCompanyExpenses()
            }
        }
    }

    private suspend fun applyRuleToExistingTransactions(keyword: String, categoryId: Long?, categoryName: String, transactionType: TransactionType) {
        try {
            val list = database.transactionDao().getAllTransactionsList()
            val normalizedKeyword = CategorizationEngine.normalize(keyword)
            if (normalizedKeyword.isBlank()) return
            list.forEach { tx ->
                val normalizedDesc = CategorizationEngine.normalize(tx.description)
                if (normalizedDesc.contains(normalizedKeyword)) {
                    database.transactionDao().updateTransactionCategory(tx.id, categoryId, categoryName, transactionType)
                }
            }
        } catch (_: Exception) {}
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
            repository.insertRule(rule)
            applyRuleToExistingTransactions(prompt.keyword, prompt.categoryId, prompt.categoryName, prompt.transactionType)
            _smartRulePrompt.value = null
            showMessage("Auto-categorization rule created and applied for '${prompt.keyword}'")
        }
    }

    fun dismissSmartRule() {
        _smartRulePrompt.value = null
    }

    fun deleteTransaction(id: Long) {
        viewModelScope.launch {
            repository.deleteTransaction(id)
            showMessage("Transaction deleted")
        }
    }

    fun deleteAllTransactions() {
        viewModelScope.launch {
            database.transactionDao().deleteAllTransactions()
            showMessage("All transactions deleted")
        }
    }

    fun clearAllUserData() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            database.clearAllTables()
            showMessage("All local data cleared.")
        }
    }

    // Loans Actions
    fun addLoan(
        personName: String,
        phone: String,
        amount: Double,
        lentDate: String,
        expectedDate: String?,
        notes: String
    ) {
        viewModelScope.launch {
            val loan = LoanEntity(
                personName = personName,
                personPhone = phone,
                amount = amount,
                lentDate = lentDate,
                expectedRepaymentDate = expectedDate,
                amountRepaid = 0.0,
                remainingAmount = amount,
                status = LoanStatus.ACTIVE,
                notes = notes
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
                    notes = notes
                )
            )
            showMessage("Loan recorded for $personName")
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

    fun deleteLoan(id: Long) {
        viewModelScope.launch {
            repository.deleteLoan(id)
            showMessage("Loan record deleted")
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
        notes: String
    ) {
        viewModelScope.launch {
            val exp = CompanyExpenseEntity(
                date = date,
                amount = amount,
                reason = reason,
                category = category,
                companyName = company,
                paymentMethod = method,
                notes = notes
            )
            repository.insertCompanyExpense(exp)
            showMessage("Company expense recorded")
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
            repository.insertRule(rule)
            applyRuleToExistingTransactions(keyword.trim(), category.id, category.name, resolvedType)
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
                    // Revert matching transactions to Uncategorized
                    val list = database.transactionDao().getAllTransactionsList()
                    val normalizedKeyword = CategorizationEngine.normalize(rule.keyword)
                    if (normalizedKeyword.isNotBlank()) {
                        list.forEach { tx ->
                            val normalizedDesc = CategorizationEngine.normalize(tx.description)
                            if (normalizedDesc.contains(normalizedKeyword) && tx.categoryName.equals(rule.categoryName, ignoreCase = true)) {
                                // Preserve the transaction's existing type — do NOT derive from credit/debit amounts
                                database.transactionDao().updateTransactionCategory(tx.id, null, "Uncategorized", tx.transactionType)
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
            showMessage("Rule deleted")
        }
    }

    fun updateRule(rule: CategorizationRuleEntity) {
        viewModelScope.launch {
            try {
                repository.updateRule(rule)
                applyRuleToExistingTransactions(rule.keyword, rule.categoryId, rule.categoryName, rule.transactionType)
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
    fun addAccount(account: AccountEntity) {
        viewModelScope.launch {
            repository.insertAccount(account)
            showMessage("Account '${account.accountName}' created")
        }
    }

    fun deleteAccount(id: Long) {
        viewModelScope.launch {
            repository.deleteAccount(id)
            showMessage("Account deleted")
        }
    }

    fun updateAccount(account: com.example.data.local.entity.AccountEntity) {
        viewModelScope.launch {
            repository.updateAccount(account)
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
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes == null || bytes.isEmpty()) {
                    showMessage("Could not read file. File appears to be empty.")
                    return@launch
                }
                val currentRules = repository.getActiveRulesList()
                val existingTxs = allTransactions.value
                val preview = StatementImportService.parseStatementBytes(bytes, fileName, currentRules, existingTxs)
                if (preview.validRows.isEmpty()) {
                    showMessage("No valid transaction rows found in '$fileName'. Please ensure it's a supported Excel bank statement.")
                }
                _importPreview.value = preview
            } catch (e: Exception) {
                e.printStackTrace()
                showMessage("Could not parse statement: ${e.localizedMessage ?: "Invalid or corrupted Excel format"}")
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
                _importPreview.value = null
                showMessage("${txEntities.size} transactions imported successfully!")
                scanForCompanyExpenses()
            } catch (e: Exception) {
                e.printStackTrace()
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
                e.printStackTrace()
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
}

private data class FilterParams(
    val txs: List<TransactionEntity>,
    val query: String,
    val typeFilter: TransactionType?,
    val catFilter: String?,
    val accFilter: Long?
)
