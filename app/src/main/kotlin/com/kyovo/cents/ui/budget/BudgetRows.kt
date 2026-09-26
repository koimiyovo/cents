package com.kyovo.cents.ui.budget

import com.kyovo.cents.domain.model.BudgetProgress
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.Subcategory
import com.kyovo.cents.domain.model.SubcategoryId

/**
 * One line of the budgets screen: an expense subcategory and, when a budget is in force for the month
 * shown, its [progress]. Without a budget, [progress] is null and the screen offers to set one.
 */
data class BudgetRow(val subcategory: Subcategory, val progress: BudgetProgress?)
{
    /** On track, close to the limit or over it; null when there is no budget. */
    val status: BudgetStatus? = progress?.let { budgetStatus(it) }

    /**
     * How much of the limit is used, for the progress bar: 0 when nothing is spent, 1 at the limit, and
     * still 1 above it (a bar cannot go past its end; [status] and the figures say the rest). Null when
     * there is no budget, so no bar is drawn.
     */
    val barFraction: Float? = progress?.let { (it.spent.value.toDouble() / it.limit.value).coerceIn(0.0, 1.0).toFloat() }
}

/**
 * What the budgets screen lists: the **expense** subcategories (an income has nothing to be capped),
 * one row each, in the order of [subcategories] (already by name), with the [progress] of the month
 * shown for those that have a budget in force. A progress whose id is not among the listed expense
 * subcategories is ignored.
 */
fun budgetRows(subcategories: List<Subcategory>, progress: Map<SubcategoryId, BudgetProgress>): List<BudgetRow>
{
    return subcategories
        .filter { it.kind == RecordableTransactionCategory.EXPENSE }
        .map { BudgetRow(it, progress[it.id]) }
}
