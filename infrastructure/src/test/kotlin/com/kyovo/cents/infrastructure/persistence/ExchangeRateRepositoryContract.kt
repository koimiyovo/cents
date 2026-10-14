package com.kyovo.cents.infrastructure.persistence

import com.kyovo.cents.domain.model.AccountCurrency
import com.kyovo.cents.domain.model.ExchangeRates
import com.kyovo.cents.domain.port.output.ExchangeRateRepository
import kotlinx.coroutines.flow.first
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * What every [ExchangeRateRepository] must do, whatever it keeps the rates in (today Room): the port's
 * specification, written as tests. It keeps one set of rates, the last one saved, and nothing before the
 * first save.
 */
abstract class ExchangeRateRepositoryContract
{
    /** Fresh, empty storage. Called before each test. */
    protected abstract fun createRepository(): ExchangeRateRepository

    protected lateinit var repository: ExchangeRateRepository

    @BeforeEach
    fun createTheRepository()
    {
        repository = createRepository()
    }

    private val monday = ExchangeRates(
        LocalDate.of(2026, 10, 12),
        mapOf(AccountCurrency.USD to BigDecimal("1.1"), AccountCurrency.GBP to BigDecimal("0.85"))
    )

    private val tuesday = ExchangeRates(
        LocalDate.of(2026, 10, 13),
        mapOf(AccountCurrency.USD to BigDecimal("1.16345"))
    )

    private suspend fun stored() = repository.observe().first()

    @Test
    fun `has no rates before the first save`() = realTime()
    {
        assertThat(stored()).isNull()
    }

    @Test
    fun `gives back the rates that were saved`() = realTime()
    {
        // WHEN
        repository.save(monday)

        // THEN
        assertThat(stored()).isEqualTo(monday)
    }

    @Test
    fun `keeps only the last rates saved`() = realTime()
    {
        // GIVEN
        repository.save(monday)

        // WHEN
        repository.save(tuesday)

        // THEN the dollar of Monday, and the pound that Tuesday no longer has, are gone
        assertThat(stored()).isEqualTo(tuesday)
    }

    // The digits are what the server said: "1.10" is not "1.1" to a BigDecimal, and a rate of 0.0000123 must not
    // turn into 1.23E-5 or lose a digit.
    @Test
    fun `keeps every digit of a rate, trailing zeros included`() = realTime()
    {
        // GIVEN
        val precise = ExchangeRates(
            LocalDate.of(2026, 10, 12),
            mapOf(
                AccountCurrency.USD to BigDecimal("1.10"),
                AccountCurrency.GBP to BigDecimal("0.0000123"),
                AccountCurrency.CAD to BigDecimal("1234567.123456789")
            )
        )

        // WHEN
        repository.save(precise)

        // THEN
        assertThat(stored()).isEqualTo(precise)
        assertThat(stored()!!.perEuro.getValue(AccountCurrency.USD).toPlainString()).isEqualTo("1.10")
    }

    @Test
    fun `keeps the day of rates that cover no currency`() = realTime()
    {
        // GIVEN rates of a day when the server had none of the app's currencies
        val nothing = ExchangeRates(LocalDate.of(2026, 10, 12), emptyMap())

        // WHEN
        repository.save(nothing)

        // THEN
        assertThat(stored()).isEqualTo(nothing)
    }

    @Test
    fun `keeps the rates of every currency the app can fetch`() = realTime()
    {
        // GIVEN
        val every = ExchangeRates(
            LocalDate.of(2026, 10, 12),
            mapOf(
                AccountCurrency.CAD to BigDecimal("1.5"),
                AccountCurrency.GBP to BigDecimal("0.85"),
                AccountCurrency.USD to BigDecimal("1.1")
            )
        )

        // WHEN
        repository.save(every)

        // THEN
        assertThat(stored()).isEqualTo(every)
    }

    @Test
    fun `lets an observer see a new save`() = realTime()
    {
        // GIVEN rates already saved, and observed
        repository.save(monday)
        assertThat(repository.observe().awaitMatching { it == monday }).isEqualTo(monday)

        // WHEN
        repository.save(tuesday)

        // THEN
        assertThat(repository.observe().awaitMatching { it == tuesday }).isEqualTo(tuesday)
    }

    @Test
    fun `saving the same rates again changes nothing`() = realTime()
    {
        // WHEN
        repository.save(monday)
        repository.save(monday)

        // THEN
        assertThat(stored()).isEqualTo(monday)
    }
}
