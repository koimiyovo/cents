package com.kyovo.cents.domain.port.input

import com.kyovo.cents.domain.model.BudgetStartDay

interface SetDefaultBudgetStartDayUseCase
{
    suspend fun set(day: BudgetStartDay)
}
