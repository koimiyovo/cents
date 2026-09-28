package com.kyovo.cents.domain.port.input

import com.kyovo.cents.domain.model.BudgetAlert
import java.time.YearMonth

interface CheckBudgetAlertsUseCase
{
    suspend fun check(month: YearMonth): List<BudgetAlert>
}