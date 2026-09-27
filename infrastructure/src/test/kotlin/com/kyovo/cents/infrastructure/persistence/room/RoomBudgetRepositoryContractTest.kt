package com.kyovo.cents.infrastructure.persistence.room

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.kyovo.cents.infrastructure.persistence.BudgetRepositoryContract
import com.kyovo.cents.infrastructure.persistence.BudgetStores
import org.junit.jupiter.api.AfterEach

/** The Room adapters, against a real SQLite. */
class RoomBudgetRepositoryContractTest : BudgetRepositoryContract()
{
    private lateinit var database: CentsDatabase

    override fun createStores(): BudgetStores
    {
        database = Room.inMemoryDatabaseBuilder<CentsDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()
        return BudgetStores(
            RoomSubcategoryRepository(database.subcategoryDao()),
            RoomBudgetRepository(database.budgetDao()),
        )
    }

    @AfterEach
    fun closeTheDatabase()
    {
        database.close()
    }
}
