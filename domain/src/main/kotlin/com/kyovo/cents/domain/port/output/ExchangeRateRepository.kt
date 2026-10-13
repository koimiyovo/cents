package com.kyovo.cents.domain.port.output

import com.kyovo.cents.domain.model.ExchangeRates
import kotlinx.coroutines.flow.Flow

/**
 * The last exchange rates that were fetched, kept on the device so that totals can be converted offline.
 * Null until rates have been fetched once.
 */
interface ExchangeRateRepository
{
    fun observe(): Flow<ExchangeRates?>

    suspend fun save(rates: ExchangeRates)
}
