package com.kyovo.cents.application.usecase

import com.kyovo.cents.domain.port.input.RefreshExchangeRatesUseCase
import com.kyovo.cents.domain.port.output.ExchangeRateProvider
import com.kyovo.cents.domain.port.output.ExchangeRateRepository

class RefreshExchangeRatesService(
    private val provider: ExchangeRateProvider,
    private val repository: ExchangeRateRepository
) : RefreshExchangeRatesUseCase
{
    override suspend fun refresh()
    {
        // The rates are only saved once fetched: when the provider throws, what was kept stays.
        repository.save(provider.fetchLatest())
    }
}
