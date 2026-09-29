package com.kyovo.cents.infrastructure.persistence.room

import androidx.room3.Database
import androidx.room3.RoomDatabase

/**
 * The app's database: the list of its tables (entities) and the way to reach each one (a DAO).
 * The subcategories, the accounts, the transactions, the budgets, the recurring expenses and the budget
 * alerts already reported are in it.
 *
 * Version 1 (subcategories, accounts, transactions) is installed on phones with real data, so it is
 * frozen: version 2 adds the `budgets` table (with the alert threshold of each budget), version 3 adds
 * `recurring_transactions`, version 4 adds `budget_alerts` (the WorkManager check's memory of what it already
 * reported), each with a migration in [CentsMigrations]. **Version 3 turned out to already be installed**
 * (found the hard way: folding `budget_alerts` into it, instead of a real version 4, crashed on a real
 * phone with "Room cannot verify the data integrity" — its identity hash no longer matched the file
 * already there), so it is frozen too now, same as version 1: any further change to a table means a new
 * version and a new migration, never an edit of a table an already-exported version describes.
 */
@Database(
    entities = [
        SubcategoryEntity::class,
        AccountEntity::class,
        TransactionEntity::class,
        BudgetEntity::class,
        RecurringTransactionEntity::class,
        BudgetAlertEntity::class,
    ],
    version = 4
)
abstract class CentsDatabase : RoomDatabase()
{
    abstract fun subcategoryDao(): SubcategoryDao
    abstract fun accountDao(): AccountDao
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun recurringTransactionDao(): RecurringTransactionDao
    abstract fun budgetAlertDao(): BudgetAlertDao
}
