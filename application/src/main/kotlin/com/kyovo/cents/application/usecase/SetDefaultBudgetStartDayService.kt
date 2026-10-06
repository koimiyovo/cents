package com.kyovo.cents.application.usecase

import com.kyovo.cents.domain.model.BudgetStartDay
import com.kyovo.cents.domain.port.input.SetDefaultBudgetStartDayUseCase
import com.kyovo.cents.domain.port.output.BudgetCalendarRepository

class SetDefaultBudgetStartDayService(private val calendarRepository: BudgetCalendarRepository) :
    SetDefaultBudgetStartDayUseCase
{
    override suspend fun set(day: BudgetStartDay)
    {
        calendarRepository.saveDefaultStartDay(day)
    }
}
