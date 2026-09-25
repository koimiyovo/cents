package com.kyovo.cents.ui.common

import java.text.NumberFormat
import java.util.Locale

/** Formats a cents amount as "2 140,25 €" (fr-FR euro formatting used across the UI). */
fun formatEuroCents(cents: Long): String
{
    return NumberFormat.getCurrencyInstance(Locale.FRANCE).format(cents / 100.0)
}

/**
 * Same as [formatEuroCents] but prefixed with an explicit "+" or "-", e.g. "+2 450,00 €". Zero has no
 * sign: it is neither a gain nor a loss ("0,00 €", what a total shows when nothing happened).
 */
fun formatSignedEuroCents(cents: Long): String
{
    val sign = when
    {
        cents < 0 -> "-"
        cents > 0 -> "+"
        else      -> ""
    }
    return sign + formatEuroCents(kotlin.math.abs(cents))
}
