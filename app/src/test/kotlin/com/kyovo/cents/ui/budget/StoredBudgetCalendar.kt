package com.kyovo.cents.ui.budget

import com.kyovo.cents.domain.model.BudgetStartDay
import com.kyovo.cents.domain.model.BudgetCalendar
import com.kyovo.cents.domain.port.input.ClearBudgetCycleStartUseCase
import com.kyovo.cents.domain.port.input.GetBudgetCalendarUseCase
import com.kyovo.cents.domain.port.input.SetBudgetCycleStartUseCase
import com.kyovo.cents.domain.port.input.SetDefaultBudgetStartDayUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate
import java.time.YearMonth

/** The budget calendar's use cases over a calendar kept in memory, which a test can change while a view model watches. */
internal class StoredBudgetCalendar(initial: BudgetCalendar = BudgetCalendar()) :
    GetBudgetCalendarUseCase, SetDefaultBudgetStartDayUseCase, SetBudgetCycleStartUseCase, ClearBudgetCycleStartUseCase
{
    val state = MutableStateFlow(initial)

    override fun observe(): Flow<BudgetCalendar> = state

    override suspend fun cycleOf(date: LocalDate): YearMonth = state.value.cycleOf(date)

    override suspend fun set(day: BudgetStartDay)
    {
        state.value = BudgetCalendar(day, state.value.declaredStarts)
    }

    override suspend fun set(date: LocalDate)
    {
        val month = BudgetCalendar.monthStartingOn(date)
        val others = state.value.declaredStarts.filterNot { BudgetCalendar.monthStartingOn(it) == month }
        state.value = BudgetCalendar(state.value.defaultStartDay, (others + date).toSet())
    }

    override suspend fun clear(month: YearMonth)
    {
        state.value = BudgetCalendar(
            state.value.defaultStartDay,
            state.value.declaredStarts.filterNot { BudgetCalendar.monthStartingOn(it) == month }.toSet(),
        )
    }
}
