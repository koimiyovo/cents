package com.kyovo.cents.domain.port.input

import java.time.LocalDate

interface SetBudgetCycleStartUseCase
{
    suspend fun set(date: LocalDate)
}
