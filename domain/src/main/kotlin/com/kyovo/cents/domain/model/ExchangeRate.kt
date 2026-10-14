package com.kyovo.cents.domain.model

import com.kyovo.cents.domain.exception.InvalidExchangeRateException
import java.math.BigDecimal
import java.math.RoundingMode

data class ExchangeRate(
    val from: AccountCurrency,
    val to: AccountCurrency,
    val rate: BigDecimal
)
{
    init
    {
        if (rate.signum() <= 0) throw InvalidExchangeRateException()
    }

    fun convert(amount: Money): Money
    {
        // The rate is between whole units (1 USD = 600 XOF) while a Money counts smallest units (cents, francs):
        // shift the decimal point by the difference of decimals, apply the rate, then round once.
        val decimalsShift = to.fractionDigits - from.fractionDigits
        val converted = BigDecimal.valueOf(amount.value).multiply(rate).scaleByPowerOfTen(decimalsShift)
        return Money(converted.setScale(0, RoundingMode.HALF_UP).longValueExact())
    }
}
