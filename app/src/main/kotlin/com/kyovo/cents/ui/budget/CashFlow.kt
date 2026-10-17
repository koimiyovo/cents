package com.kyovo.cents.ui.budget

/** What a node of the cash-flow diagram is called — carries no string: the screen picks the wording. */
sealed interface CashFlowLabel
{
    /** An income or spending slice, named like the pie's. */
    data class Slice(val label: SpendingSliceLabel) : CashFlowLabel

    /** On the spending side: the income that was not spent. */
    data object Savings : CashFlowLabel

    /** On the income side: what was spent beyond the income, which came from what was put aside before. */
    data object FromSavings : CashFlowLabel
}

data class CashFlowNode(val label: CashFlowLabel, val cents: Long)

/**
 * The month as a flow: [sources] (where the money came from) all go into one pot that [uses] (where it went)
 * all come out of, so both columns add up to [total]. A balanced month has nothing else; otherwise a
 * [CashFlowLabel.Savings] node closes the spending side, or a [CashFlowLabel.FromSavings] node the income side.
 */
data class CashFlow(val sources: List<CashFlowNode>, val uses: List<CashFlowNode>, val total: Long)
{
    val isEmpty: Boolean get() = total == 0L

    /**
     * The share of the income that was not spent, in whole percent, or null when none was left over: the
     * [CashFlowLabel.Savings] node over the income, which is then the whole [total].
     */
    val savingsRatePercent: Int?
        get()
        {
            val savings = uses.firstOrNull { it.label == CashFlowLabel.Savings } ?: return null
            return Math.round(savings.cents * 100.0 / total).toInt()
        }
}

/** Joins the month's income and spending breakdowns, in the main currency, into the two sides of the diagram. */
fun cashFlow(income: SpendingBreakdown, spending: SpendingBreakdown): CashFlow
{
    val received = income.total.value
    val spent = spending.total.value

    val sources = income.slices.map { CashFlowNode(CashFlowLabel.Slice(it.label), it.amount.value) } +
            if (spent > received) listOf(CashFlowNode(CashFlowLabel.FromSavings, spent - received)) else emptyList()
    val uses = spending.slices.map { CashFlowNode(CashFlowLabel.Slice(it.label), it.amount.value) } +
            if (received > spent) listOf(CashFlowNode(CashFlowLabel.Savings, received - spent)) else emptyList()

    return CashFlow(sources, uses, maxOf(received, spent))
}
