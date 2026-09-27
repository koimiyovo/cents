package com.kyovo.cents.ui.budget

import com.kyovo.cents.domain.model.BudgetProgress
import com.kyovo.cents.domain.model.BudgetProjection
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.Subcategory
import com.kyovo.cents.domain.model.SubcategoryId
import com.kyovo.cents.domain.model.project
import java.time.LocalDate
import java.time.YearMonth

/** What is left of a budget's limit, or by how much it was passed: what a row says in words. */
sealed interface BudgetRemaining
{
    /** [cents] are still left; zero when the limit was reached exactly (which is not over). */
    data class Left(val cents: Long) : BudgetRemaining

    /** The limit was passed by [cents], always above zero. */
    data class Over(val cents: Long) : BudgetRemaining
}

/**
 * One line of the budgets screen: an expense subcategory and, when a budget is in force for the month
 * shown, its [progress]. Without a budget, [progress] is null and the screen offers to set one. [projection]
 * is where the pace of spending is heading by the month's end — null without a budget, and also null for
 * any month but the one running now (a past month has nothing left to project).
 */
data class BudgetRow(
    val subcategory: Subcategory,
    val progress: BudgetProgress?,
    val projection: BudgetProjection? = null,
)
{
    /** On track, close to the limit or over it; null when there is no budget. */
    val status: BudgetStatus? = progress?.let { budgetStatus(it) }

    /** What is left of the limit, or by how much it was passed; null when there is no budget. */
    val remaining: BudgetRemaining? = progress?.let {
        if (it.isOverspent) BudgetRemaining.Over(-it.remaining) else BudgetRemaining.Left(it.remaining)
    }

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
 * subcategories is ignored. [month] and [today] project that progress to the month's end (see
 * [BudgetRow.projection]); left null when the caller has no use for it, which skips the projection
 * entirely rather than compute one nobody reads.
 */
fun budgetRows(
    subcategories: List<Subcategory>,
    progress: Map<SubcategoryId, BudgetProgress>,
    month: YearMonth? = null,
    today: LocalDate? = null,
): List<BudgetRow>
{
    return subcategories
        .filter { it.kind == RecordableTransactionCategory.EXPENSE }
        .map { subcategory ->
            val rowProgress = progress[subcategory.id]
            val projection = if (month != null && today != null) rowProgress?.project(month, today) else null
            BudgetRow(subcategory, rowProgress, projection)
        }
}
