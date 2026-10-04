package com.kyovo.cents.infrastructure.persistence.room

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.kyovo.cents.domain.port.output.ProjectRepository
import com.kyovo.cents.infrastructure.persistence.ProjectRepositoryContract
import org.junit.jupiter.api.AfterEach

/** The Room adapter, against a real SQLite (in memory, through the bundled driver). */
class RoomProjectRepositoryContractTest : ProjectRepositoryContract()
{
    private lateinit var database: CentsDatabase

    override fun createRepository(): ProjectRepository
    {
        database = Room.inMemoryDatabaseBuilder<CentsDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()
        return RoomProjectRepository(database.projectDao())
    }

    @AfterEach
    fun closeTheDatabase()
    {
        database.close()
    }
}
