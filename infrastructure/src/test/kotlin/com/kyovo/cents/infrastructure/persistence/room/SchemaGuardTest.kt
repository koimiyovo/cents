package com.kyovo.cents.infrastructure.persistence.room

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Version 1 of the database is installed on real phones, holding real accounts. From then on the
 * schema is a promise: the tables of an installed version never change silently. Changing an entity
 * means a new version and a [androidx.room3.migration.Migration] that carries the existing data over,
 * because the alternative (recreating the tables) wipes the user's accounts.
 *
 * Room writes the schema of the current version to `schemas/` at every build, and those files are
 * checked into git. These tests read them: they fail, with the way out in the message, when an
 * entity changes without the version going up, or when the version goes up without a migration.
 */
class SchemaGuardTest
{
    // Read relative to the module folder, where the tests run.
    private val schemaFolder = File("schemas/com.kyovo.cents.infrastructure.persistence.room.CentsDatabase")

    /** The exported schemas, by version. */
    private val schemaFiles: Map<Int, File> get() =
        schemaFolder.listFiles { file -> file.extension == "json" }.orEmpty()
            .associateBy { it.nameWithoutExtension.toInt() }
            .toSortedMap()

    private fun identityHashOf(file: File): String =
        Regex("\"identityHash\":\\s*\"([0-9a-f]+)\"").find(file.readText())?.groupValues?.get(1)
            ?: error("No identity hash in ${file.name}")

    @Test
    fun `the schemas of the database are exported`()
    {
        assertThat(schemaFiles).describedAs("the exported schemas in ${schemaFolder.path}").isNotEmpty()
    }

    // The hash Room computed for version 1 when it was first installed on a phone.
    @Test
    fun `version 1 of the schema is still the one that was installed`()
    {
        val file = schemaFiles[1] ?: error("schemas/.../1.json is missing: it must stay in git")

        assertThat(identityHashOf(file))
            .describedAs(
                "The schema of version 1 changed, but version 1 is installed on real phones. Do not edit " +
                    "the entities of an installed version: restore schemas/.../1.json from git, undo the change, " +
                    "then make it as a new version — raise `version` in CentsDatabase and add a Migration " +
                    "from the previous version to CentsMigrations.ALL."
            )
            .isEqualTo(V1_IDENTITY_HASH)
    }

    @Test
    fun `the exported versions follow each other without a gap`()
    {
        val versions = schemaFiles.keys.toList()

        assertThat(versions).isEqualTo((1..versions.max()).toList())
    }

    @Test
    fun `every version after the first has a migration from the one before`()
    {
        val latest = schemaFiles.keys.max()

        for (version in 2..latest)
        {
            assertThat(CentsMigrations.ALL.any { it.startVersion == version - 1 && it.endVersion == version })
                .describedAs("No Migration from version ${version - 1} to $version in CentsMigrations.ALL")
                .isTrue()
        }
    }

    @Test
    fun `no migration goes backwards or skips outside the known versions`()
    {
        val latest = schemaFiles.keys.max()

        CentsMigrations.ALL.forEach {
            assertThat(it.startVersion).isLessThan(it.endVersion)
            assertThat(it.endVersion).isLessThanOrEqualTo(latest)
        }
    }

    private companion object
    {
        const val V1_IDENTITY_HASH = "6443675907e21e5be77fbe107eff03cc"
    }
}
