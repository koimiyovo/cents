package com.kyovo.cents.ui.budget

import com.kyovo.cents.domain.model.BudgetCalendar
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

private val DAY_AND_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.FRENCH)
private val FULL_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.FRENCH)

/**
 * The days a budget month covers, "25 sept. – 27 oct.", for the screens to show under the month's name — or
 * null when it is the plain calendar month, where the name says it all.
 */
fun budgetCycleRangeLabel(calendar: BudgetCalendar, month: YearMonth): String?
{
    val first = calendar.startOf(month)
    val last = calendar.endOf(month).minusDays(1)
    if (first == month.atDay(1) && last == month.atEndOfMonth()) return null
    return "${DAY_AND_MONTH.format(first)} – ${DAY_AND_MONTH.format(last)}"
}

/** How far from a cycle's usual start an income can be and still look like "the pay that opens it". */
private const val SUGGESTION_WINDOW_DAYS = 7L

/** A proposal to open [month]'s budget cycle on [date], the day an income came in. */
data class CycleStartSuggestion(val date: LocalDate, val month: YearMonth)

/**
 * Whether an income received on [date] looks like the one that opens a budget cycle, and the cycle starts
 * should then move to that day: it falls within a week of where the cycle it would open now starts, but not
 * on it. Nothing is suggested when the day already is the start, or when the user already chose a start for
 * that cycle — they decided, and a second question would only nag.
 */
fun cycleStartSuggestion(date: LocalDate, calendar: BudgetCalendar): CycleStartSuggestion?
{
    val month = BudgetCalendar.monthStartingOn(date)
    if (calendar.declaredStarts.any { BudgetCalendar.monthStartingOn(it) == month }) return null
    val usualStart = calendar.startOf(month)
    if (date == usualStart) return null
    if (abs(ChronoUnit.DAYS.between(date, usualStart)) > SUGGESTION_WINDOW_DAYS) return null
    return CycleStartSuggestion(date, month)
}

/** One start the user declared, for the list that lets them take it back. */
data class DeclaredStartRow(val month: YearMonth, val monthLabel: String, val startLabel: String)

/** The declared starts, most recent cycle first. */
fun declaredStartRows(calendar: BudgetCalendar): List<DeclaredStartRow>
{
    return calendar.declaredStarts
        .map { BudgetCalendar.monthStartingOn(it) to it }
        .sortedByDescending { it.first }
        .map { (month, date) -> DeclaredStartRow(month, MonthSelector(month).label, FULL_DATE.format(date)) }
}
