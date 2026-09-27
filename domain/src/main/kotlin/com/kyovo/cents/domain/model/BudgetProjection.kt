package com.kyovo.cents.domain.model

import java.time.LocalDate
import java.time.YearMonth

/**
 * Where a budget is heading if spending keeps the same pace for the rest of the month: what [spent] so
 * far would become by the month's end, extrapolated linearly from how many days have already gone by.
 */
data class BudgetProjection(
    val limit: Money,
    val spent: Money,
    val projectedSpend: Money,
)
{
    /** Whether, at the current pace, the month would end over the limit — even while [spent] is still under it. */
    val isPacingToExceed: Boolean = projectedSpend.value > limit.value
}

/**
 * Projects [this] progress to the end of [month], from how far into it [today] is — only meaningful for
 * the month actually running, so null for any other one (a past month has nothing left to project, a
 * future one has not started). Day 1 counts as one day elapsed, never zero, so the projection is always
 * defined within the month. Whole-cents integer division, like the rest of `Money`: it truncates, never
 * floating point.
 */
fun BudgetProgress.project(month: YearMonth, today: LocalDate): BudgetProjection?
{
    if (YearMonth.from(today) != month) return null

    val dayOfMonth = today.dayOfMonth.toLong()
    val daysInMonth = month.lengthOfMonth().toLong()
    val projectedCents = spent.value * daysInMonth / dayOfMonth

    return BudgetProjection(limit, spent, Money(projectedCents))
}
