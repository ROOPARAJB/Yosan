package com.example.data.local

import com.example.data.local.entity.*

object DatabaseInitializer {

    suspend fun purgeLegacyDemoData(database: AppDatabase) {
        val accountDao = database.accountDao()
        val categoryDao = database.categoryDao()
        val ruleDao = database.categorizationRuleDao()
        val profileDao = database.userProfileDao()
        val loanDao = database.loanDao()
        val companyExpenseDao = database.companyExpenseDao()
        val transactionDao = database.transactionDao()

        // Purge all old demo data from existing database tables
        transactionDao.deleteAllTransactions()
        loanDao.deleteAllLoans()
        companyExpenseDao.deleteAllCompanyExpenses()
        accountDao.deleteAllAccounts()
        categoryDao.deleteAllCategories()
        ruleDao.deleteAllRules()

        // Re-seed clean essential defaults
        seedInitialData(database)
    }

    suspend fun seedInitialData(database: AppDatabase) {
        val accountDao = database.accountDao()
        val categoryDao = database.categoryDao()
        val ruleDao = database.categorizationRuleDao()
        val profileDao = database.userProfileDao()

        // 1. Seed Profile if none exists
        if (profileDao.getUserProfileOnce() == null) {
            profileDao.insertOrUpdateProfile(
                UserProfileEntity(
                    id = 1,
                    name = "User",
                    email = "",
                    currencySymbol = "₹",
                    isDarkMode = false
                )
            )
        }


        // 2. Seed Default Primary Account (removed - user adds manually)
        // 3. Seed Essential Default Categories (removed - user adds manually)
    }
}
