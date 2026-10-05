package com.kyovo.cents.application.usecase

import com.kyovo.cents.domain.port.input.SetBudgetCycleStartUseCase
import com.kyovo.cents.domain.port.output.BudgetCalendarRepository
import java.time.LocalDate

class SetBudgetCycleStartService(private val calendarRepository: BudgetCalendarRepository) :
    SetBudgetCycleStartUseCase
{
    override suspend fun set(date: LocalDate)
    {
        calendarRepository.saveCycleStart(date)
    }
}
