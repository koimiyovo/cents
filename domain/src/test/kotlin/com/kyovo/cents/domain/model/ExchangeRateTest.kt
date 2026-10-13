package com.kyovo.cents.domain.model

import com.kyovo.cents.domain.exception.InvalidExchangeRateException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class ExchangeRateTest
{
    @Test
    fun `converts an amount of the source currency into the target currency`()
    {
        // GIVEN 1 USD is worth 0.92 EUR
        val rate = ExchangeRate(
            from = AccountCurrency.USD,
            to = AccountCurrency.EUR,
            rate = BigDecimal("0.92")
        )

        // WHEN converting 100,00 USD (10 000 cents)
        val converted = rate.convert(Money(10_000))

        // THEN it is 92,00 EUR (9 200 cents)
        assertThat(converted).isEqualTo(Money(9_200))
    }

    // Triangulation
    @Test
    fun `uses the rate and the amount it is given`()
    {
        // GIVEN 1 GBP is worth 1.15 EUR
        val rate = ExchangeRate(
            from = AccountCurrency.GBP,
            to = AccountCurrency.EUR,
            rate = BigDecimal("1.15")
        )

        // WHEN converting 10,00 GBP (1 000 pence)
        val converted = rate.convert(Money(1_000))

        // THEN it is 11,50 EUR (1 150 cents)
        assertThat(converted).isEqualTo(Money(1_150))
    }

    // A rate is between whole units (1 USD = 600 XOF), while a Money counts the smallest unit of its currency:
    // cents for the dollar, whole francs for the CFA franc. The conversion has to account for the difference.
    @Test
    fun `accounts for the different decimals of the two currencies`()
    {
        // GIVEN 1 USD is worth 600 XOF
        val rate = ExchangeRate(
            from = AccountCurrency.USD,
            to = AccountCurrency.XOF,
            rate = BigDecimal("600")
        )

        // WHEN converting 100,00 USD (10 000 cents)
        val converted = rate.convert(Money(10_000))

        // THEN it is 60 000 XOF (60 000 francs, there are no cents)
        assertThat(converted).isEqualTo(Money(60_000))
    }

    // The rounding rule, pinned: half a smallest unit goes up, anything lower goes down, once, at the end.
    @Test
    fun `rounds half a unit up`()
    {
        // GIVEN 1 XOF is worth 0.00153 EUR
        val rate = ExchangeRate(
            from = AccountCurrency.XOF,
            to = AccountCurrency.EUR,
            rate = BigDecimal("0.00153")
        )

        // WHEN converting 500 XOF: 0.765 EUR, that is 76.5 cents
        val converted = rate.convert(Money(500))

        // THEN the half cent goes up
        assertThat(converted).isEqualTo(Money(77))
    }

    @Test
    fun `rounds less than half a unit down`()
    {
        // GIVEN 1 XOF is worth 0.001524 EUR
        val rate = ExchangeRate(
            from = AccountCurrency.XOF,
            to = AccountCurrency.EUR,
            rate = BigDecimal("0.001524")
        )

        // WHEN converting 1 000 XOF: 1.524 EUR, that is 152.4 cents
        val converted = rate.convert(Money(1_000))

        // THEN it is 152 cents
        assertThat(converted).isEqualTo(Money(152))
    }

    @Test
    fun `converts nothing into nothing`()
    {
        // GIVEN
        val rate = ExchangeRate(
            from = AccountCurrency.USD,
            to = AccountCurrency.EUR,
            rate = BigDecimal("0.92")
        )

        // THEN
        assertThat(rate.convert(Money(0))).isEqualTo(Money(0))
    }

    @Test
    fun `refuses a rate of zero`()
    {
        // WHEN / THEN
        assertThatThrownBy {
            ExchangeRate(from = AccountCurrency.USD, to = AccountCurrency.EUR, rate = BigDecimal.ZERO)
        }.isInstanceOf(InvalidExchangeRateException::class.java)
    }

    @Test
    fun `refuses a negative rate`()
    {
        // WHEN / THEN
        assertThatThrownBy {
            ExchangeRate(from = AccountCurrency.USD, to = AccountCurrency.EUR, rate = BigDecimal("-0.92"))
        }.isInstanceOf(InvalidExchangeRateException::class.java)
    }
}
