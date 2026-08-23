package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.CompanyExpenseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CompanyExpenseDao {
    @Query("SELECT * FROM company_expenses ORDER BY date DESC, id DESC")
    fun getAllCompanyExpenses(): Flow<List<CompanyExpenseEntity>>

    @Query("SELECT * FROM company_expenses ORDER BY date DESC")
    suspend fun getAllCompanyExpensesList(): List<CompanyExpenseEntity>

    @Query("SELECT * FROM company_expenses WHERE date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    fun getCompanyExpensesByDateRange(startDate: String, endDate: String): Flow<List<CompanyExpenseEntity>>

    @Query("SELECT * FROM company_expenses WHERE id = :id LIMIT 1")
    suspend fun getExpenseById(id: Long): CompanyExpenseEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompanyExpense(expense: CompanyExpenseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompanyExpenses(expenses: List<CompanyExpenseEntity>)

    @Update
    suspend fun updateCompanyExpense(expense: CompanyExpenseEntity)

    @Query("UPDATE company_expenses SET isReimbursed = :isReimbursed, updatedAt = :timestamp WHERE id = :id")
    suspend fun updateReimbursementStatus(id: Long, isReimbursed: Boolean, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM company_expenses WHERE id = :id")
    suspend fun deleteCompanyExpense(id: Long)

    @Query("DELETE FROM company_expenses")
    suspend fun deleteAllCompanyExpenses()

    @Query("SELECT COUNT(*) FROM company_expenses")
    suspend fun getCompanyExpenseCount(): Int
}
