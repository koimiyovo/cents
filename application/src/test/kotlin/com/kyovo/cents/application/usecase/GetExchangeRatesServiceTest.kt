package com.kyovo.cents.application.usecase

import com.kyovo.cents.application.fakes.InMemoryExchangeRateRepository
import com.kyovo.cents.domain.model.AccountCurrency
import com.kyovo.cents.domain.model.ExchangeRates
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

/** The rates the screens show next to the totals (their day, to say how old they are): what is kept, as it is. */
class GetExchangeRatesServiceTest
{
    private val repository = InMemoryExchangeRateRepository()
    private val service = GetExchangeRatesService(repository)

    private val monday = ExchangeRates(LocalDate.of(2026, 10, 12), mapOf(AccountCurrency.USD to BigDecimal("1.1")))
    private val tuesday = ExchangeRates(LocalDate.of(2026, 10, 13), mapOf(AccountCurrency.USD to BigDecimal("1.12")))

    @Test
    fun `has no rates until some are kept`() = runTest()
    {
        assertThat(service.observe().first()).isNull()
    }

    @Test
    fun `gives the rates that are kept`() = runTest()
    {
        repository.save(monday)

        assertThat(service.observe().first()).isEqualTo(monday)
    }

    @Test
    fun `follows new rates`() = runTest()
    {
        repository.save(monday)
        repository.save(tuesday)

        assertThat(service.observe().first()).isEqualTo(tuesday)
    }
}
