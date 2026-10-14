package com.kyovo.cents.ui.transaction

import com.kyovo.cents.domain.model.AccountCurrency
import com.kyovo.cents.domain.model.ExchangeRates
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.ui.common.parseAmountToCents
import java.time.LocalDate

/**
 * What the destination of a transfer would receive at the ECB's rate, shown under the "amount received" field so the
 * user can compare it with what their bank gave. Null when there is nothing to suggest: no valid amount sent yet, the
 * same currency on both sides, or no rate for them. With no rates fetched, the euro and the CFA franc still convert
 * into each other (their rate is fixed).
 */
fun transferEstimate(
    amountSentText: String,
    from: AccountCurrency,
    to: AccountCurrency,
    rates: ExchangeRates?
): Money?
{
    if (from == to) return null

    val cents = parseAmountToCents(amountSentText, fractionDigits = from.fractionDigits) ?: return null
    val usable = rates ?: ExchangeRates(LocalDate.ofEpochDay(0), emptyMap())
    if (!usable.canConvert(from, to)) return null

    val estimate = usable.convert(Money(cents), from, to)
    return estimate.takeIf { it.isNotZero() }
}
