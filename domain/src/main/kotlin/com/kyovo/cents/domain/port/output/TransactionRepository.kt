package com.kyovo.cents.domain.port.output

import kotlinx.coroutines.flow.Flow
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.model.TransactionId

interface TransactionRepository
{
    suspend fun save(transaction: Transaction)
    suspend fun findById(id: TransactionId): Transaction?
    suspend fun deleteById(id: TransactionId)
    suspend fun findAll(): List<Transaction>

    fun observeAll(): Flow<List<Transaction>>
}