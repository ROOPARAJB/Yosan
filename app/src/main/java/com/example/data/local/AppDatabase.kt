package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.*
import com.example.data.local.entity.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        CategorizationRuleEntity::class,
        TransactionEntity::class,
        LoanEntity::class,
        LoanRepaymentEntity::class,
        CompanyExpenseEntity::class,
        UserProfileEntity::class,
        DeletedTransactionEntity::class,
        SyncMetadataEntity::class,
        UndoHistoryEntity::class
    ],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun categorizationRuleDao(): CategorizationRuleDao
    abstract fun transactionDao(): TransactionDao
    abstract fun loanDao(): LoanDao
    abstract fun loanRepaymentDao(): LoanRepaymentDao
    abstract fun companyExpenseDao(): CompanyExpenseDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun syncDao(): SyncDao
    abstract fun undoDao(): UndoDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add syncId to transactions table
                db.execSQL("ALTER TABLE transactions ADD COLUMN syncId TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE transactions SET syncId = lower(hex(randomblob(16))) WHERE syncId = ''")

                // Create deleted_transactions table
                db.execSQL("CREATE TABLE IF NOT EXISTS deleted_transactions (syncId TEXT NOT NULL PRIMARY KEY, deletedAt INTEGER NOT NULL)")

                // Create sync_metadata table
                db.execSQL("CREATE TABLE IF NOT EXISTS sync_metadata (`key` TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL, updatedAt INTEGER NOT NULL)")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `undo_history` (
                        `actionId` TEXT NOT NULL PRIMARY KEY,
                        `actionType` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `affectedTransactionIds` TEXT NOT NULL,
                        `previousStateJson` TEXT NOT NULL,
                        `newStateJson` TEXT NOT NULL,
                        `relatedRuleId` INTEGER,
                        `ruleSnapshotJson` TEXT
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN advanceId TEXT DEFAULT NULL")
            }
        }

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "finance_manager_db"
                )
                    .addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                    .addCallback(AppDatabaseCallback(scope))
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class AppDatabaseCallback(
            private val scope: CoroutineScope
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
                        DatabaseInitializer.seedInitialData(database)
                    }
                }
            }
        }

    }
}
