package com.kyovo.cents.domain.port.input

import com.kyovo.cents.domain.model.RecurringExpense

interface CreateRecurringExpenseUseCase
{
    suspend fun create(command: CreateRecurringExpenseCommand): RecurringExpense
}
