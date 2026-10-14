package com.kyovo.cents.ui.common

import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.model.TransactionCategory

/**
 * The income and the expense of a list of transactions, in the main currency, and how many of them could not be
 * counted ([unconverted]: their currency has no exchange rate), which makes the figures incomplete.
 */
data class ConvertedTotals(
    val incomeCents: Long,
    val expenseCents: Long,
    val unconverted: Int
)
{
    val netCents: Long get() = incomeCents - expenseCents
}

/**
 * Adds up the incomes and expenses of [transactions] at the amount each has in [converted] (the same transactions
 * with their amounts in the main currency, matched by id). A transaction with no converted version is left out and
 * counted. Transfers and opening deposits are neither income nor expense: they are not in the sums.
 */
fun convertedTotals(transactions: List<Transaction>, converted: List<Transaction>): ConvertedTotals
{
    val convertedById = converted.associateBy { it.id }
    var income = 0L
    var expense = 0L
    var unconverted = 0

    for (transaction in transactions)
    {
        if (transaction.category != TransactionCategory.INCOME && transaction.category != TransactionCategory.EXPENSE) continue

        val inMainCurrency = convertedById[transaction.id]
        if (inMainCurrency == null)
        {
            unconverted++
        } else if (transaction.category == TransactionCategory.INCOME)
        {
            income += inMainCurrency.amount.value
        } else
        {
            expense += inMainCurrency.amount.value
        }
    }
    return ConvertedTotals(income, expense, unconverted)
}
