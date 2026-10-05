package com.kyovo.cents.domain.model

import com.kyovo.cents.domain.exception.DuplicateBudgetCycleStartException
import java.time.LocalDate
import java.time.YearMonth

/**
 * Where each budget month (a *cycle*) starts and ends. A cycle runs from its start to the day before the
 * next cycle's start, so the cycles tile the calendar.
 *
 * A cycle is named after the month that holds most of its days: a start on the 1st..15th names its own
 * month, a start on the 16th..31st names the next one (pay received on September 28th funds "October").
 * That keeps the starts of consecutive cycles strictly increasing whatever the user declares.
 *
 * A start is *declared* (a date chosen for one cycle) or falls back to [defaultStartDay].
 */
data class BudgetCalendar(
    val defaultStartDay: BudgetStartDay = BudgetStartDay.DEFAULT,
    val declaredStarts: Set<LocalDate> = emptySet()
)
{
    init
    {
        if (declaredStarts.map(::monthStartingOn).toSet().size != declaredStarts.size)
        {
            throw DuplicateBudgetCycleStartException()
        }
    }

    fun startOf(month: YearMonth): LocalDate
    {
        return declaredStarts.firstOrNull { monthStartingOn(it) == month } ?: defaultStartOf(month)
    }

    /** The first day *after* the cycle: the start of the next one. */
    fun endOf(month: YearMonth): LocalDate
    {
        return startOf(month.plusMonths(1))
    }

    fun cycleOf(date: LocalDate): YearMonth
    {
        val candidate = monthStartingOn(date)
        return if (date < startOf(candidate)) candidate.minusMonths(1) else candidate
    }

    private fun defaultStartOf(month: YearMonth): LocalDate
    {
        val day = defaultStartDay.value
        val startMonth = if (day <= LAST_DAY_NAMING_ITS_OWN_MONTH) month else month.minusMonths(1)
        return startMonth.atDay(day)
    }

    companion object
    {
        private const val LAST_DAY_NAMING_ITS_OWN_MONTH = 15

        /** The cycle a start date opens, i.e. the month the cycle is named after. */
        fun monthStartingOn(start: LocalDate): YearMonth
        {
            val month = YearMonth.from(start)
            return if (start.dayOfMonth <= LAST_DAY_NAMING_ITS_OWN_MONTH) month else month.plusMonths(1)
        }
    }
}
