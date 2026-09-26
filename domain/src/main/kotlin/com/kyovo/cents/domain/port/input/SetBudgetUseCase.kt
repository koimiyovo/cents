package com.kyovo.cents.domain.port.input

import com.kyovo.cents.domain.model.Budget

interface SetBudgetUseCase
{
    suspend fun set(command: SetBudgetCommand): Budget
}