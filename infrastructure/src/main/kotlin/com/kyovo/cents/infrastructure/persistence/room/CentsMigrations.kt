package com.kyovo.cents.infrastructure.persistence.room

import androidx.room3.migration.Migration
import androidx.sqlite.execSQL

/**
 * The migrations of [CentsDatabase], from one version to the next. Version 1 is installed on phones with
 * real accounts in it, so a version already released is never edited: a change to a table means raising
 * `version` in [CentsDatabase] and adding here a `Migration(from, to)` that carries the existing rows over.
 * Room then exports the new schema (`schemas/.../N.json`, to be checked in), and `SchemaGuardTest` fails
 * until the migration is there. A "destructive" fallback that recreates the tables is deliberately not
 * configured anywhere: it would erase the user's accounts.
 */
object CentsMigrations
{
    /**
     * Version 2 adds the budgets, each with its own alert threshold. Nothing existing is touched: only a
     * new, empty table. The SQL is the one Room exported for version 2 (`schemas/.../2.json`), word for
     * word — Room checks the table it finds against the schema when the database opens, and refuses one
     * that differs.
     */
    val MIGRATION_1_2 = Migration(1, 2)
    { connection ->
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `budgets` (" +
                    "`subcategoryId` BLOB NOT NULL, " +
                    "`month` INTEGER NOT NULL, " +
                    "`limitCents` INTEGER NOT NULL, " +
                    "`alertPercent` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`subcategoryId`, `month`), " +
                    "FOREIGN KEY(`subcategoryId`) REFERENCES `subcategories`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
    }

    /**
     * Version 3 adds the recurring expenses and the budget alerts already reported (the WorkManager
     * check's memory, so it never notifies the same crossing twice). Nothing existing is touched: only two
     * new, empty tables. The SQL is the one Room exported for version 3 (`schemas/.../3.json`), word for
     * word. Neither table existed in a version 3 released anywhere yet, so both were added to this same
     * migration rather than a 3-to-4 one — the same reasoning as folding the alert threshold into version 2.
     */
    val MIGRATION_2_3 = Migration(2, 3)
    { connection ->
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `recurring_expenses` (" +
                    "`id` BLOB NOT NULL, " +
                    "`accountId` BLOB NOT NULL, " +
                    "`amount` INTEGER NOT NULL, " +
                    "`title` TEXT NOT NULL, " +
                    "`subcategoryId` BLOB, " +
                    "`description` TEXT, " +
                    "`frequency` TEXT NOT NULL, " +
                    "`interval` INTEGER NOT NULL, " +
                    "`startDate` INTEGER NOT NULL, " +
                    "`endDate` INTEGER, " +
                    "`lastGeneratedDate` INTEGER, " +
                    "PRIMARY KEY(`id`), " +
                    "FOREIGN KEY(`subcategoryId`) REFERENCES `subcategories`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE SET NULL )"
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_recurring_expenses_accountId` ON `recurring_expenses` (`accountId`)"
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_recurring_expenses_subcategoryId` ON `recurring_expenses` (`subcategoryId`)"
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `budget_alerts` (" +
                    "`subcategoryId` BLOB NOT NULL, " +
                    "`month` INTEGER NOT NULL, " +
                    "`level` TEXT NOT NULL, " +
                    "PRIMARY KEY(`subcategoryId`, `month`, `level`), " +
                    "FOREIGN KEY(`subcategoryId`) REFERENCES `subcategories`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
    }

    val ALL: List<Migration> = listOf(MIGRATION_1_2, MIGRATION_2_3)
}
