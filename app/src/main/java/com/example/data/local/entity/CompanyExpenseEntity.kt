package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "company_expenses")
data class CompanyExpenseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: String, // YYYY-MM-DD
    val amount: Double,
    val reason: String,
    val category: String = "Travel",
    val companyName: String = "Corporate",
    val paymentMethod: String = "Corporate Card / UPI",
    val isReimbursed: Boolean = false,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
