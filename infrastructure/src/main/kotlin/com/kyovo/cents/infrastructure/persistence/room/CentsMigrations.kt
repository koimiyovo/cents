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
     * Version 2 adds the budgets. Nothing existing is touched: only a new, empty table. The SQL is the
     * one Room exported for version 2 (`schemas/.../2.json`), word for word — Room checks the table it
     * finds against the schema when the database opens, and refuses one that differs.
     */
    val MIGRATION_1_2 = Migration(1, 2)
    { connection ->
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `budgets` (" +
                    "`subcategoryId` BLOB NOT NULL, " +
                    "`month` INTEGER NOT NULL, " +
                    "`limitCents` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`subcategoryId`, `month`), " +
                    "FOREIGN KEY(`subcategoryId`) REFERENCES `subcategories`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
    }

    val ALL: List<Migration> = listOf(MIGRATION_1_2)
}
