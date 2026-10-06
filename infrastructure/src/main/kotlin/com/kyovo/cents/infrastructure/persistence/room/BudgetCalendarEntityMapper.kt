package com.kyovo.cents.infrastructure.persistence.room

import com.kyovo.cents.domain.exception.InvalidBudgetStartDayException
import com.kyovo.cents.domain.model.BudgetCalendar
import com.kyovo.cents.domain.model.BudgetStartDay
import java.time.LocalDate
import java.time.YearMonth

/** The key of a cycle in the database: `year * 100 + month`, like the `month` of a budget. */
fun YearMonth.toKey(): Int
{
    return year * 100 + monthValue
}

fun LocalDate.toCycleStartEntity(): BudgetCycleStartEntity
{
    return BudgetCycleStartEntity(
        month = BudgetCalendar.monthStartingOn(this).toKey(),
        startEpochDay = toEpochDay(),
    )
}

/**
 * Puts the two tables back together. A row that contradicts itself (a date that does not open the cycle it
 * is filed under, a default day out of range) throws instead of being guessed at, like the other mappers.
 */
fun toBudgetCalendar(
    settings: List<BudgetSettingsEntity>,
    starts: List<BudgetCycleStartEntity>,
): BudgetCalendar
{
    val dates = starts.map { row ->
        val date = LocalDate.ofEpochDay(row.startEpochDay)
        if (BudgetCalendar.monthStartingOn(date).toKey() != row.month)
        {
            throw IllegalStateException("Budget cycle start $date is filed under another cycle: ${row.month}")
        }
        date
    }
    val defaultStartDay = settings.firstOrNull()?.let { row ->
        try
        {
            BudgetStartDay(row.defaultStartDay)
        } catch (_: InvalidBudgetStartDayException)
        {
            throw IllegalStateException("Unknown default budget start day in the database: ${row.defaultStartDay}")
        }
    } ?: BudgetStartDay.DEFAULT
    return BudgetCalendar(defaultStartDay, dates.toSet())
}
