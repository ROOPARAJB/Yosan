package com.example.features.transactions.coordinator

import com.example.data.local.AppDatabase
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.LoanEntity
import com.example.data.local.entity.LoanStatus
import com.example.data.local.entity.TransactionEntity
import com.example.data.local.entity.TransactionType
import com.example.features.lending.GetLoansUseCase
import com.example.features.reports.FinancialCalculationService
import com.example.features.reports.PersonLendingSummary
import com.example.repository.FinanceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LendingCoordinator(
    private val database: AppDatabase,
    private val repository: FinanceRepository,
    private val scope: CoroutineScope,
    private val getLoansUseCase: GetLoansUseCase,
    private val allTransactionsFlow: StateFlow<List<TransactionEntity>>,
    private val accountsFlow: StateFlow<List<AccountEntity>>,
    private val showMessage: (String) -> Unit
) {
    val loans: StateFlow<List<LoanEntity>> = getLoansUseCase()
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    val personLendingSummaries: StateFlow<List<PersonLendingSummary>> = loans.map { lns ->
        FinancialCalculationService.calculatePersonLendingSummaries(lns)
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun generateNextLendId(): String {
        val txs = allTransactionsFlow.value
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
        val txs = allTransactionsFlow.value
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

    fun isLendIdUnique(id: String, excludeTxId: Long? = null, excludeLoanId: Long? = null): Boolean {
        if (id.isBlank()) return true
        val clean = id.trim()
        val txs = allTransactionsFlow.value
        val loansList = loans.value
        val inTxs = txs.any { it.id != excludeTxId && (it.advanceId?.trim().equals(clean, ignoreCase = true) || it.referenceNumber.trim().equals(clean, ignoreCase = true)) }
        val inLoans = loansList.any { it.id != excludeLoanId && (it.notes.contains(clean, ignoreCase = true) || it.notes.contains("#$clean", ignoreCase = true)) }
        return !inTxs && !inLoans
    }

    fun isBorrowIdUnique(id: String, excludeTxId: Long? = null, excludeLoanId: Long? = null): Boolean {
        if (id.isBlank()) return true
        val clean = id.trim()
        val txs = allTransactionsFlow.value
        val loansList = loans.value
        val inTxs = txs.any { it.id != excludeTxId && (it.advanceId?.trim().equals(clean, ignoreCase = true) || it.referenceNumber.trim().equals(clean, ignoreCase = true)) }
        val inLoans = loansList.any { it.id != excludeLoanId && (it.notes.contains(clean, ignoreCase = true) || it.notes.contains("#$clean", ignoreCase = true)) }
        return !inTxs && !inLoans
    }

    fun addLoan(
        personName: String,
        phone: String,
        amount: Double,
        lentDate: String,
        expectedDate: String?,
        notes: String,
        lendId: String? = null
    ) {
        scope.launch {
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

            val primaryAcc = accountsFlow.value.firstOrNull()?.id ?: 1
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
        accountId: Long = accountsFlow.value.firstOrNull()?.id ?: 1,
        borrowId: String? = null,
        notes: String = ""
    ) {
        scope.launch {
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
        scope.launch {
            repository.recordLoanRepayment(loanId, amount, date, method, notes)
            val loan = repository.getLoanById(loanId)
            val primaryAcc = accountsFlow.value.firstOrNull()?.id ?: 1
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
        scope.launch {
            repository.deleteRepayment(repaymentId, loanId)
            showMessage("Repayment deleted and loan balance updated")
        }
    }

    fun deleteLoan(id: Long) {
        scope.launch {
            database.loanRepaymentDao().deleteRepaymentsForLoan(id)
            val linkedTxs = database.transactionDao().getAllTransactionsList().filter { it.linkedLoanId == id }
            if (linkedTxs.isNotEmpty()) {
                database.transactionDao().deleteTransactions(linkedTxs.map { it.id })
            }
            repository.deleteLoan(id)
            showMessage("Loan and linked records deleted")
        }
    }

    fun deleteBorrowSet(borrowId: String, deleteInflowTx: Boolean = false) {
        scope.launch {
            val cleanId = borrowId.trim().removePrefix("#")
            val idPattern = Regex("""#?$cleanId\b""", RegexOption.IGNORE_CASE)
            val matchingTxs = allTransactionsFlow.value.filter {
                it.advanceId?.trim().equals(cleanId, ignoreCase = true) ||
                it.advanceId?.trim().equals("#$cleanId", ignoreCase = true) ||
                idPattern.containsMatchIn(it.notes) ||
                idPattern.containsMatchIn(it.description)
            }
            for (tx in matchingTxs) {
                if (deleteInflowTx && (tx.transactionType == TransactionType.BORROWING || tx.creditAmount > 0) && tx.isManual) {
                    repository.deleteTransaction(tx.id)
                } else {
                    val cleanNotes = tx.notes.replace(idPattern, "").trim()
                    repository.updateTransaction(
                        tx.copy(
                            advanceId = null,
                            notes = cleanNotes,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            }
            showMessage("Borrow set '#$cleanId' deleted and unlinked")
        }
    }

    fun deleteLendSet(lendTag: String, loanId: Long? = null, deleteLendTx: Boolean = false) {
        scope.launch {
            val cleanId = lendTag.trim().removePrefix("#")
            val idPattern = Regex("""#?$cleanId\b""", RegexOption.IGNORE_CASE)

            if (loanId != null) {
                database.loanRepaymentDao().deleteRepaymentsForLoan(loanId)
                repository.deleteLoan(loanId)
            }

            val matchingTxs = allTransactionsFlow.value.filter {
                (loanId != null && it.linkedLoanId == loanId) ||
                it.advanceId?.trim().equals(cleanId, ignoreCase = true) ||
                it.advanceId?.trim().equals("#$cleanId", ignoreCase = true) ||
                idPattern.containsMatchIn(it.notes) ||
                idPattern.containsMatchIn(it.description)
            }
            for (tx in matchingTxs) {
                if (deleteLendTx && (tx.transactionType == TransactionType.LENDING || tx.debitAmount > 0) && tx.isManual) {
                    repository.deleteTransaction(tx.id)
                } else {
                    val cleanNotes = tx.notes.replace(idPattern, "").trim()
                    repository.updateTransaction(
                        tx.copy(
                            advanceId = null,
                            linkedLoanId = null,
                            notes = cleanNotes,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            }
            showMessage("Lend record '#$cleanId' deleted")
        }
    }
}
