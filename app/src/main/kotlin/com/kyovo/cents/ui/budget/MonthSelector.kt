package com.kyovo.cents.ui.budget

import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The month a budgets screen shows: `‹ Septembre 2026 ›`. There are no bounds: planning several months
 * ahead is normal use, and looking back as far as one likes costs nothing, so it only ever moves one
 * month at a time, in either direction, and never refuses.
 */
data class MonthSelector(val month: YearMonth)
{
    fun previous(): MonthSelector
    {
        return MonthSelector(month.minusMonths(1))
    }

    fun next(): MonthSelector
    {
        return MonthSelector(month.plusMonths(1))
    }

    /** The month spelled out in French and the year, capitalised like a title: "Septembre 2026". */
    val label: String
        get() = month.format(LABEL_FORMAT).replaceFirstChar { it.uppercase(Locale.FRENCH) }

    /** Whether this is the month of [today]: the screen offers a way back to "now" only when it is elsewhere. */
    fun isCurrent(today: YearMonth): Boolean
    {
        return month == today
    }

    private companion object
    {
        val LABEL_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("LLLL yyyy", Locale.FRENCH)
    }
}
