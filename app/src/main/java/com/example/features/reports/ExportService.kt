package com.example.features.reports

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.data.local.entity.CompanyExpenseEntity
import com.example.data.local.entity.LoanEntity
import com.example.data.local.entity.TransactionEntity
import com.example.utils.CurrencyFormatter
import java.io.File
import java.io.FileOutputStream

object ExportService {

    fun exportTransactionsCsv(transactions: List<TransactionEntity>): String {
        val sb = StringBuilder()
        sb.append("Date,Description,Debit Amount,Credit Amount,Total Amount,Type,Category,Balance,Reference\n")
        transactions.forEach { tx ->
            sb.append("\"${tx.transactionDate}\",")
            sb.append("\"${tx.description.replace("\"", "\"\"")}\",")
            sb.append("${tx.debitAmount},")
            sb.append("${tx.creditAmount},")
            sb.append("${tx.amount},")
            sb.append("\"${tx.transactionType}\",")
            sb.append("\"${tx.categoryName}\",")
            sb.append("${tx.balanceAfterTransaction ?: ""},")
            sb.append("\"${tx.referenceNumber}\"\n")
        }
        return sb.toString()
    }

    fun exportLoansCsv(loans: List<LoanEntity>): String {
        val sb = StringBuilder()
        sb.append("Person Name,Phone,Lent Date,Principal Amount,Amount Repaid,Remaining Outstanding,Status,Notes\n")
        loans.forEach { loan ->
            sb.append("\"${loan.personName}\",")
            sb.append("\"${loan.personPhone}\",")
            sb.append("\"${loan.lentDate}\",")
            sb.append("${loan.amount},")
            sb.append("${loan.amountRepaid},")
            sb.append("${loan.remainingAmount},")
            sb.append("\"${loan.status}\",")
            sb.append("\"${loan.notes.replace("\"", "\"\"")}\"\n")
        }
        return sb.toString()
    }

    fun exportCompanyExpensesCsv(expenses: List<CompanyExpenseEntity>): String {
        val sb = StringBuilder()
        sb.append("Date,Amount,Reason,Category,Company Name,Payment Method,Reimbursed,Notes\n")
        expenses.forEach { exp ->
            sb.append("\"${exp.date}\",")
            sb.append("${exp.amount},")
            sb.append("\"${exp.reason.replace("\"", "\"\"")}\",")
            sb.append("\"${exp.category}\",")
            sb.append("\"${exp.companyName}\",")
            sb.append("\"${exp.paymentMethod}\",")
            sb.append("${exp.isReimbursed},")
            sb.append("\"${exp.notes.replace("\"", "\"\"")}\"\n")
        }
        return sb.toString()
    }

    fun exportFinancialReportSummary(
        summary: DashboardSummary,
        categoryBreakdown: List<CategoryExpenseItem>,
        monthlyTrends: List<MonthlyTrendItem>
    ): String {
        val sb = StringBuilder()
        sb.append("=========================================\n")
        sb.append("      FINANCIAL SUMMARY REPORT           \n")
        sb.append("=========================================\n\n")
        sb.append("Current Net Balance:   ${CurrencyFormatter.formatInr(summary.currentBalance)}\n")
        sb.append("Total Income Recorded: ${CurrencyFormatter.formatInr(summary.totalIncome)}\n")
        sb.append("Total Expenses:        ${CurrencyFormatter.formatInr(summary.totalExpense)}\n")
        sb.append("Total Money Lent:      ${CurrencyFormatter.formatInr(summary.moneyLent)}\n")
        sb.append("Total Repayments Recv: ${CurrencyFormatter.formatInr(summary.totalRepaid)}\n")
        sb.append("Total Outstanding:     ${CurrencyFormatter.formatInr(summary.totalOutstanding)}\n")
        sb.append("Company Expenses:      ${CurrencyFormatter.formatInr(summary.companyExpense)}\n\n")

        sb.append("--- CATEGORY SPENDING BREAKDOWN ---\n")
        categoryBreakdown.forEach { item ->
            sb.append(String.format("%-22s: %12s (%5.1f%%)\n", item.categoryName, CurrencyFormatter.formatInr(item.amount), item.percentage))
        }

        sb.append("\n--- MONTHLY CASH FLOW TRENDS ---\n")
        monthlyTrends.forEach { trend ->
            sb.append("${trend.monthLabel}: Income=${CurrencyFormatter.formatInr(trend.income)} | Expense=${CurrencyFormatter.formatInr(trend.expense)}\n")
        }

        return sb.toString()
    }

    fun generatePdfReport(
        context: Context,
        summary: DashboardSummary,
        categoryBreakdown: List<CategoryExpenseItem>,
        monthlyTrends: List<MonthlyTrendItem>,
        userName: String? = null
    ): File {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        // Title Paint — dark, matching card section style
        val titlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 16f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            isAntiAlias = true
        }

        // Subtitle / User info
        val userPaint = Paint().apply {
            color = Color.rgb(100, 100, 100)
            textSize = 10f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            isAntiAlias = true
        }

        canvas.drawText("YOSAN FINANCIAL SUMMARY REPORT", 30f, 32f, titlePaint)
        canvas.drawText("Generated for: ${userName ?: "User"}", 30f, 48f, userPaint)

        // Helper paints
        val textPaint = Paint().apply {
            color = Color.BLACK
            textSize = 11f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            isAntiAlias = true
        }

        val sectionTitlePaint = Paint().apply {
            color = Color.rgb(59, 130, 246)
            textSize = 13f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            isAntiAlias = true
        }

        val cardBgPaint = Paint().apply {
            color = Color.rgb(250, 250, 250)
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val cardBorderPaint = Paint().apply {
            color = Color.rgb(224, 224, 224)
            style = Paint.Style.STROKE
            strokeWidth = 1f
            isAntiAlias = true
        }

        val formatInr = { valStr: Double -> CurrencyFormatter.formatInr(valStr) }

        // 1. Overview Summary Section
        canvas.drawText("Overview Summary", 30f, 85f, sectionTitlePaint)
        // Draw card background
        canvas.drawRoundRect(30f, 95f, 565f, 280f, 10f, 10f, cardBgPaint)
        canvas.drawRoundRect(30f, 95f, 565f, 280f, 10f, 10f, cardBorderPaint)

        var y = 120f
        val metrics = listOf(
            "Current Net Balance" to summary.currentBalance,
            "Total Income Recorded" to summary.totalIncome,
            "Total Expenses" to summary.totalExpense,
            "Total Money Lent" to summary.moneyLent,
            "Total Repayments Received" to summary.totalRepaid,
            "Total Outstanding Loans" to summary.totalOutstanding,
            "Company Expenses" to summary.companyExpense
        )

        metrics.forEach { (label, value) ->
            textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            canvas.drawText(label, 45f, y, textPaint)
            val valStr = formatInr(value)
            textPaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
            val width = textPaint.measureText(valStr)
            canvas.drawText(valStr, 550f - width, y, textPaint)
            y += 22f
        }

        // 2. Category Spending Breakdown Section
        canvas.drawText("Category Spending Breakdown (${categoryBreakdown.size})", 30f, 305f, sectionTitlePaint)
        val catSectionHeight = (categoryBreakdown.size.coerceAtLeast(1) * 20f + 40f).coerceAtLeast(80f)
        canvas.drawRoundRect(30f, 315f, 565f, 315f + catSectionHeight, 10f, 10f, cardBgPaint)
        canvas.drawRoundRect(30f, 315f, 565f, 315f + catSectionHeight, 10f, 10f, cardBorderPaint)

        y = 338f
        if (categoryBreakdown.isEmpty()) {
            textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            canvas.drawText("No categorized expenses recorded.", 45f, y, textPaint)
        } else {
            categoryBreakdown.forEach { item ->
                textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                canvas.drawText(item.categoryName, 45f, y, textPaint)
                
                val pctStr = "${String.format(java.util.Locale.ENGLISH, "%.1f", item.percentage)}% (${item.count})"
                canvas.drawText(pctStr, 280f, y, textPaint)
                
                val valStr = formatInr(item.amount)
                textPaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
                val width = textPaint.measureText(valStr)
                canvas.drawText(valStr, 550f - width, y, textPaint)
                y += 20f
            }
        }

        // 3. Monthly Trends Summary Section
        val monthlyYStart = 315f + catSectionHeight + 25f
        canvas.drawText("Monthly Trends Summary", 30f, monthlyYStart - 10f, sectionTitlePaint)
        val monthlyHeight = (monthlyTrends.take(6).size.coerceAtLeast(1) * 22f + 40f).coerceAtLeast(70f)
        canvas.drawRoundRect(30f, monthlyYStart, 565f, (monthlyYStart + monthlyHeight).coerceAtMost(785f), 10f, 10f, cardBgPaint)
        canvas.drawRoundRect(30f, monthlyYStart, 565f, (monthlyYStart + monthlyHeight).coerceAtMost(785f), 10f, 10f, cardBorderPaint)

        y = monthlyYStart + 25f
        if (monthlyTrends.isEmpty()) {
            textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            canvas.drawText("No monthly trend data recorded.", 45f, y, textPaint)
        } else {
            textPaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
            canvas.drawText("Month", 45f, y, textPaint)
            canvas.drawText("Income", 240f, y, textPaint)
            canvas.drawText("Expense", 420f, y, textPaint)
            
            y += 22f
            textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)

            monthlyTrends.take(6).forEach { trend ->
                if (y < 780f) {
                    canvas.drawText(trend.monthLabel, 45f, y, textPaint)
                    canvas.drawText(formatInr(trend.income), 240f, y, textPaint)
                    canvas.drawText(formatInr(trend.expense), 420f, y, textPaint)
                    y += 22f
                }
            }
        }


        // 4. Draw Footer
        val footerY = 795f
        val linePaint = Paint().apply {
            color = Color.rgb(224, 224, 224)
            strokeWidth = 1f
        }
        canvas.drawLine(30f, footerY, 565f, footerY, linePaint)

        val footerPaint = Paint().apply {
            color = Color.rgb(128, 128, 128)
            textSize = 9f
            typeface = Typeface.create("sans-serif", Typeface.ITALIC)
            isAntiAlias = true
        }
        canvas.drawText("This report was generated by Yosan app only.", 30f, footerY + 15f, footerPaint)

        pdfDocument.finishPage(page)

        val timeStamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
        val cleanName = (userName ?: "User").trim().replace("\\s+".toRegex(), "_").replace("[^a-zA-Z0-9_]".toRegex(), "")
        val fileName = "${cleanName}_Financial_Report_$timeStamp.pdf"
        val reportsDir = File(context.cacheDir, "reports").apply { if (!exists()) mkdirs() }
        val file = File(reportsDir, fileName)
        FileOutputStream(file).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()
        return file
    }
}
