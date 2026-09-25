package com.kyovo.cents.infrastructure.persistence.room

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.kyovo.cents.domain.port.output.SubcategoryRepository
import com.kyovo.cents.infrastructure.persistence.SubcategoryRepositoryContract
import org.junit.jupiter.api.AfterEach

/**
 * The Room adapter, against a real SQLite (in memory, through the bundled driver: the one Android
 * ships only loads on a device) — same tests as the in-memory list.
 */
class RoomSubcategoryRepositoryContractTest : SubcategoryRepositoryContract()
{
    private lateinit var database: CentsDatabase

    override fun createRepository(): SubcategoryRepository
    {
        database = Room.inMemoryDatabaseBuilder<CentsDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()
        return RoomSubcategoryRepository(database.subcategoryDao())
    }

    @AfterEach
    fun closeTheDatabase()
    {
        database.close()
    }
}
