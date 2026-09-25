package com.kyovo.cents.infrastructure.persistence.room

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.kyovo.cents.infrastructure.persistence.Storage
import com.kyovo.cents.infrastructure.persistence.Stores
import com.kyovo.cents.infrastructure.persistence.UnitOfWorkContract
import org.junit.jupiter.api.AfterEach

/** The unit of work built on a database transaction must honour the same contract. */
class RoomUnitOfWorkContractTest : UnitOfWorkContract()
{
    private lateinit var database: CentsDatabase

    override fun createStorage(): Storage
    {
        database = Room.inMemoryDatabaseBuilder<CentsDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()
        return Storage(
            Stores(
                RoomAccountRepository(database.accountDao()),
                RoomSubcategoryRepository(database.subcategoryDao()),
                RoomTransactionRepository(database.transactionDao()),
            ),
            RoomUnitOfWork(database),
        )
    }

    @AfterEach
    fun closeTheDatabase()
    {
        database.close()
    }
}
