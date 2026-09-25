package com.kyovo.cents.infrastructure.persistence

import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.model.TransactionId
import com.kyovo.cents.domain.port.output.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class ListTransactionRepository : TransactionRepository
{
    private val transactions = MutableStateFlow<List<Transaction>>(emptyList())

    override suspend fun save(transaction: Transaction)
    {
        // An existing transaction is replaced where it stands, like the other repositories.
        val current = transactions.value
        val index = current.indexOfFirst { it.id == transaction.id }
        transactions.value = if (index >= 0) current.toMutableList().also { it[index] = transaction } else current + transaction
    }

    internal fun snapshot(): List<Transaction>
    {
        return transactions.value
    }

    internal fun restore(snapshot: List<Transaction>)
    {
        transactions.value = snapshot
    }

    override suspend fun findById(id: TransactionId): Transaction?
    {
        return transactions.value.find { it.id == id }
    }

    override suspend fun deleteById(id: TransactionId)
    {
        transactions.value = transactions.value.filterNot { it.id == id }
    }

    override suspend fun findAll(): List<Transaction>
    {
        return transactions.value
    }

    override fun observeAll(): Flow<List<Transaction>>
    {
        return transactions
    }
}
