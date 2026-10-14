package com.kyovo.cents.application.usecase

import com.kyovo.cents.domain.model.ExchangeRates
import com.kyovo.cents.domain.port.input.GetExchangeRatesUseCase
import com.kyovo.cents.domain.port.output.ExchangeRateRepository
import kotlinx.coroutines.flow.Flow

class GetExchangeRatesService(private val repository: ExchangeRateRepository) : GetExchangeRatesUseCase
{
    override fun observe(): Flow<ExchangeRates?> = repository.observe()
}
