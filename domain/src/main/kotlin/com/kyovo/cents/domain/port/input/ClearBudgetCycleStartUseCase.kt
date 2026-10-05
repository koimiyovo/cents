package com.kyovo.cents.domain.port.input

import java.time.YearMonth

interface ClearBudgetCycleStartUseCase
{
    suspend fun clear(month: YearMonth)
}
