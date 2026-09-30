package com.kyovo.cents.infrastructure.persistence.room

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.kyovo.cents.infrastructure.persistence.BudgetAlertRepositoryContract
import com.kyovo.cents.infrastructure.persistence.BudgetAlertStores
import org.junit.jupiter.api.AfterEach

/** The Room adapters, against a real SQLite. */
class RoomBudgetAlertRepositoryContractTest : BudgetAlertRepositoryContract()
{
    private lateinit var database: CentsDatabase

    override fun createStores(): BudgetAlertStores
    {
        database = Room.inMemoryDatabaseBuilder<CentsDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()
        return BudgetAlertStores(
            RoomSubcategoryRepository(database.subcategoryDao()),
            RoomBudgetAlertRepository(database.budgetAlertDao()),
        )
    }

    @AfterEach
    fun closeTheDatabase()
    {
        database.close()
    }
}
