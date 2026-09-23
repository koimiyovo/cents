package com.kyovo.cents.ui.common

import java.text.NumberFormat
import java.util.Locale

/** Formats a cents amount as "2 140,25 €" (fr-FR euro formatting used across the UI). */
fun formatEuroCents(cents: Long): String
{
    return NumberFormat.getCurrencyInstance(Locale.FRANCE).format(cents / 100.0)
}

/** Same as [formatEuroCents] but always prefixed with an explicit "+" or "-", e.g. "+2 450,00 €". */
fun formatSignedEuroCents(cents: Long): String
{
    val sign = if (cents < 0) "-" else "+"
    return sign + formatEuroCents(kotlin.math.abs(cents))
}
