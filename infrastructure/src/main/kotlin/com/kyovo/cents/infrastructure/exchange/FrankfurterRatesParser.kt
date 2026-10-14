package com.kyovo.cents.infrastructure.exchange

import com.kyovo.cents.domain.model.AccountCurrency
import com.kyovo.cents.domain.model.ExchangeRates
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Reads what Frankfurter answers to `GET /v2/rates?base=EUR&quotes=CAD,GBP,USD&providers=ecb`: one row per rate,
 * `[{"date":"2026-10-12","base":"EUR","quote":"USD","rate":1.1634},...]`.
 *
 * Only the currencies the app handles are kept. The euro is the base and the CFA franc is tied to it at a fixed
 * rate (see [ExchangeRates]), so neither is taken from the answer. Anything that cannot be trusted is refused
 * with a [MalformedExchangeRatesException].
 */
class FrankfurterRatesParser
{
    /** One row of the answer. The rate stays a [JsonPrimitive] to be read from its text, never as a Double. */
    @Serializable
    private class Row(
        val date: String,
        val base: String,
        val quote: String,
        val rate: JsonPrimitive
    )

    companion object
    {
        const val BASE = "EUR"

        private val FETCHED_CURRENCIES = AccountCurrency.SUPPORTED
            .filter { it != AccountCurrency.EUR && it != AccountCurrency.XOF }
            .associateBy { it.value.currencyCode }

        /** What to ask for, so the answer holds three rows and not a hundred and sixty. */
        val QUOTES = FETCHED_CURRENCIES.keys.joinToString(",")

        // A server may add fields one day: what is not read does not matter.
        private val JSON = Json { ignoreUnknownKeys = true }
    }

    fun parse(json: String): ExchangeRates
    {
        // Whatever fails below (bad JSON, a missing field, a date or a number that does not read, a rate the
        // domain refuses) means the same thing to the caller: this answer is no use.
        try
        {
            val rows = JSON.decodeFromString(ListSerializer(Row.serializer()), json)
            require(rows.isNotEmpty()) { "the answer has no rate" }

            val dates = mutableSetOf<LocalDate>()
            val perEuro = mutableMapOf<AccountCurrency, BigDecimal>()
            for (row in rows)
            {
                require(row.base == BASE) { "the rates are against ${row.base}, not against the euro" }

                dates += LocalDate.parse(row.date)

                val currency = FETCHED_CURRENCIES[row.quote] ?: continue
                // A rate is read from the text of its number, never through a Double: no binary rounding.
                val previous = perEuro.put(currency, BigDecimal(row.rate.content))
                require(previous == null) { "the rate of ${row.quote} is given twice" }
            }
            require(dates.size == 1) { "the rates are of several days: $dates" }

            return ExchangeRates(dates.single(), perEuro)
        } catch (failure: RuntimeException)
        {
            throw MalformedExchangeRatesException(
                "Frankfurter's answer cannot be read: ${failure.message}",
                failure
            )
        }
    }
}
