package com.kyovo.cents.infrastructure.persistence.room

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.kyovo.cents.domain.model.AccountCurrency
import com.kyovo.cents.domain.model.ExchangeRates
import com.kyovo.cents.infrastructure.persistence.realTime
import kotlinx.coroutines.flow.first
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate

/** What only a database on disk can do: survive being closed, which is how the app works offline. */
class RoomExchangeRateRepositoryTest
{
    @TempDir
    lateinit var folder: File

    private fun open(): CentsDatabase =
        Room.databaseBuilder<CentsDatabase>(File(folder, "cents.db").absolutePath)
            .setDriver(BundledSQLiteDriver())
            .build()

    @Test
    fun `the rates are still there when the database is closed and opened again`() = realTime()
    {
        // GIVEN rates saved, then the database closed (as when the app is killed)
        val rates = ExchangeRates(
            LocalDate.of(2026, 10, 12),
            mapOf(AccountCurrency.USD to BigDecimal("1.1"), AccountCurrency.GBP to BigDecimal("0.85"))
        )
        val first = open()
        RoomExchangeRateRepository(first.exchangeRateDao()).save(rates)
        first.close()

        // WHEN the file is opened by a new database
        val second = open()
        val found = RoomExchangeRateRepository(second.exchangeRateDao()).observe().first()
        second.close()

        // THEN
        assertThat(found).isEqualTo(rates)
    }

    @Test
    fun `a new file has no rates`() = realTime()
    {
        val database = open()
        val found = RoomExchangeRateRepository(database.exchangeRateDao()).observe().first()
        database.close()

        assertThat(found).isNull()
    }
}
