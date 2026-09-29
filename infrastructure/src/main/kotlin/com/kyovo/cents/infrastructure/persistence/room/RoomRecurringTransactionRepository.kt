package com.kyovo.cents.infrastructure.persistence.room

import com.kyovo.cents.domain.model.RecurringTransaction
import com.kyovo.cents.domain.model.RecurringTransactionId
import com.kyovo.cents.domain.port.output.RecurringTransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The recurring expenses, stored in the Room database. */
class RoomRecurringTransactionRepository(private val dao: RecurringTransactionDao) : RecurringTransactionRepository
{
    override suspend fun save(recurringTransaction: RecurringTransaction)
    {
        dao.upsert(recurringTransaction.toEntity())
    }

    override suspend fun findById(id: RecurringTransactionId): RecurringTransaction?
    {
        return dao.findById(id.value)?.toDomain()
    }

    override suspend fun findAll(): List<RecurringTransaction>
    {
        return dao.findAll().map { it.toDomain() }
    }

    override suspend fun deleteById(id: RecurringTransactionId)
    {
        dao.deleteById(id.value)
    }

    override fun observeAll(): Flow<List<RecurringTransaction>>
    {
        return dao.observeAll().map { rows -> rows.map { it.toDomain() } }
    }
}
