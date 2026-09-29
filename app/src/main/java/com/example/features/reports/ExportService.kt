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
        val totalPages = if (monthlyTrends.isNotEmpty()) 2 else 1
        val pdfDocument = PdfDocument()

        // Common Paints
        val titlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 16f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            isAntiAlias = true
        }

        val userPaint = Paint().apply {
            color = Color.rgb(100, 100, 100)
            textSize = 10f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            isAntiAlias = true
        }

        val pageTagPaint = Paint().apply {
            color = Color.rgb(120, 120, 120)
            textSize = 10f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            isAntiAlias = true
        }

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

        val linePaint = Paint().apply {
            color = Color.rgb(224, 224, 224)
            strokeWidth = 1f
        }

        val footerPaint = Paint().apply {
            color = Color.rgb(128, 128, 128)
            textSize = 9f
            typeface = Typeface.create("sans-serif", Typeface.ITALIC)
            isAntiAlias = true
        }

        val positivePaint = Paint().apply {
            color = Color.rgb(16, 185, 129)
            textSize = 11f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            isAntiAlias = true
        }

        val negativePaint = Paint().apply {
            color = Color.rgb(239, 68, 68)
            textSize = 11f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            isAntiAlias = true
        }

        val formatInr = { valStr: Double -> CurrencyFormatter.formatInr(valStr) }

        // ================= PAGE 1: Overview & Category Breakdown =================
        val page1Info = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page1 = pdfDocument.startPage(page1Info)
        val canvas1 = page1.canvas

        canvas1.drawText("YOSAN FINANCIAL SUMMARY REPORT", 30f, 34f, titlePaint)
        canvas1.drawText("Generated for: ${userName ?: "User"} • Financial Performance Overview", 30f, 50f, userPaint)
        val p1Tag = "Page 1 of $totalPages"
        canvas1.drawText(p1Tag, 565f - pageTagPaint.measureText(p1Tag), 34f, pageTagPaint)

        // 1. Overview Summary Section
        canvas1.drawText("Overview Summary", 30f, 85f, sectionTitlePaint)
        canvas1.drawRoundRect(30f, 95f, 565f, 290f, 10f, 10f, cardBgPaint)
        canvas1.drawRoundRect(30f, 95f, 565f, 290f, 10f, 10f, cardBorderPaint)

        var y = 122f
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
            canvas1.drawText(label, 45f, y, textPaint)
            val valStr = formatInr(value)
            textPaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
            val width = textPaint.measureText(valStr)
            canvas1.drawText(valStr, 550f - width, y, textPaint)
            y += 23f
        }

        // 2. Category Spending Breakdown Section
        canvas1.drawText("Category Spending Breakdown (${categoryBreakdown.size})", 30f, 320f, sectionTitlePaint)
        val maxCatRows = 16
        val displayedCats = categoryBreakdown.take(maxCatRows)
        val catSectionHeight = (displayedCats.size.coerceAtLeast(1) * 22f + 45f).coerceAtLeast(85f)
        val catCardBottom = (332f + catSectionHeight).coerceAtMost(770f)
        canvas1.drawRoundRect(30f, 332f, 565f, catCardBottom, 10f, 10f, cardBgPaint)
        canvas1.drawRoundRect(30f, 332f, 565f, catCardBottom, 10f, 10f, cardBorderPaint)

        y = 358f
        if (displayedCats.isEmpty()) {
            textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            canvas1.drawText("No categorized expenses recorded.", 45f, y, textPaint)
        } else {
            displayedCats.forEach { item ->
                if (y < catCardBottom - 10f) {
                    textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                    canvas1.drawText(item.categoryName, 45f, y, textPaint)

                    val pctStr = "${String.format(java.util.Locale.ENGLISH, "%.1f", item.percentage)}% (${item.count})"
                    canvas1.drawText(pctStr, 280f, y, textPaint)

                    val valStr = formatInr(item.amount)
                    textPaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
                    val width = textPaint.measureText(valStr)
                    canvas1.drawText(valStr, 550f - width, y, textPaint)
                    y += 22f
                }
            }
        }

        // Page 1 Footer
        val footerY1 = 795f
        canvas1.drawLine(30f, footerY1, 565f, footerY1, linePaint)
        canvas1.drawText("This report was generated by Yosan app only. • Page 1 of $totalPages", 30f, footerY1 + 15f, footerPaint)
        pdfDocument.finishPage(page1)

        // ================= PAGE 2: Full Yearly Monthly Trends =================
        if (totalPages > 1) {
            val page2Info = PdfDocument.PageInfo.Builder(595, 842, 2).create()
            val page2 = pdfDocument.startPage(page2Info)
            val canvas2 = page2.canvas

            canvas2.drawText("YOSAN FINANCIAL SUMMARY REPORT — YEARLY TRENDS", 30f, 34f, titlePaint)
            canvas2.drawText("Complete 12-Month Cash Flow & Yearly Performance Analysis", 30f, 50f, userPaint)
            val p2Tag = "Page 2 of $totalPages"
            canvas2.drawText(p2Tag, 565f - pageTagPaint.measureText(p2Tag), 34f, pageTagPaint)

            // Monthly Trends Table
            canvas2.drawText("Yearly Cash Flow Performance (${monthlyTrends.size} Months)", 30f, 85f, sectionTitlePaint)
            val tableRows = monthlyTrends.take(12)
            val tableHeight = (tableRows.size * 26f + 50f).coerceAtLeast(90f)
            val tableBottom = (95f + tableHeight).coerceAtMost(520f)
            canvas2.drawRoundRect(30f, 95f, 565f, tableBottom, 10f, 10f, cardBgPaint)
            canvas2.drawRoundRect(30f, 95f, 565f, tableBottom, 10f, 10f, cardBorderPaint)

            // Table Header
            y = 120f
            textPaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
            canvas2.drawText("Month", 45f, y, textPaint)
            canvas2.drawText("Income", 180f, y, textPaint)
            canvas2.drawText("Expense", 320f, y, textPaint)
            canvas2.drawText("Net Savings", 440f, y, textPaint)
            canvas2.drawLine(40f, y + 8f, 555f, y + 8f, linePaint)

            y += 26f
            tableRows.forEach { trend ->
                if (y < tableBottom - 10f) {
                    textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                    canvas2.drawText(trend.monthLabel, 45f, y, textPaint)
                    canvas2.drawText(formatInr(trend.income), 180f, y, textPaint)
                    canvas2.drawText(formatInr(trend.expense), 320f, y, textPaint)

                    val savingsVal = trend.savings
                    val savingsPaint = if (savingsVal >= 0) positivePaint else negativePaint
                    canvas2.drawText(formatInr(savingsVal), 440f, y, savingsPaint)
                    y += 24f
                }
            }

            // Annual Insights Summary Card
            val insightsY = tableBottom + 25f
            canvas2.drawText("Annual Financial Performance Insights", 30f, insightsY, sectionTitlePaint)
            val insightsBottom = (insightsY + 160f).coerceAtMost(770f)
            canvas2.drawRoundRect(30f, insightsY + 10f, 565f, insightsBottom, 10f, 10f, cardBgPaint)
            canvas2.drawRoundRect(30f, insightsY + 10f, 565f, insightsBottom, 10f, 10f, cardBorderPaint)

            val totalInflow = monthlyTrends.sumOf { it.income }
            val totalOutflow = monthlyTrends.sumOf { it.expense }
            val netYearlySavings = totalInflow - totalOutflow
            val avgIncome = if (monthlyTrends.isNotEmpty()) totalInflow / monthlyTrends.size else 0.0
            val avgExpense = if (monthlyTrends.isNotEmpty()) totalOutflow / monthlyTrends.size else 0.0

            var iy = insightsY + 35f
            val insightsList = listOf(
                "Total Annual Inflow" to totalInflow,
                "Total Annual Outflow" to totalOutflow,
                "Net Annual Savings" to netYearlySavings,
                "Average Monthly Income" to avgIncome,
                "Average Monthly Expense" to avgExpense
            )

            insightsList.forEach { (label, value) ->
                textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                canvas2.drawText(label, 45f, iy, textPaint)
                val valStr = formatInr(value)
                val p = if (label == "Net Annual Savings") {
                    if (value >= 0) positivePaint else negativePaint
                } else {
                    Paint(textPaint).apply { typeface = Typeface.create("sans-serif", Typeface.BOLD) }
                }
                val width = p.measureText(valStr)
                canvas2.drawText(valStr, 550f - width, iy, p)
                iy += 24f
            }

            // Page 2 Footer
            val footerY2 = 795f
            canvas2.drawLine(30f, footerY2, 565f, footerY2, linePaint)
            canvas2.drawText("This report was generated by Yosan app only. • Page 2 of $totalPages", 30f, footerY2 + 15f, footerPaint)
            pdfDocument.finishPage(page2)
        }

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
