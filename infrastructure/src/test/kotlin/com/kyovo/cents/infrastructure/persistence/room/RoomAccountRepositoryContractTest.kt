package com.kyovo.cents.infrastructure.persistence.room

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.kyovo.cents.domain.port.output.AccountRepository
import com.kyovo.cents.infrastructure.persistence.AccountRepositoryContract
import org.junit.jupiter.api.AfterEach

/** The Room adapter, against a real SQLite. */
class RoomAccountRepositoryContractTest : AccountRepositoryContract()
{
    private lateinit var database: CentsDatabase

    override fun createRepository(): AccountRepository
    {
        database = Room.inMemoryDatabaseBuilder<CentsDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()
        return RoomAccountRepository(database.accountDao())
    }

    @AfterEach
    fun closeTheDatabase()
    {
        database.close()
    }
}
