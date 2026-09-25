package com.kyovo.cents.infrastructure.persistence.room

import androidx.room3.migration.Migration

/**
 * The migrations of [CentsDatabase], from one version to the next. There are none yet: version 1 is
 * the first, and it is installed on phones with real accounts in it.
 *
 * When an entity has to change, do not edit version 1: raise `version` in [CentsDatabase] and add here a
 * `Migration(from, to) { connection -> connection.execSQL("ALTER TABLE ...") }` that carries the existing
 * rows over. Room then exports the new schema (`schemas/.../2.json`, to be checked in), and `SchemaGuardTest`
 * fails until the migration is there. A "destructive" fallback that recreates the tables is deliberately
 * not configured anywhere: it would erase the user's accounts.
 */
object CentsMigrations
{
    val ALL: List<Migration> = emptyList()
}
