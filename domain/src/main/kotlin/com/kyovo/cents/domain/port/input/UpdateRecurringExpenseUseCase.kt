package com.kyovo.cents.domain.port.input

import com.kyovo.cents.domain.model.RecurringExpense

interface UpdateRecurringExpenseUseCase
{
    suspend fun update(command: UpdateRecurringExpenseCommand): RecurringExpense
}
