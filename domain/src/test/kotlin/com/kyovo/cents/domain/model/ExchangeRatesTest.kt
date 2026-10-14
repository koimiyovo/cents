package com.kyovo.cents.domain.model

import com.kyovo.cents.domain.exception.ExchangeRateUnavailableException
import com.kyovo.cents.domain.exception.InvalidExchangeRateException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * The rates of one day, all expressed against the euro (what the ECB publishes: "1 EUR = 1.10 USD"). Any two
 * currencies convert through the euro. The CFA franc is not published, but it is tied to the euro by treaty at
 * a fixed 655.957 XOF per euro, so it is known without being in the rates.
 */
class ExchangeRatesTest
{
    private val day = LocalDate.of(2026, 10, 12)

    private val rates = ExchangeRates(
        date = day,
        perEuro = mapOf(
            AccountCurrency.USD to BigDecimal("1.10"),
            AccountCurrency.GBP to BigDecimal("0.85")
        )
    )

    @Test
    fun `converts euros into a currency with its rate`()
    {
        // WHEN 100,00 EUR into dollars
        val converted = rates.convert(Money(10_000), AccountCurrency.EUR, AccountCurrency.USD)

        // THEN 110,00 USD
        assertThat(converted).isEqualTo(Money(11_000))
    }

    @Test
    fun `converts a currency back into euros`()
    {
        // WHEN 110,00 USD into euros
        val converted = rates.convert(Money(11_000), AccountCurrency.USD, AccountCurrency.EUR)

        // THEN 100,00 EUR
        assertThat(converted).isEqualTo(Money(10_000))
    }

    @Test
    fun `converts between two foreign currencies through the euro`()
    {
        // WHEN 110,00 USD into pounds: 100,00 EUR, then 85,00 GBP
        val converted = rates.convert(Money(11_000), AccountCurrency.USD, AccountCurrency.GBP)

        // THEN
        assertThat(converted).isEqualTo(Money(8_500))
    }

    @Test
    fun `leaves an amount alone when the currency does not change`()
    {
        // THEN even for a currency that has no rate
        assertThat(rates.convert(Money(1_234), AccountCurrency.CAD, AccountCurrency.CAD)).isEqualTo(Money(1_234))
    }

    @Test
    fun `knows the CFA franc without it being in the rates, at its fixed rate to the euro`()
    {
        // WHEN 100,00 EUR into CFA francs: 65 595.7, rounded to the franc
        val converted = rates.convert(Money(10_000), AccountCurrency.EUR, AccountCurrency.XOF)

        // THEN
        assertThat(converted).isEqualTo(Money(65_596))
    }

    @Test
    fun `converts CFA francs back into euros at the fixed rate`()
    {
        // WHEN 655 957 XOF into euros
        val converted = rates.convert(Money(655_957), AccountCurrency.XOF, AccountCurrency.EUR)

        // THEN exactly 1 000,00 EUR
        assertThat(converted).isEqualTo(Money(100_000))
    }

    @Test
    fun `converts between the CFA franc and another currency through the euro`()
    {
        // WHEN 65 595.7 XOF is 100 EUR, which is 110 USD: 100 000 XOF into dollars
        val converted = rates.convert(Money(655_957), AccountCurrency.XOF, AccountCurrency.USD)

        // THEN 1 000 EUR = 1 100,00 USD
        assertThat(converted).isEqualTo(Money(110_000))
    }

    @Test
    fun `refuses to convert a currency it has no rate for`()
    {
        // WHEN / THEN the Canadian dollar is not in these rates
        assertThatThrownBy { rates.convert(Money(100), AccountCurrency.EUR, AccountCurrency.CAD) }
            .isInstanceOf(ExchangeRateUnavailableException::class.java)
        assertThatThrownBy { rates.convert(Money(100), AccountCurrency.CAD, AccountCurrency.EUR) }
            .isInstanceOf(ExchangeRateUnavailableException::class.java)
    }

    @Test
    fun `says whether a conversion is possible`()
    {
        assertThat(rates.canConvert(AccountCurrency.USD, AccountCurrency.GBP)).isTrue()
        assertThat(rates.canConvert(AccountCurrency.EUR, AccountCurrency.XOF)).isTrue()
        assertThat(rates.canConvert(AccountCurrency.USD, AccountCurrency.USD)).isTrue()
        assertThat(rates.canConvert(AccountCurrency.USD, AccountCurrency.CAD)).isFalse()
    }

    @Test
    fun `refuses a rate that is not positive`()
    {
        // WHEN / THEN
        assertThatThrownBy { ExchangeRates(day, mapOf(AccountCurrency.USD to BigDecimal.ZERO)) }
            .isInstanceOf(InvalidExchangeRateException::class.java)
    }

    @Test
    fun `keeps the day the rates are from`()
    {
        assertThat(rates.date).isEqualTo(day)
    }
}
