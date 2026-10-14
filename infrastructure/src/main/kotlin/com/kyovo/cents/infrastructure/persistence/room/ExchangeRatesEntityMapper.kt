package com.kyovo.cents.infrastructure.persistence.room

import com.kyovo.cents.domain.model.AccountCurrency
import com.kyovo.cents.domain.model.ExchangeRates
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Currency

fun ExchangeRates.toEntity(): ExchangeRatesEntity
{
    // Sorted by code, so that equal rates always give the same text whatever the order of the map.
    val text = perEuro.entries
        .sortedBy { it.key.value.currencyCode }
        .joinToString(";") { (currency, rate) -> "${currency.value.currencyCode}=${rate.toPlainString()}" }
    return ExchangeRatesEntity(epochDay = date.toEpochDay(), rates = text)
}

/** An unreadable row is a corrupted database: it throws rather than guess rates, which would corrupt every total. */
fun ExchangeRatesEntity.toDomain(): ExchangeRates
{
    try
    {
        val perEuro = rates.split(";")
            .filter { it.isNotBlank() }
            .associate { pair ->
                val (code, rate) = pair.split("=", limit = 2)
                AccountCurrency(Currency.getInstance(code)) to BigDecimal(rate)
            }
        return ExchangeRates(LocalDate.ofEpochDay(epochDay), perEuro)
    } catch (failure: RuntimeException)
    {
        throw IllegalStateException("The exchange rates stored in the database cannot be read: \"$rates\"", failure)
    }
}
