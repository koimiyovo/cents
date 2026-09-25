package com.kyovo.cents.infrastructure.persistence.room

import androidx.room3.Database
import androidx.room3.RoomDatabase

/**
 * The app's database: the list of its tables (entities) and the way to reach each one (a DAO).
 * The subcategories, the accounts and the transactions are in it.
 *
 * `version` stays 1 while no copy of this database exists anywhere but on a development machine: the
 * schema may still change freely. It goes up (with a migration) the first time an installed app has
 * created the database and the tables change after that.
 */
@Database(entities = [SubcategoryEntity::class, AccountEntity::class, TransactionEntity::class], version = 1)
abstract class CentsDatabase : RoomDatabase()
{
    abstract fun subcategoryDao(): SubcategoryDao
    abstract fun accountDao(): AccountDao
    abstract fun transactionDao(): TransactionDao
}
