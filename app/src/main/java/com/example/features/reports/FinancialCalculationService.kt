package com.example.features.reports

import com.example.data.local.entity.*
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

data class DashboardSummary(
    val currentBalance: Double = 0.0,
    val totalIncome: Double = 0.0,
    val totalExpense: Double = 0.0,
    val netSavings: Double = 0.0,
    val moneyLent: Double = 0.0,
    val totalRepaid: Double = 0.0,
    val totalOutstanding: Double = 0.0,
    val companyExpense: Double = 0.0,
    val transactionCount: Int = 0
)

data class CategoryExpenseItem(
    val categoryName: String,
    val amount: Double,
    val percentage: Float,
    val colorHex: String,
    val count: Int
)

data class MonthlyTrendItem(
    val monthKey: String, // e.g. "2026-02"
    val monthLabel: String, // e.g. "Feb 26"
    val income: Double,
    val expense: Double,
    val savings: Double
)

data class PersonLendingSummary(
    val personName: String,
    val personPhone: String,
    val totalLent: Double,
    val totalRepaid: Double,
    val outstanding: Double,
    val loansCount: Int,
    val status: LoanStatus
)

enum class InsightType { POSITIVE, WARNING, INFO }

data class FinancialInsight(
    val title: String,
    val message: String,
    val type: InsightType
)

object FinancialCalculationService {

    fun calculateSummary(
        transactions: List<TransactionEntity>,
        loans: List<LoanEntity>,
        companyExpenses: List<CompanyExpenseEntity>,
        accounts: List<AccountEntity>
    ): DashboardSummary {
        // Helper: resolve actual debit value from a transaction regardless of how it was stored
        fun resolveDebit(tx: TransactionEntity): Double {
            return when {
                tx.debitAmount > 0 -> tx.debitAmount
                tx.transactionType == TransactionType.EXPENSE && tx.amount > 0 -> tx.amount
                tx.transactionType == TransactionType.LENDING && tx.amount > 0 -> tx.amount
                tx.transactionType == TransactionType.INVESTMENT && tx.amount > 0 -> tx.amount
                else -> 0.0
            }
        }

        // Helper: resolve actual credit value from a transaction regardless of how it was stored
        fun resolveCredit(tx: TransactionEntity): Double {
            return when {
                tx.creditAmount > 0 -> tx.creditAmount
                tx.transactionType == TransactionType.INCOME && tx.amount > 0 -> tx.amount
                tx.transactionType == TransactionType.REFUND && tx.amount > 0 -> tx.amount
                else -> 0.0
            }
        }

        // Income = SUM of credits classified as INCOME or REFUND
        val totalIncome = com.example.utils.CurrencyFormatter.roundFinancialAmount(
            transactions
                .filter { it.transactionType == TransactionType.INCOME || it.transactionType == TransactionType.REFUND }
                .sumOf { resolveCredit(it) }
        )

        // Expense = SUM of debits classified as EXPENSE
        val totalExpense = com.example.utils.CurrencyFormatter.roundFinancialAmount(
            transactions
                .filter { it.transactionType == TransactionType.EXPENSE }
                .sumOf { resolveDebit(it) }
        )

        // Net Savings
        val netSavings = com.example.utils.CurrencyFormatter.roundFinancialAmount(totalIncome - totalExpense)

        // Money Lent
        val moneyLent = com.example.utils.CurrencyFormatter.roundFinancialAmount(loans.sumOf { it.amount })

        // Repaid
        val totalRepaid = com.example.utils.CurrencyFormatter.roundFinancialAmount(loans.sumOf { it.amountRepaid })

        // Outstanding
        val totalOutstanding = com.example.utils.CurrencyFormatter.roundFinancialAmount(loans.sumOf { it.remainingAmount.coerceAtLeast(0.0) })

        // Company Expenses
        val totalCompanyExpense = com.example.utils.CurrencyFormatter.roundFinancialAmount(companyExpenses.sumOf { it.amount })

        // Current Balance: use latest balanceAfterTransaction if available (most accurate for imported statements)
        // Otherwise fall back to openingBalance + credits - debits per account
        val totalAccountBalance = com.example.utils.CurrencyFormatter.roundFinancialAmount(
            if (accounts.isNotEmpty()) {
                accounts.sumOf { acc ->
                    val accTxs = transactions.filter { it.accountId == acc.id }
                    // Prefer the running balance from the last imported statement row
                    val latestRunningBalance = accTxs
                        .filter { it.balanceAfterTransaction != null }
                        .maxByOrNull { it.transactionDate + it.createdAt }
                        ?.balanceAfterTransaction
                    latestRunningBalance
                        ?: run {
                            val totalCredits = accTxs.sumOf { resolveCredit(it) }
                            val totalDebits = accTxs.sumOf { resolveDebit(it) }
                            acc.openingBalance + totalCredits - totalDebits
                        }
                }
            } else {
                // No accounts set up: use latest statement running balance or net
                val latestBalance = transactions
                    .filter { it.balanceAfterTransaction != null }
                    .maxByOrNull { it.transactionDate + it.createdAt }
                    ?.balanceAfterTransaction
                latestBalance ?: (totalIncome - totalExpense)
            }
        )

        return DashboardSummary(
            currentBalance = totalAccountBalance,
            totalIncome = totalIncome,
            totalExpense = totalExpense,
            netSavings = netSavings,
            moneyLent = moneyLent,
            totalRepaid = totalRepaid,
            totalOutstanding = totalOutstanding,
            companyExpense = totalCompanyExpense,
            transactionCount = transactions.size
        )
    }


    fun calculateCategoryExpenseBreakdown(
        transactions: List<TransactionEntity>,
        categoryEntities: List<CategoryEntity>
    ): List<CategoryExpenseItem> {
        val expenseTransactions = transactions.filter { it.transactionType == TransactionType.EXPENSE }
        val totalExpense = expenseTransactions.sumOf { if (it.debitAmount > 0) it.debitAmount else it.amount }

        val categoryColorMap = categoryEntities.associate { it.name.lowercase() to it.colorHex }

        val palette = listOf(
            "#EF4444", "#F59E0B", "#10B981", "#3B82F6", "#8B5CF6",
            "#EC4899", "#F97316", "#06B6D4", "#64748B", "#84CC16"
        )

        return expenseTransactions
            .groupBy { it.categoryName }
            .map { (catName, txs) ->
                val amount = txs.sumOf { if (it.debitAmount > 0) it.debitAmount else it.amount }
                val percentage = if (totalExpense > 0) ((amount / totalExpense) * 100).toFloat() else 0f
                val color = categoryColorMap[catName.lowercase()]
                    ?: palette[Math.abs(catName.hashCode()) % palette.size]
                CategoryExpenseItem(
                    categoryName = catName,
                    amount = amount,
                    percentage = percentage,
                    colorHex = color,
                    count = txs.size
                )
            }
            .sortedByDescending { it.amount }
    }

    fun calculateCategoryIncomeBreakdown(
        transactions: List<TransactionEntity>,
        categoryEntities: List<CategoryEntity>
    ): List<CategoryExpenseItem> {
        val incomeTransactions = transactions.filter { it.transactionType == TransactionType.INCOME || it.transactionType == TransactionType.REFUND }
        val totalIncome = incomeTransactions.sumOf { if (it.creditAmount > 0) it.creditAmount else it.amount }

        val categoryColorMap = categoryEntities.associate { it.name.lowercase() to it.colorHex }

        val incomePalette = listOf(
            "#10B981", "#059669", "#34D399", "#3B82F6", "#6366F1",
            "#8B5CF6", "#06B6D4", "#14B8A6", "#84CC16", "#F59E0B"
        )

        return incomeTransactions
            .groupBy { it.categoryName }
            .map { (catName, txs) ->
                val amount = txs.sumOf { if (it.creditAmount > 0) it.creditAmount else it.amount }
                val percentage = if (totalIncome > 0) ((amount / totalIncome) * 100).toFloat() else 0f
                val color = categoryColorMap[catName.lowercase()]
                    ?: incomePalette[Math.abs(catName.hashCode()) % incomePalette.size]
                CategoryExpenseItem(
                    categoryName = catName,
                    amount = amount,
                    percentage = percentage,
                    colorHex = color,
                    count = txs.size
                )
            }
            .sortedByDescending { it.amount }
    }

    fun calculateMonthlyTrends(transactions: List<TransactionEntity>): List<MonthlyTrendItem> {
        val monthGroups = transactions.groupBy { tx ->
            if (tx.transactionDate.length >= 7) tx.transactionDate.substring(0, 7) else "2026-02"
        }

        val sortedMonths = monthGroups.keys.sorted()

        return sortedMonths.map { monthKey ->
            val txs = monthGroups[monthKey] ?: emptyList()
            val income = txs
                .filter { it.transactionType == TransactionType.INCOME || it.transactionType == TransactionType.REFUND }
                .sumOf { if (it.creditAmount > 0) it.creditAmount else it.amount }

            val expense = txs
                .filter { it.transactionType == TransactionType.EXPENSE }
                .sumOf { if (it.debitAmount > 0) it.debitAmount else it.amount }

            val monthLabel = formatMonthLabel(monthKey)

            MonthlyTrendItem(
                monthKey = monthKey,
                monthLabel = monthLabel,
                income = income,
                expense = expense,
                savings = income - expense
            )
        }
    }

    fun calculatePersonLendingSummaries(loans: List<LoanEntity>): List<PersonLendingSummary> {
        return loans.groupBy { it.personName.trim() }.map { (person, personLoans) ->
            val totalLent = personLoans.sumOf { it.amount }
            val totalRepaid = personLoans.sumOf { it.amountRepaid }
            val outstanding = personLoans.sumOf { it.remainingAmount.coerceAtLeast(0.0) }
            val phone = personLoans.firstOrNull { it.personPhone.isNotBlank() }?.personPhone ?: ""
            val overallStatus = when {
                outstanding <= 0.0 -> LoanStatus.PAID
                totalRepaid > 0.0 -> LoanStatus.PARTIALLY_PAID
                else -> LoanStatus.ACTIVE
            }
            PersonLendingSummary(
                personName = person,
                personPhone = phone,
                totalLent = totalLent,
                totalRepaid = totalRepaid,
                outstanding = outstanding,
                loansCount = personLoans.size,
                status = overallStatus
            )
        }.sortedByDescending { it.outstanding }
    }

    fun generateInsights(
        summary: DashboardSummary,
        categoryBreakdown: List<CategoryExpenseItem>,
        monthlyTrends: List<MonthlyTrendItem>
    ): List<FinancialInsight> {
        val list = mutableListOf<FinancialInsight>()

        // 1. Savings Rate
        if (summary.totalIncome > 0) {
            val savingsRate = ((summary.netSavings / summary.totalIncome) * 100).toInt()
            if (savingsRate > 25) {
                list.add(
                    FinancialInsight(
                        title = "Healthy Savings Rate ($savingsRate%)",
                        message = "You have retained ₹${formatRupee(summary.netSavings)} of your total income.",
                        type = InsightType.POSITIVE
                    )
                )
            } else if (savingsRate < 10) {
                list.add(
                    FinancialInsight(
                        title = "Low Savings Margin ($savingsRate%)",
                        message = "Expenses are consuming over 90% of your total recorded income.",
                        type = InsightType.WARNING
                    )
                )
            }
        }

        // 2. Top Category
        categoryBreakdown.firstOrNull()?.let { topCat ->
            list.add(
                FinancialInsight(
                    title = "Highest Spending: ${topCat.categoryName}",
                    message = "${String.format(Locale.ENGLISH, "%.1f", topCat.percentage)}% of expenses (₹${formatRupee(topCat.amount)}) went to ${topCat.categoryName}.",
                    type = InsightType.INFO
                )
            )
        }

        // 3. Outstanding Loans
        if (summary.totalOutstanding > 0) {
            list.add(
                FinancialInsight(
                    title = "₹${formatRupee(summary.totalOutstanding)} Outstanding to Collect",
                    message = "You have active loans pending repayment from friends/contacts.",
                    type = InsightType.WARNING
                )
            )
        }

        // 4. Company Expense status
        if (summary.companyExpense > 0) {
            list.add(
                FinancialInsight(
                    title = "Company Expenses: ₹${formatRupee(summary.companyExpense)}",
                    message = "Track your official expense receipts for corporate reimbursement.",
                    type = InsightType.INFO
                )
            )
        }

        return list
    }

    private fun formatMonthLabel(monthKey: String): String {
        return try {
            val parts = monthKey.split("-")
            if (parts.size == 2) {
                val year = parts[0].takeLast(2)
                val month = when (parts[1]) {
                    "01" -> "Jan"
                    "02" -> "Feb"
                    "03" -> "Mar"
                    "04" -> "Apr"
                    "05" -> "May"
                    "06" -> "Jun"
                    "07" -> "Jul"
                    "08" -> "Aug"
                    "09" -> "Sep"
                    "10" -> "Oct"
                    "11" -> "Nov"
                    "12" -> "Dec"
                    else -> parts[1]
                }
                "$month '$year"
            } else monthKey
        } catch (e: Exception) {
            monthKey
        }
    }

    private fun formatRupee(amount: Double): String {
        val symbols = DecimalFormatSymbols(Locale.Builder().setLanguage("en").setRegion("IN").build())
        val formatter = DecimalFormat("##,##,##0.00", symbols)
        return formatter.format(amount)
    }
}
