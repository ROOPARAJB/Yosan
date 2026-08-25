package com.example.utils

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CurrencyFormatter {

    fun formatInr(amount: Double, includeDecimals: Boolean = true): String {
        return try {
            val symbols = DecimalFormatSymbols(Locale("en", "IN"))
            val pattern = if (includeDecimals) "##,##,##0.00" else "##,##,##0"
            val formatter = DecimalFormat(pattern, symbols)
            "₹ " + formatter.format(amount)
        } catch (e: Exception) {
            "₹ " + String.format(Locale.ENGLISH, "%.2f", amount)
        }
    }

    fun formatInrCompact(amount: Double): String {
        return when {
            amount >= 10000000 -> String.format(Locale.ENGLISH, "₹%.2f Cr", amount / 10000000.0)
            amount >= 100000 -> String.format(Locale.ENGLISH, "₹%.2f L", amount / 100000.0)
            amount >= 1000 -> String.format(Locale.ENGLISH, "₹%.1f K", amount / 1000.0)
            else -> formatInr(amount, includeDecimals = false)
        }
    }
}

object DateUtils {
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
    private val displayFormat = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
    private val shortDisplayFormat = SimpleDateFormat("dd MMM", Locale.ENGLISH)
    private val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.ENGLISH)

    fun getCurrentDate(): String = isoFormat.format(Date())

    fun formatForDisplay(isoDate: String): String {
        return try {
            val date = isoFormat.parse(isoDate) ?: return isoDate
            displayFormat.format(date)
        } catch (e: Exception) {
            isoDate
        }
    }

    fun formatShortDisplay(isoDate: String): String {
        return try {
            val date = isoFormat.parse(isoDate) ?: return isoDate
            shortDisplayFormat.format(date)
        } catch (e: Exception) {
            isoDate
        }
    }

    fun formatMonthHeader(isoDate: String): String {
        return try {
            val date = isoFormat.parse(isoDate) ?: return isoDate
            monthFormat.format(date)
        } catch (e: Exception) {
            isoDate
        }
    }
}
