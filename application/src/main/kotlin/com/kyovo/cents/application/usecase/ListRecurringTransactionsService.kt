package com.kyovo.cents.application.usecase

import com.kyovo.cents.domain.model.RecurringTransaction
import com.kyovo.cents.domain.port.input.ListRecurringTransactionsUseCase
import com.kyovo.cents.domain.port.output.RecurringTransactionRepository
import kotlinx.coroutines.flow.Flow

class ListRecurringTransactionsService(
    private val recurringTransactionRepository: RecurringTransactionRepository
) : ListRecurringTransactionsUseCase
{
    override fun observe(): Flow<List<RecurringTransaction>>
    {
        return recurringTransactionRepository.observeAll()
    }
}
