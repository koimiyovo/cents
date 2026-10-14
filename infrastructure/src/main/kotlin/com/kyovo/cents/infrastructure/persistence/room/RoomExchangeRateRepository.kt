package com.kyovo.cents.infrastructure.persistence.room

import com.kyovo.cents.domain.model.ExchangeRates
import com.kyovo.cents.domain.port.output.ExchangeRateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** The last exchange rates fetched, kept in the Room database so that totals convert offline. */
class RoomExchangeRateRepository(private val dao: ExchangeRateDao) : ExchangeRateRepository
{
    override fun observe(): Flow<ExchangeRates?>
    {
        return dao.observe().map { rows -> rows.firstOrNull()?.toDomain() }.distinctUntilChanged()
    }

    override suspend fun save(rates: ExchangeRates)
    {
        dao.upsert(rates.toEntity())
    }
}
