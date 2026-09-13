package com.example.utils

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.data.local.AppDatabase
import com.example.data.local.entity.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

data class RestoreResult(
    val success: Boolean,
    val message: String,
    val accountsCount: Int = 0,
    val transactionsCount: Int = 0,
    val loansCount: Int = 0,
    val companyExpensesCount: Int = 0
)

object BackupService {

    suspend fun generateBackupJsonObject(database: AppDatabase): JSONObject {
        val root = JSONObject()
        root.put("version", 1)
        root.put("appName", "Yosan")
        root.put("createdAt", System.currentTimeMillis())

        // 1. User Profile
        try {
            val userProfile = database.userProfileDao().getUserProfileOnce()
            if (userProfile != null) {
                val pObj = JSONObject()
                pObj.put("id", userProfile.id)
                pObj.put("googleSub", userProfile.googleSub)
                pObj.put("name", userProfile.name)
                pObj.put("email", userProfile.email)
                pObj.put("profilePictureUrl", userProfile.profilePictureUrl)
                pObj.put("emailVerified", userProfile.emailVerified)
                pObj.put("currencySymbol", userProfile.currencySymbol)
                pObj.put("isDarkMode", userProfile.isDarkMode)
                pObj.put("isBiometricEnabled", userProfile.isBiometricEnabled)
                pObj.put("isPrivacyBlurEnabled", userProfile.isPrivacyBlurEnabled)
                pObj.put("blurTimeoutSeconds", userProfile.blurTimeoutSeconds)
                pObj.put("isOnboardingCompleted", userProfile.isOnboardingCompleted)
                pObj.put("dashboardCardsConfig", userProfile.dashboardCardsConfig)
                pObj.put("createdAt", userProfile.createdAt)
                pObj.put("updatedAt", userProfile.updatedAt)
                pObj.put("lastLoginAt", userProfile.lastLoginAt)
                pObj.put("isActive", userProfile.isActive)
                root.put("userProfile", pObj)
            }
        } catch (e: Exception) {
            android.util.Log.e("BackupService", "Error serializing user profile", e)
        }

        // 2. Accounts
        try {
            val accounts = database.accountDao().getAllAccountsList()
            val accArray = JSONArray()
            accounts.forEach { acc ->
                val obj = JSONObject()
                obj.put("id", acc.id)
                obj.put("accountName", acc.accountName)
                obj.put("accountNumberMasked", acc.accountNumberMasked)
                obj.put("bankName", acc.bankName)
                obj.put("accountType", acc.accountType.name)
                obj.put("openingBalance", acc.openingBalance)
                obj.put("currentBalance", acc.currentBalance)
                obj.put("isDefault", acc.isDefault)
                obj.put("createdAt", acc.createdAt)
                obj.put("updatedAt", acc.updatedAt)
                accArray.put(obj)
            }
            root.put("accounts", accArray)
        } catch (e: Exception) {
            android.util.Log.e("BackupService", "Error serializing accounts", e)
        }

        // 3. Categories
        try {
            val categories = database.categoryDao().getAllCategoriesList()
            val catArray = JSONArray()
            categories.forEach { cat ->
                val obj = JSONObject()
                obj.put("id", cat.id)
                obj.put("name", cat.name)
                obj.put("type", cat.type.name)
                obj.put("iconName", cat.iconName)
                obj.put("colorHex", cat.colorHex)
                obj.put("isSystem", cat.isSystem)
                if (cat.parentCategoryId != null) obj.put("parentCategoryId", cat.parentCategoryId)
                obj.put("createdAt", cat.createdAt)
                obj.put("updatedAt", cat.updatedAt)
                catArray.put(obj)
            }
            root.put("categories", catArray)
        } catch (e: Exception) {
            android.util.Log.e("BackupService", "Error serializing categories", e)
        }

        // 4. Rules
        try {
            val rules = database.categorizationRuleDao().getAllRulesList()
            val ruleArray = JSONArray()
            rules.forEach { rule ->
                val obj = JSONObject()
                obj.put("id", rule.id)
                obj.put("keyword", rule.keyword)
                obj.put("categoryId", rule.categoryId)
                obj.put("categoryName", rule.categoryName)
                obj.put("matchType", rule.matchType.name)
                obj.put("priority", rule.priority)
                obj.put("isActive", rule.isActive)
                obj.put("transactionType", rule.transactionType.name)
                obj.put("createdAt", rule.createdAt)
                obj.put("updatedAt", rule.updatedAt)
                ruleArray.put(obj)
            }
            root.put("rules", ruleArray)
        } catch (e: Exception) {
            android.util.Log.e("BackupService", "Error serializing rules", e)
        }

        // 5. Transactions
        try {
            val transactions = database.transactionDao().getAllTransactionsList()
            val txArray = JSONArray()
            transactions.forEach { tx ->
                val obj = JSONObject()
                obj.put("id", tx.id)
                obj.put("syncId", tx.syncId)
                obj.put("accountId", tx.accountId)
                obj.put("transactionDate", tx.transactionDate)
                obj.put("description", tx.description)
                obj.put("debitAmount", tx.debitAmount)
                obj.put("creditAmount", tx.creditAmount)
                obj.put("amount", tx.amount)
                obj.put("transactionType", tx.transactionType.name)
                if (tx.balanceAfterTransaction != null) obj.put("balanceAfterTransaction", tx.balanceAfterTransaction)
                if (tx.categoryId != null) obj.put("categoryId", tx.categoryId)
                obj.put("categoryName", tx.categoryName)
                obj.put("source", tx.source)
                obj.put("referenceNumber", tx.referenceNumber)
                obj.put("notes", tx.notes)
                obj.put("isManual", tx.isManual)
                obj.put("isCategorized", tx.isCategorized)
                obj.put("categorizationConfidence", tx.categorizationConfidence.toDouble())
                if (tx.transferId != null) obj.put("transferId", tx.transferId)
                if (tx.linkedLoanId != null) obj.put("linkedLoanId", tx.linkedLoanId)
                obj.put("createdAt", tx.createdAt)
                obj.put("updatedAt", tx.updatedAt)
                txArray.put(obj)
            }
            root.put("transactions", txArray)
        } catch (e: Exception) {
            android.util.Log.e("BackupService", "Error serializing transactions", e)
        }

        // 6. Loans
        try {
            val loans = database.loanDao().getAllLoansList()
            val loanArray = JSONArray()
            loans.forEach { loan ->
                val obj = JSONObject()
                obj.put("id", loan.id)
                obj.put("personName", loan.personName)
                obj.put("personPhone", loan.personPhone)
                obj.put("amount", loan.amount)
                obj.put("lentDate", loan.lentDate)
                if (loan.expectedRepaymentDate != null) obj.put("expectedRepaymentDate", loan.expectedRepaymentDate)
                obj.put("amountRepaid", loan.amountRepaid)
                obj.put("remainingAmount", loan.remainingAmount)
                obj.put("status", loan.status.name)
                obj.put("notes", loan.notes)
                obj.put("createdAt", loan.createdAt)
                obj.put("updatedAt", loan.updatedAt)
                loanArray.put(obj)
            }
            root.put("loans", loanArray)
        } catch (e: Exception) {
            android.util.Log.e("BackupService", "Error serializing loans", e)
        }

        // 7. Loan Repayments
        try {
            val repayments = database.loanRepaymentDao().getAllRepaymentsList()
            val repArray = JSONArray()
            repayments.forEach { rep ->
                val obj = JSONObject()
                obj.put("id", rep.id)
                obj.put("loanId", rep.loanId)
                obj.put("amount", rep.amount)
                obj.put("repaymentDate", rep.repaymentDate)
                obj.put("paymentMethod", rep.paymentMethod)
                obj.put("notes", rep.notes)
                obj.put("createdAt", rep.createdAt)
                repArray.put(obj)
            }
            root.put("loanRepayments", repArray)
        } catch (e: Exception) {
            android.util.Log.e("BackupService", "Error serializing repayments", e)
        }

        // 8. Company Expenses
        try {
            val companyExpenses = database.companyExpenseDao().getAllCompanyExpensesList()
            val compArray = JSONArray()
            companyExpenses.forEach { comp ->
                val obj = JSONObject()
                obj.put("id", comp.id)
                obj.put("date", comp.date)
                obj.put("amount", comp.amount)
                obj.put("reason", comp.reason)
                obj.put("companyName", comp.companyName)
                obj.put("category", comp.category)
                obj.put("paymentMethod", comp.paymentMethod)
                obj.put("isReimbursed", comp.isReimbursed)
                obj.put("notes", comp.notes)
                obj.put("createdAt", comp.createdAt)
                obj.put("updatedAt", comp.updatedAt)
                compArray.put(obj)
            }
            root.put("companyExpenses", compArray)
        } catch (e: Exception) {
            android.util.Log.e("BackupService", "Error serializing company expenses", e)
        }

        return root
    }

    suspend fun createBackupJson(context: Context, database: AppDatabase): File {
        val root = generateBackupJsonObject(database)
        val backupDir = File(context.cacheDir, "backups")
        if (!backupDir.exists()) {
            backupDir.mkdirs()
        }
        val timeTag = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val backupFile = File(backupDir, "yosan_backup_$timeTag.json")
        backupFile.writeText(root.toString(2), Charsets.UTF_8)
        return backupFile
    }

    suspend fun saveBackupToStorageUri(context: Context, uri: Uri, database: AppDatabase): Boolean {
        return try {
            val root = generateBackupJsonObject(database)
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.writer(Charsets.UTF_8).use { writer ->
                    writer.write(root.toString(2))
                }
            }
            true
        } catch (e: Exception) {
            android.util.Log.e("BackupService", "Error writing backup to URI", e)
            false
        }
    }

    suspend fun saveBackupToDownloads(context: Context, database: AppDatabase): String? {
        val root = generateBackupJsonObject(database)
        val timeTag = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "yosan_backup_$timeTag.json"
        val content = root.toString(2)

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Yosan")
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(content.toByteArray(Charsets.UTF_8))
                    }
                    "Downloads/Yosan/$fileName"
                } else null
            } else {
                val downloadDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Yosan")
                if (!downloadDir.exists()) downloadDir.mkdirs()
                val targetFile = File(downloadDir, fileName)
                FileOutputStream(targetFile).use { out ->
                    out.write(content.toByteArray(Charsets.UTF_8))
                }
                targetFile.absolutePath
            }
        } catch (e: Exception) {
            android.util.Log.e("BackupService", "Error saving to Downloads", e)
            null
        }
    }

    suspend fun restoreBackupJson(jsonString: String, database: AppDatabase): RestoreResult {
        return try {
            val cleanJson = jsonString.trim().removePrefix("\uFEFF")
            if (cleanJson.isBlank()) {
                return RestoreResult(false, "Backup file is empty")
            }

            val root = JSONObject(cleanJson)

            // 1. User Profile
            if (root.has("userProfile")) {
                try {
                    val pObj = root.getJSONObject("userProfile")
                    val profile = UserProfileEntity(
                        id = pObj.optLong("id", 1L),
                        googleSub = pObj.optString("googleSub", ""),
                        name = pObj.optString("name", "User"),
                        email = pObj.optString("email", ""),
                        profilePictureUrl = pObj.optString("profilePictureUrl", ""),
                        emailVerified = pObj.optBoolean("emailVerified", true),
                        currencySymbol = pObj.optString("currencySymbol", "₹"),
                        isDarkMode = pObj.optBoolean("isDarkMode", false),
                        isBiometricEnabled = pObj.optBoolean("isBiometricEnabled", false),
                        isPrivacyBlurEnabled = pObj.optBoolean("isPrivacyBlurEnabled", true),
                        blurTimeoutSeconds = pObj.optInt("blurTimeoutSeconds", 5),
                        isOnboardingCompleted = pObj.optBoolean("isOnboardingCompleted", true),
                        dashboardCardsConfig = pObj.optString("dashboardCardsConfig", "BALANCE:true,OFFICIAL:true,SPENDING:true,MONTHLY:true,INSIGHTS:true,LOANS:true,RECENT:true"),
                        createdAt = pObj.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = pObj.optLong("updatedAt", System.currentTimeMillis()),
                        lastLoginAt = pObj.optLong("lastLoginAt", System.currentTimeMillis()),
                        isActive = pObj.optBoolean("isActive", true)
                    )
                    database.userProfileDao().insertOrUpdateProfile(profile)
                } catch (e: Exception) {
                    android.util.Log.e("BackupService", "Error restoring profile", e)
                }
            }

            // 2. Accounts
            var accCount = 0
            if (root.has("accounts")) {
                try {
                    val accArray = root.getJSONArray("accounts")
                    val accList = mutableListOf<AccountEntity>()
                    for (i in 0 until accArray.length()) {
                        val obj = accArray.getJSONObject(i)
                        val typeStr = obj.optString("accountType", "BANK")
                        val accType = try { AccountType.valueOf(typeStr) } catch (_: Exception) { AccountType.BANK }
                        accList.add(
                            AccountEntity(
                                id = obj.optLong("id", 0L),
                                accountName = obj.optString("accountName", "Account"),
                                accountNumberMasked = obj.optString("accountNumberMasked", ""),
                                bankName = obj.optString("bankName", "Bank"),
                                accountType = accType,
                                openingBalance = obj.optDouble("openingBalance", 0.0),
                                currentBalance = obj.optDouble("currentBalance", 0.0),
                                isDefault = obj.optBoolean("isDefault", false),
                                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                            )
                        )
                    }
                    if (accList.isNotEmpty()) {
                        database.accountDao().insertAccounts(accList)
                        accCount = accList.size
                    }
                } catch (e: Exception) {
                    android.util.Log.e("BackupService", "Error restoring accounts", e)
                }
            }

            // 3. Categories
            if (root.has("categories")) {
                try {
                    val catArray = root.getJSONArray("categories")
                    val catList = mutableListOf<CategoryEntity>()
                    for (i in 0 until catArray.length()) {
                        val obj = catArray.getJSONObject(i)
                        val typeStr = obj.optString("type", "EXPENSE")
                        val catType = try { CategoryType.valueOf(typeStr) } catch (_: Exception) { CategoryType.EXPENSE }
                        catList.add(
                            CategoryEntity(
                                id = obj.optLong("id", 0L),
                                name = obj.optString("name", "General"),
                                type = catType,
                                iconName = obj.optString("iconName", "category"),
                                colorHex = obj.optString("colorHex", "#10B981"),
                                isSystem = obj.optBoolean("isSystem", false),
                                parentCategoryId = if (obj.has("parentCategoryId") && !obj.isNull("parentCategoryId")) obj.optLong("parentCategoryId") else null,
                                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                            )
                        )
                    }
                    if (catList.isNotEmpty()) {
                        database.categoryDao().insertCategories(catList)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("BackupService", "Error restoring categories", e)
                }
            }

            // 4. Rules
            if (root.has("rules")) {
                try {
                    val ruleArray = root.getJSONArray("rules")
                    val ruleList = mutableListOf<CategorizationRuleEntity>()
                    for (i in 0 until ruleArray.length()) {
                        val obj = ruleArray.getJSONObject(i)
                        val mTypeStr = obj.optString("matchType", "CONTAINS")
                        val matchType = try { MatchType.valueOf(mTypeStr) } catch (_: Exception) { MatchType.CONTAINS }
                        val txTypeStr = obj.optString("transactionType", "EXPENSE")
                        val txType = try { TransactionType.valueOf(txTypeStr) } catch (_: Exception) { TransactionType.EXPENSE }
                        ruleList.add(
                            CategorizationRuleEntity(
                                id = obj.optLong("id", 0L),
                                keyword = obj.optString("keyword", ""),
                                categoryId = obj.optLong("categoryId", 0L),
                                categoryName = obj.optString("categoryName", "Uncategorized"),
                                matchType = matchType,
                                priority = obj.optInt("priority", 0),
                                isActive = obj.optBoolean("isActive", true),
                                transactionType = txType,
                                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                            )
                        )
                    }
                    if (ruleList.isNotEmpty()) {
                        database.categorizationRuleDao().insertRules(ruleList)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("BackupService", "Error restoring rules", e)
                }
            }

            // 5. Transactions
            var txCount = 0
            if (root.has("transactions")) {
                try {
                    val txArray = root.getJSONArray("transactions")
                    val txList = mutableListOf<TransactionEntity>()
                    for (i in 0 until txArray.length()) {
                        val obj = txArray.getJSONObject(i)
                        val txTypeStr = obj.optString("transactionType", "EXPENSE")
                        val txType = try { TransactionType.valueOf(txTypeStr) } catch (_: Exception) { TransactionType.EXPENSE }
                        val syncId = obj.optString("syncId").ifBlank { UUID.randomUUID().toString() }
                        txList.add(
                            TransactionEntity(
                                id = obj.optLong("id", 0L),
                                syncId = syncId,
                                accountId = obj.optLong("accountId", 1L),
                                transactionDate = obj.optString("transactionDate", ""),
                                description = obj.optString("description", ""),
                                debitAmount = obj.optDouble("debitAmount", 0.0),
                                creditAmount = obj.optDouble("creditAmount", 0.0),
                                amount = obj.optDouble("amount", 0.0),
                                transactionType = txType,
                                balanceAfterTransaction = if (obj.has("balanceAfterTransaction") && !obj.isNull("balanceAfterTransaction")) obj.optDouble("balanceAfterTransaction") else null,
                                categoryId = if (obj.has("categoryId") && !obj.isNull("categoryId")) obj.optLong("categoryId") else null,
                                categoryName = obj.optString("categoryName", "Uncategorized"),
                                source = obj.optString("source", "MANUAL"),
                                referenceNumber = obj.optString("referenceNumber", ""),
                                notes = obj.optString("notes", ""),
                                isManual = obj.optBoolean("isManual", true),
                                isCategorized = obj.optBoolean("isCategorized", false),
                                categorizationConfidence = obj.optDouble("categorizationConfidence", 0.0).toFloat(),
                                transferId = if (obj.has("transferId") && !obj.isNull("transferId")) obj.optString("transferId") else null,
                                linkedLoanId = if (obj.has("linkedLoanId") && !obj.isNull("linkedLoanId")) obj.optLong("linkedLoanId") else null,
                                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                            )
                        )
                    }
                    if (txList.isNotEmpty()) {
                        database.transactionDao().insertTransactions(txList)
                        txCount = txList.size
                    }
                } catch (e: Exception) {
                    android.util.Log.e("BackupService", "Error restoring transactions", e)
                }
            }

            // 6. Loans
            var loanCount = 0
            if (root.has("loans")) {
                try {
                    val loanArray = root.getJSONArray("loans")
                    val loanList = mutableListOf<LoanEntity>()
                    for (i in 0 until loanArray.length()) {
                        val obj = loanArray.getJSONObject(i)
                        val statusStr = obj.optString("status", "ACTIVE")
                        val status = try { LoanStatus.valueOf(statusStr) } catch (_: Exception) { LoanStatus.ACTIVE }
                        loanList.add(
                            LoanEntity(
                                id = obj.optLong("id", 0L),
                                personName = obj.optString("personName", "Contact"),
                                personPhone = obj.optString("personPhone", ""),
                                amount = obj.optDouble("amount", 0.0),
                                lentDate = obj.optString("lentDate", ""),
                                expectedRepaymentDate = if (obj.has("expectedRepaymentDate") && !obj.isNull("expectedRepaymentDate")) obj.optString("expectedRepaymentDate") else null,
                                amountRepaid = obj.optDouble("amountRepaid", 0.0),
                                remainingAmount = obj.optDouble("remainingAmount", 0.0),
                                status = status,
                                notes = obj.optString("notes", ""),
                                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                            )
                        )
                    }
                    if (loanList.isNotEmpty()) {
                        database.loanDao().insertLoans(loanList)
                        loanCount = loanList.size
                    }
                } catch (e: Exception) {
                    android.util.Log.e("BackupService", "Error restoring loans", e)
                }
            }

            // 7. Loan Repayments
            if (root.has("loanRepayments")) {
                try {
                    val repArray = root.getJSONArray("loanRepayments")
                    val repList = mutableListOf<LoanRepaymentEntity>()
                    for (i in 0 until repArray.length()) {
                        val obj = repArray.getJSONObject(i)
                        repList.add(
                            LoanRepaymentEntity(
                                id = obj.optLong("id", 0L),
                                loanId = obj.optLong("loanId", 0L),
                                amount = obj.optDouble("amount", 0.0),
                                repaymentDate = obj.optString("repaymentDate", ""),
                                paymentMethod = obj.optString("paymentMethod", "UPI"),
                                notes = obj.optString("notes", ""),
                                createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                            )
                        )
                    }
                    if (repList.isNotEmpty()) {
                        database.loanRepaymentDao().insertRepayments(repList)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("BackupService", "Error restoring loan repayments", e)
                }
            }

            // 8. Company Expenses
            var compCount = 0
            if (root.has("companyExpenses")) {
                try {
                    val compArray = root.getJSONArray("companyExpenses")
                    val compList = mutableListOf<CompanyExpenseEntity>()
                    for (i in 0 until compArray.length()) {
                        val obj = compArray.getJSONObject(i)
                        compList.add(
                            CompanyExpenseEntity(
                                id = obj.optLong("id", 0L),
                                date = obj.optString("date", ""),
                                amount = obj.optDouble("amount", 0.0),
                                reason = obj.optString("reason", ""),
                                companyName = obj.optString("companyName", "Corporate"),
                                category = obj.optString("category", "Official Expense"),
                                paymentMethod = obj.optString("paymentMethod", "UPI"),
                                isReimbursed = obj.optBoolean("isReimbursed", false),
                                notes = obj.optString("notes", ""),
                                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                            )
                        )
                    }
                    if (compList.isNotEmpty()) {
                        database.companyExpenseDao().insertCompanyExpenses(compList)
                        compCount = compList.size
                    }
                } catch (e: Exception) {
                    android.util.Log.e("BackupService", "Error restoring company expenses", e)
                }
            }

            RestoreResult(
                success = true,
                message = "Restored: $accCount accounts, $txCount transactions, $loanCount loans, $compCount official expenses",
                accountsCount = accCount,
                transactionsCount = txCount,
                loansCount = loanCount,
                companyExpensesCount = compCount
            )
        } catch (e: Exception) {
            android.util.Log.e("BackupService", "Error parsing backup JSON", e)
            RestoreResult(false, "Invalid backup format: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    fun shareBackupFile(context: Context, backupFile: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                backupFile
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri("Yosan Backup", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "Share / Save Yosan Backup").apply {
                clipData = ClipData.newRawUri("Yosan Backup", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            android.util.Log.e("BackupService", "Error sharing backup", e)
            android.widget.Toast.makeText(context, "Error sharing backup: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    }
}
