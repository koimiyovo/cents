package com.kyovo.cents.application.usecase

import com.kyovo.cents.domain.port.input.ClearBudgetCycleStartUseCase
import com.kyovo.cents.domain.port.output.BudgetCalendarRepository
import java.time.YearMonth

class ClearBudgetCycleStartService(private val calendarRepository: BudgetCalendarRepository) :
    ClearBudgetCycleStartUseCase
{
    override suspend fun clear(month: YearMonth)
    {
        calendarRepository.deleteCycleStart(month)
    }
}
