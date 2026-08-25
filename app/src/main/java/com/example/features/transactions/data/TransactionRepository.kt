package com.example.features.transactions.data

import com.example.data.local.AppDatabase
import com.example.data.local.dao.AccountDao
import com.example.data.local.dao.CategoryDao
import com.example.data.local.dao.TransactionDao
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

class TransactionRepository(private val database: AppDatabase) {
    private val accountDao: AccountDao = database.accountDao()
    private val categoryDao: CategoryDao = database.categoryDao()
    private val transactionDao: TransactionDao = database.transactionDao()

    val allAccounts: Flow<List<AccountEntity>> = accountDao.getAllAccounts()
    val allCategories: Flow<List<CategoryEntity>> = categoryDao.getAllCategories()
    val allTransactions: Flow<List<TransactionEntity>> = transactionDao.getAllTransactions()

    suspend fun getAccountById(id: Long) = accountDao.getAccountById(id)
    suspend fun insertAccount(account: AccountEntity) = accountDao.insertAccount(account)
    suspend fun deleteAccount(id: Long) = accountDao.deleteAccount(id)

    suspend fun insertCategory(category: CategoryEntity) = categoryDao.insertCategory(category)
    suspend fun deleteCategory(id: Long) = categoryDao.deleteCategory(id)

    suspend fun insertTransaction(transaction: TransactionEntity): Long {
        val id = transactionDao.insertTransaction(transaction)
        recalculateAccountBalance(transaction.accountId)
        return id
    }

    suspend fun insertTransactions(transactions: List<TransactionEntity>): List<Long> {
        val ids = transactionDao.insertTransactions(transactions)
        val accountIds = transactions.map { it.accountId }.distinct()
        accountIds.forEach { recalculateAccountBalance(it) }
        return ids
    }

    suspend fun deleteTransaction(id: Long) {
        val tx = transactionDao.getTransactionById(id)
        transactionDao.deleteTransaction(id)
        tx?.let { recalculateAccountBalance(it.accountId) }
    }

    suspend fun updateTransactionCategory(id: Long, categoryId: Long?, categoryName: String, transactionType: com.example.data.local.entity.TransactionType) {
        transactionDao.updateTransactionCategory(id, categoryId, categoryName, transactionType)
    }

    private suspend fun recalculateAccountBalance(accountId: Long) {
        // Balance recalculation logic is handled on database/UI side
    }
}
