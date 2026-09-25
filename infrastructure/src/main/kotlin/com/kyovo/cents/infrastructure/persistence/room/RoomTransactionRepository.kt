package com.kyovo.cents.infrastructure.persistence.room

import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.model.TransactionId
import com.kyovo.cents.domain.port.output.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The transactions, stored in the Room database: the adapter that replaces the in-memory list. */
class RoomTransactionRepository(private val dao: TransactionDao) : TransactionRepository
{
    override suspend fun save(transaction: Transaction)
    {
        dao.upsert(transaction.toEntity())
    }

    override suspend fun findById(id: TransactionId): Transaction?
    {
        return dao.findById(id.value)?.toDomain()
    }

    override suspend fun deleteById(id: TransactionId)
    {
        dao.deleteById(id.value)
    }

    override suspend fun findAll(): List<Transaction>
    {
        return dao.findAll().map { it.toDomain() }
    }

    override fun observeAll(): Flow<List<Transaction>>
    {
        return dao.observeAll().map { rows -> rows.map { it.toDomain() } }
    }
}
