package com.kyovo.cents.ui.budget

import com.kyovo.cents.domain.model.BudgetProgress

enum class BudgetStatus
{
    ON_TRACK,
    CLOSE_TO_LIMIT,
    OVER
}

/**
 * What a budget row says about itself: on track, close to its limit (from the alert threshold of its
 * budget — 80 % unless the user chose another — up to the limit itself), or over it. Spending exactly the limit is *close*, not over — the same boundary as
 * [BudgetProgress.isOverspent]. The share is compared on whole cents (`spent / limit >= threshold / 100` as
 * `spent * 100 >= limit * threshold`), never through floating point, so a boundary such as exactly 80 % cannot
 * land on the wrong side because of a rounding error.
 */
fun budgetStatus(progress: BudgetProgress): BudgetStatus
{
    val spent = progress.spent.value
    val limit = progress.limit.value
    val threshold = progress.alertThreshold.percent.toLong()

    return when
    {
        progress.isOverspent -> BudgetStatus.OVER
        spent * 100 >= limit * threshold -> BudgetStatus.CLOSE_TO_LIMIT
        else -> BudgetStatus.ON_TRACK
    }
}
