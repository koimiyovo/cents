package com.kyovo.cents.infrastructure.persistence.room

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.kyovo.cents.domain.port.output.ExchangeRateRepository
import com.kyovo.cents.infrastructure.persistence.ExchangeRateRepositoryContract
import org.junit.jupiter.api.AfterEach

/** The Room adapter, against a real SQLite. */
class RoomExchangeRateRepositoryContractTest : ExchangeRateRepositoryContract()
{
    private lateinit var database: CentsDatabase

    override fun createRepository(): ExchangeRateRepository
    {
        database = Room.inMemoryDatabaseBuilder<CentsDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()
        return RoomExchangeRateRepository(database.exchangeRateDao())
    }

    @AfterEach
    fun closeTheDatabase()
    {
        database.close()
    }
}
