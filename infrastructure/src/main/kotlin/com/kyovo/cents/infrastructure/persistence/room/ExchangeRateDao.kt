package com.kyovo.cents.infrastructure.persistence.room

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

/** The SQL of the `exchange_rates` table, checked against the schema at build time (KSP). */
@Dao
interface ExchangeRateDao
{
    /** Replaces the one row (its key is always the same) or creates it. */
    @Upsert
    suspend fun upsert(rates: ExchangeRatesEntity)

    /** Empty until rates have been fetched once; emits again each time they are saved. */
    @Query("SELECT * FROM exchange_rates")
    fun observe(): Flow<List<ExchangeRatesEntity>>
}
