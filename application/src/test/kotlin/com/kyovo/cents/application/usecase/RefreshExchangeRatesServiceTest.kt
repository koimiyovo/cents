package com.kyovo.cents.application.usecase

import com.kyovo.cents.application.fakes.FakeExchangeRateProvider
import com.kyovo.cents.application.fakes.InMemoryExchangeRateRepository
import com.kyovo.cents.application.fakes.assertThatThrownBySuspending
import com.kyovo.cents.domain.model.AccountCurrency
import com.kyovo.cents.domain.model.ExchangeRates
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.IOException
import java.math.BigDecimal
import java.time.LocalDate

class RefreshExchangeRatesServiceTest
{
    private val monday = ExchangeRates(LocalDate.of(2026, 10, 12), mapOf(AccountCurrency.USD to BigDecimal("1.10")))
    private val tuesday = ExchangeRates(LocalDate.of(2026, 10, 13), mapOf(AccountCurrency.USD to BigDecimal("1.12")))

    private val provider = FakeExchangeRateProvider(monday)
    private val repository = InMemoryExchangeRateRepository()
    private val service = RefreshExchangeRatesService(provider, repository)

    @Test
    fun `keeps the rates the provider gives`() = runTest()
    {
        // WHEN
        service.refresh()

        // THEN
        assertThat(repository.observe().first()).isEqualTo(monday)
    }

    @Test
    fun `replaces the rates it had with newer ones`() = runTest()
    {
        // GIVEN
        service.refresh()
        provider.rates = tuesday

        // WHEN
        service.refresh()

        // THEN
        assertThat(repository.observe().first()).isEqualTo(tuesday)
    }

    // Offline, the last known rates keep the totals converted: a failed refresh must not wipe them.
    @Test
    fun `keeps the rates it had when the provider fails, and says so`() = runTest()
    {
        // GIVEN
        service.refresh()
        provider.failWith = IOException("no network")

        // WHEN / THEN
        assertThatThrownBySuspending { service.refresh() }.isInstanceOf(IOException::class.java)
        assertThat(repository.observe().first()).isEqualTo(monday)
    }

    @Test
    fun `stores nothing when the very first refresh fails`() = runTest()
    {
        // GIVEN
        provider.failWith = IOException("no network")

        // WHEN
        assertThatThrownBySuspending { service.refresh() }.isInstanceOf(IOException::class.java)

        // THEN
        assertThat(repository.observe().first()).isNull()
    }
}
