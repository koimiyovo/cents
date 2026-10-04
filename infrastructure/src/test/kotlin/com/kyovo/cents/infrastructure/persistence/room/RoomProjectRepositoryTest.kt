package com.kyovo.cents.infrastructure.persistence.room

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.kyovo.cents.domain.model.Emoji
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.Project
import com.kyovo.cents.domain.model.ProjectId
import com.kyovo.cents.domain.model.ProjectName
import com.kyovo.cents.infrastructure.persistence.realTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.UUID

/** What only a database on disk can do: survive being closed. */
class RoomProjectRepositoryTest
{
    @TempDir
    lateinit var folder: File

    private fun open(): CentsDatabase =
        Room.databaseBuilder<CentsDatabase>(File(folder, "cents.db").absolutePath)
            .setDriver(BundledSQLiteDriver())
            .build()

    @Test
    fun `what was saved is still there, in the same order, when the database is closed and opened again`() =
        realTime()
        {
            // GIVEN projects saved, then the database closed (as when the app is killed)
            val japan = Project(
                ProjectId(UUID.fromString("66666666-6666-6666-6666-666666666661")),
                ProjectName("Voyage au Japon"), Emoji("✈️"), Money(300_000),
            )
            val kitchen = Project(
                ProjectId(UUID.fromString("66666666-6666-6666-6666-666666666662")),
                ProjectName("Travaux cuisine"), null, null,
            )
            val first = open()
            RoomProjectRepository(first.projectDao()).also {
                it.save(kitchen)
                it.save(japan)
            }
            first.close()

            // WHEN the file is opened by a new database
            val second = open()
            val found = RoomProjectRepository(second.projectDao()).findAll()
            second.close()

            // THEN
            assertThat(found).containsExactly(kitchen, japan)
        }
}
