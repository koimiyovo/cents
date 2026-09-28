package com.kyovo.cents.infrastructure.persistence.room

import com.kyovo.cents.domain.model.RecurringExpense
import com.kyovo.cents.domain.model.RecurringExpenseId
import com.kyovo.cents.domain.port.output.RecurringExpenseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The recurring expenses, stored in the Room database. */
class RoomRecurringExpenseRepository(private val dao: RecurringExpenseDao) : RecurringExpenseRepository
{
    override suspend fun save(recurringExpense: RecurringExpense)
    {
        dao.upsert(recurringExpense.toEntity())
    }

    override suspend fun findById(id: RecurringExpenseId): RecurringExpense?
    {
        return dao.findById(id.value)?.toDomain()
    }

    override suspend fun findAll(): List<RecurringExpense>
    {
        return dao.findAll().map { it.toDomain() }
    }

    override suspend fun deleteById(id: RecurringExpenseId)
    {
        dao.deleteById(id.value)
    }

    override fun observeAll(): Flow<List<RecurringExpense>>
    {
        return dao.observeAll().map { rows -> rows.map { it.toDomain() } }
    }
}
