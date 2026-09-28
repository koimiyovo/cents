package com.kyovo.cents.application.usecase

import com.kyovo.cents.domain.model.RecurringExpenseId
import com.kyovo.cents.domain.port.input.DeleteRecurringExpenseUseCase
import com.kyovo.cents.domain.port.output.RecurringExpenseRepository

class DeleteRecurringExpenseService(
    private val recurringExpenseRepository: RecurringExpenseRepository
) : DeleteRecurringExpenseUseCase
{
    override suspend fun delete(id: RecurringExpenseId)
    {
        recurringExpenseRepository.deleteById(id)
    }
}
