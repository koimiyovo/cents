package com.kyovo.cents.ui.budget

import com.kyovo.cents.domain.model.Money

/**
 * The month of the budgets that are set, as a whole: what the tab shows on top, to see where things stand
 * without reading every row. The subcategories that have no budget are not in it — they have no limit to
 * spend against.
 *
 * [overCount] and [closeCount] are how many budgets are over and how many are close (each by its own alert
 * threshold); [isOverspent] is about the month as a whole and is judged on its own, on the totals.
 */
data class BudgetSummary(
    val totalLimit: Money,
    val totalSpent: Money,
    val overCount: Int,
    val closeCount: Int,
)
{
    /** What is left of the total limit, negative once the total was passed. */
    val remaining: Long = totalLimit.value - totalSpent.value

    val isOverspent: Boolean = totalSpent.value > totalLimit.value

    /** The share of the total limit spent, kept between empty and full (a bar cannot go past its end). */
    val barFraction: Float = (totalSpent.value.toDouble() / totalLimit.value).coerceIn(0.0, 1.0).toFloat()
}

/**
 * What the budget tab lists: the [budgeted] rows, most urgent first, then — apart — the [unbudgeted] ones
 * (subcategories with no budget in force this month), and the [summary] of the budgets that are set, null when
 * none is.
 */
data class BudgetOverview(
    val budgeted: List<BudgetRow>,
    val unbudgeted: List<BudgetRow>,
    val summary: BudgetSummary?,
)

/** Over first, then close to the limit, then on track. */
private fun urgency(status: BudgetStatus?): Int = when (status)
{
    BudgetStatus.OVER           -> 0
    BudgetStatus.CLOSE_TO_LIMIT -> 1
    BudgetStatus.ON_TRACK       -> 2
    null                        -> 3
}

/**
 * Sorts the [rows] for a glance: the budgets that are set come most urgent first (rows of the same status
 * keep the order they were given in, already by name), the others are set apart in the order given.
 */
fun budgetOverview(rows: List<BudgetRow>): BudgetOverview
{
    val (budgeted, unbudgeted) = rows.partition { it.progress != null }
    val sorted = budgeted.sortedBy { urgency(it.status) }

    val summary = if (budgeted.isEmpty()) null else BudgetSummary(
        totalLimit = Money(budgeted.sumOf { it.progress!!.limit.value }),
        totalSpent = Money(budgeted.sumOf { it.progress!!.spent.value }),
        overCount = budgeted.count { it.status == BudgetStatus.OVER },
        closeCount = budgeted.count { it.status == BudgetStatus.CLOSE_TO_LIMIT },
    )
    return BudgetOverview(sorted, unbudgeted, summary)
}
