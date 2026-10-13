package com.kyovo.cents.application.fakes

import com.kyovo.cents.domain.model.ExchangeRates
import com.kyovo.cents.domain.port.output.ExchangeRateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** The kept rates, in memory; null until some are saved, like the real one. */
class InMemoryExchangeRateRepository(initial: ExchangeRates? = null) : ExchangeRateRepository
{
    private val state = MutableStateFlow(initial)

    override fun observe(): Flow<ExchangeRates?> = state

    override suspend fun save(rates: ExchangeRates)
    {
        state.value = rates
    }
}
