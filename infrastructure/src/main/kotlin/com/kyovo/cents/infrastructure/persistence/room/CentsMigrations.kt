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
     * Version 3 adds the recurring expenses. Nothing existing is touched: only a new, empty table. The
     * SQL is the one Room exported for version 3 (`schemas/.../3.json`), word for word.
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
    }

    /**
     * Version 4 adds the budget alerts already reported (the WorkManager check's memory, so it never
     * notifies the same crossing twice). Nothing existing is touched: only a new, empty table. The SQL is
     * the one Room exported for version 4 (`schemas/.../4.json`), word for word.
     */
    val MIGRATION_3_4 = Migration(3, 4)
    { connection ->
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

    /**
     * Version 5 turns `recurring_expenses` into `recurring_transactions`: a rule can now be an income as well
     * as an expense, so it gets a `category`, and the table is renamed to say what it holds. Nothing is lost:
     * the new table is created (the SQL is the one Room exported for version 5, `schemas/.../5.json`, word
     * for word), every existing rule is copied into it as an `EXPENSE` — the only kind that could exist
     * before — and the old table, with its indices, is dropped. Copying rather than `ALTER TABLE ... RENAME`
     * keeps the table, its indices and its foreign key exactly what Room expects, whatever names the old
     * ones had.
     */
    val MIGRATION_4_5 = Migration(4, 5)
    { connection ->
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `recurring_transactions` (" +
                    "`id` BLOB NOT NULL, " +
                    "`accountId` BLOB NOT NULL, " +
                    "`category` TEXT NOT NULL, " +
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
            "INSERT INTO `recurring_transactions` (" +
                    "`id`, `accountId`, `category`, `amount`, `title`, `subcategoryId`, `description`, " +
                    "`frequency`, `interval`, `startDate`, `endDate`, `lastGeneratedDate`) " +
                    "SELECT `id`, `accountId`, 'EXPENSE', `amount`, `title`, `subcategoryId`, `description`, " +
                    "`frequency`, `interval`, `startDate`, `endDate`, `lastGeneratedDate` " +
                    "FROM `recurring_expenses`"
        )
        connection.execSQL("DROP TABLE `recurring_expenses`")
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_recurring_transactions_accountId` ON `recurring_transactions` (`accountId`)"
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_recurring_transactions_subcategoryId` ON `recurring_transactions` (`subcategoryId`)"
        )
    }

    /**
     * Version 6 adds the projects: a new, empty `projects` table (the SQL is the one Room exported for
     * version 6, `schemas/.../6.json`, word for word), and a `projectId` column on `transactions` that
     * points to it. Nothing existing is rewritten: every transaction gets a null `projectId`, which is
     * "no project". `ALTER TABLE ... ADD COLUMN` is enough because SQLite accepts a column with a
     * `REFERENCES` clause when it defaults to NULL, and the foreign key (`SET NULL`: deleting a project
     * keeps its transactions) is then part of the table, which Room checks when the file is opened.
     */
    val MIGRATION_5_6 = Migration(5, 6)
    { connection ->
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `projects` (" +
                    "`id` BLOB NOT NULL, " +
                    "`name` TEXT NOT NULL, " +
                    "`emoji` TEXT, " +
                    "`targetCents` INTEGER, " +
                    "`alertPercent` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`))"
        )
        connection.execSQL(
            "ALTER TABLE `transactions` ADD COLUMN `projectId` BLOB " +
                    "REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL"
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_transactions_projectId` ON `transactions` (`projectId`)"
        )
    }

    /**
     * Version 7 adds the budget calendar: where each budget cycle starts. Two new, empty tables, so nothing
     * existing is touched and every user keeps calendar months until they choose otherwise (no row in
     * `budget_settings` means the default start day of 1). The SQL is the one Room exported for version 7
     * (`schemas/.../7.json`), word for word.
     */
    val MIGRATION_6_7 = Migration(6, 7)
    { connection ->
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `budget_cycle_starts` (" +
                    "`month` INTEGER NOT NULL, " +
                    "`startEpochDay` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`month`))"
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `budget_settings` (" +
                    "`id` INTEGER NOT NULL, " +
                    "`defaultStartDay` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`))"
        )
    }

    val ALL: List<Migration> =
        listOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
}
