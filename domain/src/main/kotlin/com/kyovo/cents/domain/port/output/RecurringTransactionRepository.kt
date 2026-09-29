package com.kyovo.cents.domain.port.output

import com.kyovo.cents.domain.model.RecurringTransaction
import com.kyovo.cents.domain.model.RecurringTransactionId
import kotlinx.coroutines.flow.Flow

interface RecurringTransactionRepository
{
    /** Adds a new rule at the end; an existing one is replaced where it stands. */
    suspend fun save(recurringTransaction: RecurringTransaction)
    suspend fun findById(id: RecurringTransactionId): RecurringTransaction?
    suspend fun findAll(): List<RecurringTransaction>
    suspend fun deleteById(id: RecurringTransactionId)

    fun observeAll(): Flow<List<RecurringTransaction>>
}
