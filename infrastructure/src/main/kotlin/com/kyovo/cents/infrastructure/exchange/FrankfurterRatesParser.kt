package com.kyovo.cents.infrastructure.exchange

import com.kyovo.cents.domain.model.AccountCurrency
import com.kyovo.cents.domain.model.ExchangeRates
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Reads what Frankfurter answers to `GET /v1/latest?base=EUR`:
 * `{"amount":1.0,"base":"EUR","date":"2026-10-12","rates":{"USD":1.1634,...}}`.
 *
 * Only the currencies the app handles are kept. The euro is the base and the CFA franc is tied to it at a fixed
 * rate (see [ExchangeRates]), so neither is taken from the answer. Anything that cannot be trusted is refused
 * with a [MalformedExchangeRatesException].
 */
class FrankfurterRatesParser
{
    private companion object
    {
        const val BASE = "EUR"

        val FETCHED_CURRENCIES = AccountCurrency.SUPPORTED
            .filter { it != AccountCurrency.EUR && it != AccountCurrency.XOF }
            .associateBy { it.value.currencyCode }
    }

    fun parse(json: String): ExchangeRates
    {
        // Whatever fails below (bad JSON, a missing field, a date or a number that does not read, a rate the
        // domain refuses) means the same thing to the caller: this answer is no use.
        try
        {
            val answer = Json.parseToJsonElement(json).jsonObject

            val base = answer.getValue("base").jsonPrimitive.content
            require(base == BASE) { "the rates are against $base, not against the euro" }

            val date = LocalDate.parse(answer.getValue("date").jsonPrimitive.content)

            val perEuro = answer.getValue("rates").jsonObject.entries.mapNotNull { (code, rate) ->
                val currency = FETCHED_CURRENCIES[code] ?: return@mapNotNull null
                // A rate is read from the text of its number, never through a Double: no binary rounding.
                currency to BigDecimal(rate.jsonPrimitive.content)
            }.toMap()

            return ExchangeRates(date, perEuro)
        } catch (failure: RuntimeException)
        {
            throw MalformedExchangeRatesException("Frankfurter's answer cannot be read: ${failure.message}", failure)
        }
    }
}
