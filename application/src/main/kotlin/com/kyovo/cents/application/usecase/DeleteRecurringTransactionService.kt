package com.kyovo.cents.application.usecase

import com.kyovo.cents.domain.model.RecurringTransactionId
import com.kyovo.cents.domain.port.input.DeleteRecurringTransactionUseCase
import com.kyovo.cents.domain.port.output.RecurringTransactionRepository

class DeleteRecurringTransactionService(
    private val recurringTransactionRepository: RecurringTransactionRepository
) : DeleteRecurringTransactionUseCase
{
    override suspend fun delete(id: RecurringTransactionId)
    {
        recurringTransactionRepository.deleteById(id)
    }
}
