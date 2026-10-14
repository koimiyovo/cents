package com.kyovo.cents.infrastructure.persistence.room

import androidx.room3.Entity
import androidx.room3.PrimaryKey

/**
 * The single row of the `exchange_rates` table (its `id` is always [ROW_ID]): the last exchange rates fetched,
 * kept so that totals convert offline. [epochDay] is the day of the rates (a day, not an instant); [rates] is
 * "CODE=rate" pairs separated by ";" ("GBP=0.85;USD=1.10"), each rate with the digits the server gave. They are
 * always read and written as a whole, never queried one by one, so a text is enough and a row per currency
 * (with the day repeated on each) would only be more to keep consistent.
 */
@Entity(tableName = "exchange_rates")
data class ExchangeRatesEntity(
    @PrimaryKey val id: Int = ROW_ID,
    val epochDay: Long,
    val rates: String,
)
{
    companion object
    {
        const val ROW_ID = 1
    }
}
