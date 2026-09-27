package com.kyovo.cents.infrastructure.persistence.room

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.kyovo.cents.infrastructure.persistence.RecurringExpenseRepositoryContract
import com.kyovo.cents.infrastructure.persistence.RecurringExpenseStores
import org.junit.jupiter.api.AfterEach

/** The Room adapters, against a real SQLite. */
class RoomRecurringExpenseRepositoryContractTest : RecurringExpenseRepositoryContract()
{
    private lateinit var database: CentsDatabase

    override fun createStores(): RecurringExpenseStores
    {
        database = Room.inMemoryDatabaseBuilder<CentsDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()
        return RecurringExpenseStores(
            RoomAccountRepository(database.accountDao()),
            RoomSubcategoryRepository(database.subcategoryDao()),
            RoomRecurringExpenseRepository(database.recurringExpenseDao()),
        )
    }

    @AfterEach
    fun closeTheDatabase()
    {
        database.close()
    }
}
