package com.kyovo.cents.ui.transaction

import com.kyovo.cents.domain.model.BudgetCalendar
import com.kyovo.cents.domain.port.input.GetBudgetCalendarUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate
import java.time.YearMonth

/** The calendar the view model's tests run against: nothing declared, so a cycle is a calendar month. */
internal val CalendarMonths = FixedBudgetCalendar()

/** Answers from a calendar the test may change, without any storage behind it. */
internal class FixedBudgetCalendar(var calendar: BudgetCalendar = BudgetCalendar()) : GetBudgetCalendarUseCase
{
    override fun observe(): Flow<BudgetCalendar> = flowOf(calendar)

    override suspend fun cycleOf(date: LocalDate): YearMonth = calendar.cycleOf(date)
}
