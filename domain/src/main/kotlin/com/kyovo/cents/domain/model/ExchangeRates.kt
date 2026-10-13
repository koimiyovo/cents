package com.kyovo.cents.domain.model

import com.kyovo.cents.domain.exception.ExchangeRateUnavailableException
import com.kyovo.cents.domain.exception.InvalidExchangeRateException
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate

/**
 * The exchange rates of one [date], all against the euro: [perEuro] holds how much of a currency one euro buys
 * ("1 EUR = 1.10 USD"), which is how the ECB publishes them. Any two currencies convert through the euro.
 *
 * The CFA franc is not published but is tied to the euro by treaty at a fixed 655.957 XOF per euro, so it is
 * known without being in [perEuro]; the euro itself is 1.
 */
data class ExchangeRates(
    val date: LocalDate,
    val perEuro: Map<AccountCurrency, BigDecimal>
)
{
    init
    {
        if (perEuro.values.any { it.signum() <= 0 }) throw InvalidExchangeRateException()
    }

    companion object
    {
        val XOF_PER_EURO = BigDecimal("655.957")
    }

    fun canConvert(from: AccountCurrency, to: AccountCurrency): Boolean
    {
        return from == to || (perEuroOf(from) != null && perEuroOf(to) != null)
    }

    fun convert(amount: Money, from: AccountCurrency, to: AccountCurrency): Money
    {
        if (from == to) return amount

        val fromPerEuro = perEuroOf(from) ?: throw ExchangeRateUnavailableException()
        val toPerEuro = perEuroOf(to) ?: throw ExchangeRateUnavailableException()

        // 1 unit of [from] is 1 / fromPerEuro euros, worth toPerEuro / fromPerEuro units of [to].
        val rate = toPerEuro.divide(fromPerEuro, MathContext.DECIMAL64)
        return ExchangeRate(from, to, rate).convert(amount)
    }

    private fun perEuroOf(currency: AccountCurrency): BigDecimal?
    {
        return when (currency)
        {
            AccountCurrency.EUR -> BigDecimal.ONE
            AccountCurrency.XOF -> XOF_PER_EURO
            else                -> perEuro[currency]
        }
    }
}
