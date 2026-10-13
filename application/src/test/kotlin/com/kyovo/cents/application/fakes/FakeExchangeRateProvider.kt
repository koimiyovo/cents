package com.kyovo.cents.application.fakes

import com.kyovo.cents.domain.model.ExchangeRates
import com.kyovo.cents.domain.port.output.ExchangeRateProvider

/** Answers with [rates], or fails with [failWith] (a network error, for instance) when it is set. */
class FakeExchangeRateProvider(var rates: ExchangeRates) : ExchangeRateProvider
{
    var failWith: Exception? = null
    var calls = 0
        private set

    override suspend fun fetchLatest(): ExchangeRates
    {
        calls++
        failWith?.let { throw it }
        return rates
    }
}
