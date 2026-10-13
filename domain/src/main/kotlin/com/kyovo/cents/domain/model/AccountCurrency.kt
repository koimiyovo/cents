package com.kyovo.cents.domain.model

import java.util.Currency

@JvmInline
value class AccountCurrency(val value: Currency)
{
    /**
     * How many decimals this currency's amounts have (2 for the euro, the dollars and the pound, 0 for the CFA franc).
     * A [Money] is a count of the smallest unit, so this is what tells an amount of "1250" apart:
     * 12,50 € but 1 250 F CFA.
     */
    val fractionDigits: Int get() = value.defaultFractionDigits

    companion object
    {
        val CAD = AccountCurrency(Currency.getInstance("CAD"))
        val EUR = AccountCurrency(Currency.getInstance("EUR"))
        val GBP = AccountCurrency(Currency.getInstance("GBP"))
        val USD = AccountCurrency(Currency.getInstance("USD"))
        val XOF = AccountCurrency(Currency.getInstance("XOF"))

        /** What the app offers, in the order it offers them. */
        val SUPPORTED = listOf(CAD, EUR, GBP, USD, XOF)
    }
}
