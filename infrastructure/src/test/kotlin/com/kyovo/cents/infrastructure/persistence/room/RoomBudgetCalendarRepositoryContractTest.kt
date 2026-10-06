package com.kyovo.cents.infrastructure.persistence.room

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.kyovo.cents.domain.port.output.BudgetCalendarRepository
import com.kyovo.cents.infrastructure.persistence.BudgetCalendarRepositoryContract
import org.junit.jupiter.api.AfterEach

/** The Room adapter, against a real SQLite. */
class RoomBudgetCalendarRepositoryContractTest : BudgetCalendarRepositoryContract()
{
    private lateinit var database: CentsDatabase

    override fun createRepository(): BudgetCalendarRepository
    {
        database = Room.inMemoryDatabaseBuilder<CentsDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()
        return RoomBudgetCalendarRepository(database.budgetCalendarDao())
    }

    @AfterEach
    fun closeTheDatabase()
    {
        database.close()
    }
}
