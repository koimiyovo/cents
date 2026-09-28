package com.kyovo.cents.domain.port.input

import java.time.YearMonth

interface NotifyBudgetAlertUseCase
{
    suspend fun notify(month: YearMonth)
}