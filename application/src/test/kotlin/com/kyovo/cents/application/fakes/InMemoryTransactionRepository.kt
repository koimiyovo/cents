package com.kyovo.cents.application.fakes

import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.model.TransactionId
import com.kyovo.cents.domain.port.output.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class InMemoryTransactionRepository : TransactionRepository
{
    private val state = MutableStateFlow<List<Transaction>>(emptyList())

    /** What is stored, in order (for the tests to look at). */
    val saved: List<Transaction> get() = state.value

    override suspend fun save(transaction: Transaction)
    {
        val current = state.value
        val index = current.indexOfFirst { it.id == transaction.id }
        state.value = if (index >= 0) current.toMutableList().also { it[index] = transaction } else current + transaction
    }

    override suspend fun findById(id: TransactionId): Transaction?
    {
        return state.value.find { it.id == id }
    }

    override suspend fun deleteById(id: TransactionId)
    {
        state.value = state.value.filterNot { it.id == id }
    }

    override suspend fun findAll(): List<Transaction>
    {
        return state.value
    }

    override fun observeAll(): Flow<List<Transaction>>
    {
        return state
    }
}
