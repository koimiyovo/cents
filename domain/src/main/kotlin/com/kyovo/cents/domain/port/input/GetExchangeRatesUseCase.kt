package com.kyovo.cents.domain.port.input

import com.kyovo.cents.domain.model.ExchangeRates
import kotlinx.coroutines.flow.Flow

interface GetExchangeRatesUseCase
{
    /** The exchange rates kept on the device (null until some were fetched); it follows every refresh. */
    fun observe(): Flow<ExchangeRates?>
}
