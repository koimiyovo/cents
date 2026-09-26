package com.kyovo.cents.ui.budget

import com.kyovo.cents.domain.model.BudgetProgress

enum class BudgetStatus
{
    ON_TRACK,
    CLOSE_TO_LIMIT,
    OVER
}

/** From this share of the limit on, a budget is "close". A presentation rule, so it lives with the screen. */
private const val CLOSE_TO_LIMIT_PERCENT = 80L

/**
 * What a budget row says about itself: on track, close to its limit (from 80 % of it up to the limit
 * itself), or over it. Spending exactly the limit is *close*, not over — the same boundary as
 * [BudgetProgress.isOverspent]. The share is compared on whole cents (`spent / limit >= 80 / 100` as
 * `spent * 100 >= limit * 80`), never through floating point, so a boundary such as exactly 80 % cannot
 * land on the wrong side because of a rounding error.
 */
fun budgetStatus(progress: BudgetProgress): BudgetStatus
{
    val spent = progress.spent.value
    val limit = progress.limit.value

    return when
    {
        progress.isOverspent -> BudgetStatus.OVER
        spent * 100 >= limit * CLOSE_TO_LIMIT_PERCENT -> BudgetStatus.CLOSE_TO_LIMIT
        else -> BudgetStatus.ON_TRACK
    }
}
