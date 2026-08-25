package com.example.ui.models

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

enum class DashboardCardType(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector
) {
    CURRENT_BALANCE("BALANCE", "Current Balance", "Hero card showing total net balance and visibility", Icons.Default.AccountBalanceWallet),
    OFFICIAL_EXPENSES("OFFICIAL", "Official Expenses", "Track claims and reimbursed corporate amounts", Icons.Default.Work),
    SPENDING_CATEGORY("SPENDING", "Spending by Category", "Donut chart with percentages and transaction counts", Icons.Default.PieChart),
    MONTHLY_PERFORMANCE("MONTHLY", "Monthly Performance Breakdown", "Consolidated monthly income and expense metrics", Icons.Default.TableChart),
    FINANCIAL_INSIGHTS("INSIGHTS", "Smart Insights", "Smart analysis and spending alerts", Icons.Default.Lightbulb),
    ACTIVE_LOANS("LOANS", "Active Loans & Debts", "Summary of outstanding lending and borrowing", Icons.Default.Handshake),
    RECENT_TRANSACTIONS("RECENT", "Recent Activity", "Latest transactions preview with category chips", Icons.Default.History)
}

data class DashboardCardItem(
    val type: DashboardCardType,
    val isEnabled: Boolean = true
)

object DashboardCardConfigManager {
    val DEFAULT_CONFIG = listOf(
        DashboardCardItem(DashboardCardType.CURRENT_BALANCE, true),
        DashboardCardItem(DashboardCardType.OFFICIAL_EXPENSES, true),
        DashboardCardItem(DashboardCardType.SPENDING_CATEGORY, true),
        DashboardCardItem(DashboardCardType.MONTHLY_PERFORMANCE, true),
        DashboardCardItem(DashboardCardType.FINANCIAL_INSIGHTS, true),
        DashboardCardItem(DashboardCardType.ACTIVE_LOANS, true),
        DashboardCardItem(DashboardCardType.RECENT_TRANSACTIONS, true)
    )

    fun serialize(cards: List<DashboardCardItem>): String {
        return cards.joinToString(",") { "${it.type.id}:${it.isEnabled}" }
    }

    fun parse(configStr: String?): List<DashboardCardItem> {
        if (configStr.isNullOrBlank()) return DEFAULT_CONFIG
        val map = configStr.split(",").mapNotNull { entry ->
            val parts = entry.split(":")
            if (parts.size == 2) {
                parts[0] to parts[1].toBooleanStrictOrNull()
            } else null
        }

        val result = mutableListOf<DashboardCardItem>()
        val seenTypes = mutableSetOf<DashboardCardType>()

        for ((id, enabled) in map) {
            val type = DashboardCardType.entries.firstOrNull { it.id == id }
            if (type != null && !seenTypes.contains(type)) {
                result.add(DashboardCardItem(type, enabled ?: true))
                seenTypes.add(type)
            }
        }

        for (defaultItem in DEFAULT_CONFIG) {
            if (!seenTypes.contains(defaultItem.type)) {
                result.add(defaultItem)
            }
        }

        return result
    }
}
