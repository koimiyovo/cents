package com.kyovo.cents.application.usecase

import com.kyovo.cents.domain.model.BudgetCalendar
import com.kyovo.cents.domain.port.input.GetBudgetCalendarUseCase
import com.kyovo.cents.domain.port.output.BudgetCalendarRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.YearMonth

class GetBudgetCalendarService(private val calendarRepository: BudgetCalendarRepository) : GetBudgetCalendarUseCase
{
    override fun observe(): Flow<BudgetCalendar>
    {
        return calendarRepository.observe()
    }

    override suspend fun cycleOf(date: LocalDate): YearMonth
    {
        return calendarRepository.observe().first().cycleOf(date)
    }
}
