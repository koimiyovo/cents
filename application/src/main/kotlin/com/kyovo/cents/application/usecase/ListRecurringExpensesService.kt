package com.kyovo.cents.application.usecase

import com.kyovo.cents.domain.model.RecurringExpense
import com.kyovo.cents.domain.port.input.ListRecurringExpensesUseCase
import com.kyovo.cents.domain.port.output.RecurringExpenseRepository
import kotlinx.coroutines.flow.Flow

class ListRecurringExpensesService(
    private val recurringExpenseRepository: RecurringExpenseRepository
) : ListRecurringExpensesUseCase
{
    override fun observe(): Flow<List<RecurringExpense>>
    {
        return recurringExpenseRepository.observeAll()
    }
}
