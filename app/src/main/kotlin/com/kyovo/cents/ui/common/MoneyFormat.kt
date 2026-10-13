package com.kyovo.cents.ui.common

import com.kyovo.cents.domain.model.AccountCurrency
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

/**
 * Formats an amount of [currency]'s smallest unit as "2 140,25 €", "1 250 F CFA" (fr-FR formatting). Built
 * from the whole number, never through a Double, and with the currency's own number of decimals.
 */
fun formatMoney(units: Long, currency: AccountCurrency): String
{
    val format = NumberFormat.getCurrencyInstance(Locale.FRANCE)
    format.currency = currency.value
    // Setting the currency does not change the number of decimals: the euro's two would stay on a franc.
    format.minimumFractionDigits = currency.fractionDigits
    format.maximumFractionDigits = currency.fractionDigits
    return format.format(BigDecimal.valueOf(units, currency.fractionDigits))
}

/**
 * Same as [formatMoney] but prefixed with an explicit "+" or "-", e.g. "+2 450,00 €". Zero has no
 * sign: it is neither a gain nor a loss ("0,00 €", what a total shows when nothing happened).
 */
fun formatSignedMoney(units: Long, currency: AccountCurrency): String
{
    val sign = when
    {
        units < 0 -> "-"
        units > 0 -> "+"
        else      -> ""
    }
    return sign + formatMoney(kotlin.math.abs(units), currency)
}
