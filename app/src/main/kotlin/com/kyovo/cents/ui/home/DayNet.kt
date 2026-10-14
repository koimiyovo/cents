package com.kyovo.cents.ui.home

import com.kyovo.cents.domain.model.AccountCurrency
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.model.TransactionId
import com.kyovo.cents.ui.common.formatSignedMoney

/** The net of one day, shown at the right of its date in the Transactions tab. */
sealed interface DayNet
{
    /** Nothing to show: no transactions, or a figure that would leave some out. */
    data object None : DayNet

    /** The day is in one currency: its net is added up in that currency, as it is. */
    data class InCurrency(val cents: Long, val currency: AccountCurrency) : DayNet

    /** The day mixes currencies: its net is added up in the main currency, from the converted amounts. */
    data class Converted(val cents: Long) : DayNet
}

/**
 * The net of [transactions], those of one day. [currencyOf] gives the currency of a transaction's account (null when
 * it is unknown) and [converted] the transactions with their amounts in the main currency, by id.
 *
 * In one currency, the sum needs no rate. Across currencies it is the sum of the converted amounts, so a
 * transaction with no converted version (no rate) makes it [DayNet.None]: a total that left it out would be wrong.
 */
fun dayNet(
    transactions: List<Transaction>,
    currencyOf: (Transaction) -> AccountCurrency?,
    converted: Map<TransactionId, Transaction>
): DayNet
{
    if (transactions.isEmpty()) return DayNet.None

    val currencies = transactions.map(currencyOf)
    if (currencies.any { it == null }) return DayNet.None

    val single = currencies.distinct().singleOrNull()
    if (single != null)
    {
        return DayNet.InCurrency(transactions.sumOf { it.signedAmount }, single)
    }

    var cents = 0L
    for (transaction in transactions)
    {
        val inMainCurrency = converted[transaction.id] ?: return DayNet.None
        cents += inMainCurrency.signedAmount
    }
    return DayNet.Converted(cents)
}

/** The text of a day's net, or null when there is none. A converted one starts with "≈": it is an approximation. */
fun dayNetLabel(net: DayNet, mainCurrency: AccountCurrency): String?
{
    return when (net)
    {
        DayNet.None            -> null
        is DayNet.InCurrency   -> formatSignedMoney(net.cents, net.currency)
        is DayNet.Converted    -> "≈ " + formatSignedMoney(net.cents, mainCurrency)
    }
}
