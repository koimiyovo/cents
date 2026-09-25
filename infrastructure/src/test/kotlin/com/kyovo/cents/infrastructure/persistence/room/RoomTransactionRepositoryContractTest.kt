package com.kyovo.cents.infrastructure.persistence.room

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.kyovo.cents.infrastructure.persistence.Stores
import com.kyovo.cents.infrastructure.persistence.TransactionRepositoryContract
import org.junit.jupiter.api.AfterEach

/** The Room adapters, against a real SQLite. */
class RoomTransactionRepositoryContractTest : TransactionRepositoryContract()
{
    private lateinit var database: CentsDatabase

    override fun createStores(): Stores
    {
        database = Room.inMemoryDatabaseBuilder<CentsDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()
        return Stores(
            RoomAccountRepository(database.accountDao()),
            RoomSubcategoryRepository(database.subcategoryDao()),
            RoomTransactionRepository(database.transactionDao()),
        )
    }

    @AfterEach
    fun closeTheDatabase()
    {
        database.close()
    }
}
