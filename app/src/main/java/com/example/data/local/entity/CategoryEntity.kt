package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class CategoryType {
    INCOME, EXPENSE, LENDING, BORROWING, INVESTMENT, OTHER
}

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val type: CategoryType = CategoryType.EXPENSE,
    val iconName: String = "category",
    val colorHex: String = "#10B981",
    val isSystem: Boolean = false,
    val parentCategoryId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        val DEFAULT_CATEGORIES = listOf(
            CategoryEntity(name = "Food & Dining", type = CategoryType.EXPENSE, colorHex = "#EF4444", iconName = "restaurant", isSystem = true),
            CategoryEntity(name = "Groceries & Mart", type = CategoryType.EXPENSE, colorHex = "#10B981", iconName = "shopping_cart", isSystem = true),
            CategoryEntity(name = "Shopping", type = CategoryType.EXPENSE, colorHex = "#EC4899", iconName = "shopping_bag", isSystem = true),
            CategoryEntity(name = "Travel & Fuel", type = CategoryType.EXPENSE, colorHex = "#3B82F6", iconName = "directions_car", isSystem = true),
            CategoryEntity(name = "Utilities & Bills", type = CategoryType.EXPENSE, colorHex = "#F59E0B", iconName = "receipt_long", isSystem = true),
            CategoryEntity(name = "Entertainment", type = CategoryType.EXPENSE, colorHex = "#8B5CF6", iconName = "movie", isSystem = true),
            CategoryEntity(name = "Healthcare", type = CategoryType.EXPENSE, colorHex = "#06B6D4", iconName = "medical_services", isSystem = true),
            CategoryEntity(name = "EMI", type = CategoryType.EXPENSE, colorHex = "#E11D48", iconName = "receipt_long", isSystem = true),
            CategoryEntity(name = "Investments", type = CategoryType.INVESTMENT, colorHex = "#84CC16", iconName = "trending_up", isSystem = true),
            CategoryEntity(name = "Salary & Income", type = CategoryType.INCOME, colorHex = "#10B981", iconName = "payments", isSystem = true),
            CategoryEntity(name = "Official Expense", type = CategoryType.EXPENSE, colorHex = "#06B6D4", iconName = "business_center", isSystem = true),
            CategoryEntity(name = "Personal Expense", type = CategoryType.EXPENSE, colorHex = "#F97316", iconName = "person", isSystem = true),
            CategoryEntity(name = "Transfer", type = CategoryType.OTHER, colorHex = "#64748B", iconName = "sync_alt", isSystem = true)
        )
    }
}
