package com.kyovo.cents.infrastructure.persistence.room

import androidx.room3.Database
import androidx.room3.RoomDatabase

/**
 * The app's database: the list of its tables (entities) and the way to reach each one (a DAO).
 * The subcategories, the accounts, the transactions and the budgets are in it.
 *
 * Version 1 (subcategories, accounts, transactions) is installed on phones with real data, so it is
 * frozen: version 2 adds the `budgets` table, with a migration in [CentsMigrations]. Any further change to
 * a table means a new version and a new migration, never an edit of the ones before.
 */
@Database(
    entities = [SubcategoryEntity::class, AccountEntity::class, TransactionEntity::class, BudgetEntity::class],
    version = 2
)
abstract class CentsDatabase : RoomDatabase()
{
    abstract fun subcategoryDao(): SubcategoryDao
    abstract fun accountDao(): AccountDao
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
}
