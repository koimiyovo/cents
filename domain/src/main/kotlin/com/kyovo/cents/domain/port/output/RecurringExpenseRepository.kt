package com.kyovo.cents.domain.port.output

import com.kyovo.cents.domain.model.RecurringExpense
import com.kyovo.cents.domain.model.RecurringExpenseId
import kotlinx.coroutines.flow.Flow

interface RecurringExpenseRepository
{
    /** Adds a new rule at the end; an existing one is replaced where it stands. */
    suspend fun save(recurringExpense: RecurringExpense)
    suspend fun findById(id: RecurringExpenseId): RecurringExpense?
    suspend fun findAll(): List<RecurringExpense>
    suspend fun deleteById(id: RecurringExpenseId)

    fun observeAll(): Flow<List<RecurringExpense>>
}
