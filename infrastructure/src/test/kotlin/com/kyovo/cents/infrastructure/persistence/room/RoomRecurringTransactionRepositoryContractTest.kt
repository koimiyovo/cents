package com.kyovo.cents.infrastructure.persistence.room

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.kyovo.cents.infrastructure.persistence.RecurringTransactionRepositoryContract
import com.kyovo.cents.infrastructure.persistence.RecurringTransactionStores
import org.junit.jupiter.api.AfterEach

/** The Room adapters, against a real SQLite. */
class RoomRecurringTransactionRepositoryContractTest : RecurringTransactionRepositoryContract()
{
    private lateinit var database: CentsDatabase

    override fun createStores(): RecurringTransactionStores
    {
        database = Room.inMemoryDatabaseBuilder<CentsDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()
        return RecurringTransactionStores(
            RoomAccountRepository(database.accountDao()),
            RoomSubcategoryRepository(database.subcategoryDao()),
            RoomRecurringTransactionRepository(database.recurringTransactionDao()),
        )
    }

    @AfterEach
    fun closeTheDatabase()
    {
        database.close()
    }
}
