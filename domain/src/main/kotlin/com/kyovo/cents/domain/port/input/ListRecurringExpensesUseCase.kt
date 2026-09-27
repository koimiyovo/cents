package com.kyovo.cents.domain.port.input

import com.kyovo.cents.domain.model.RecurringExpense
import kotlinx.coroutines.flow.Flow

interface ListRecurringExpensesUseCase
{
    fun observe(): Flow<List<RecurringExpense>>
}
