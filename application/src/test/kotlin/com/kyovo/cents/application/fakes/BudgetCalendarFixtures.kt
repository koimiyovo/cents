package com.kyovo.cents.application.fakes

import com.kyovo.cents.domain.model.BudgetStartDay
import com.kyovo.cents.domain.model.BudgetCalendar
import com.kyovo.cents.domain.port.output.BudgetCalendarRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate
import java.time.YearMonth

class InMemoryBudgetCalendarRepository : BudgetCalendarRepository
{
    private val state = MutableStateFlow(BudgetCalendar())

    /** What is stored (for the tests to look at). */
    val saved: BudgetCalendar get() = state.value

    override suspend fun saveDefaultStartDay(day: BudgetStartDay)
    {
        state.value = BudgetCalendar(day, state.value.declaredStarts)
    }

    override suspend fun saveCycleStart(date: LocalDate)
    {
        val month = BudgetCalendar.monthStartingOn(date)
        val others = state.value.declaredStarts.filterNot { BudgetCalendar.monthStartingOn(it) == month }
        state.value = BudgetCalendar(state.value.defaultStartDay, (others + date).toSet())
    }

    override suspend fun deleteCycleStart(month: YearMonth)
    {
        state.value = BudgetCalendar(
            state.value.defaultStartDay,
            state.value.declaredStarts.filterNot { BudgetCalendar.monthStartingOn(it) == month }.toSet()
        )
    }

    override fun observe(): Flow<BudgetCalendar>
    {
        return state
    }
}
