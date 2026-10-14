package com.kyovo.cents.ui.common

import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.AccountCurrency
import com.kyovo.cents.domain.model.ExchangeRates
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** What a total says about the exchange rates it used, shown under it. */
sealed interface RatesNote
{
    /** Every account is in the main currency: nothing was converted, so there is nothing to say. */
    data object None : RatesNote

    /** The total was converted with the rates of this day. */
    data class RatesOf(val day: LocalDate) : RatesNote

    /** Same, but the rates are old (the last fetch was a while ago, offline for instance). */
    data class Stale(val day: LocalDate) : RatesNote

    /** [accounts] accounts could not be converted at all (no rate): they are not in the total, which is incomplete. */
    data class Incomplete(val accounts: Int) : RatesNote
}

// Rates are published on working days: Friday's are still the latest on Tuesday, and a holiday adds a day.
private const val DAYS_BEFORE_STALE = 5L

fun ratesNote(foreignAccounts: Int, unconvertedAccounts: Int, rates: ExchangeRates?, today: LocalDate): RatesNote
{
    return when
    {
        foreignAccounts == 0                                          -> RatesNote.None
        unconvertedAccounts > 0                                       -> RatesNote.Incomplete(unconvertedAccounts)
        rates == null                                                 -> RatesNote.Incomplete(foreignAccounts)
        ChronoUnit.DAYS.between(rates.date, today) > DAYS_BEFORE_STALE -> RatesNote.Stale(rates.date)
        else                                                          -> RatesNote.RatesOf(rates.date)
    }
}

/** How many of [accounts] are in another currency than [mainCurrency]: the ones whose amounts get converted. */
fun foreignAccountCount(accounts: List<Account>, mainCurrency: AccountCurrency): Int
{
    return accounts.count { it.currency != mainCurrency }
}

/** How many of [accounts] cannot be converted into [mainCurrency] with [rates] (none fetched yet, or not covered). */
fun unconvertedAccountCount(accounts: List<Account>, mainCurrency: AccountCurrency, rates: ExchangeRates?): Int
{
    return accounts.count { it.currency != mainCurrency && (rates == null || !rates.canConvert(it.currency, mainCurrency)) }
}
