package com.kyovo.cents.infrastructure.persistence.room

import androidx.room3.Database
import androidx.room3.RoomDatabase

/**
 * The app's database: the list of its tables (entities) and the way to reach each one (a DAO).
 * The subcategories, the accounts, the transactions, the budgets and the recurring expenses are in it.
 *
 * Version 1 (subcategories, accounts, transactions) is installed on phones with real data, so it is
 * frozen: version 2 adds the `budgets` table (with the alert threshold of each budget), version 3 adds
 * `recurring_expenses`, each with a migration in [CentsMigrations]. Neither version 2 nor 3 has been
 * released yet, so they may still change; once an app carrying one is installed anywhere, any further
 * change to a table means a new version and a new migration, never an edit of the ones before.
 */
@Database(
    entities = [
        SubcategoryEntity::class,
        AccountEntity::class,
        TransactionEntity::class,
        BudgetEntity::class,
        RecurringExpenseEntity::class,
    ],
    version = 3
)
abstract class CentsDatabase : RoomDatabase()
{
    abstract fun subcategoryDao(): SubcategoryDao
    abstract fun accountDao(): AccountDao
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun recurringExpenseDao(): RecurringExpenseDao
}
