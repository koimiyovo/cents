package com.kyovo.cents.infrastructure.persistence

import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.model.TransactionId
import com.kyovo.cents.domain.port.output.TransactionRepository

class ListTransactionRepository : TransactionRepository
{
    private val transactions = mutableListOf<Transaction>()

    override fun save(transaction: Transaction)
    {
        transactions.removeAll { it.id == transaction.id }
        transactions.add(transaction)
    }

    internal fun snapshot(): List<Transaction>
    {
        return transactions.toList()
    }

    internal fun restore(snapshot: List<Transaction>)
    {
        transactions.clear()
        transactions.addAll(snapshot)
    }

    override fun findById(id: TransactionId): Transaction?
    {
        return transactions.find { it.id == id }
    }

    override fun deleteById(id: TransactionId)
    {
        transactions.removeAll { it.id == id }
    }

    override fun findAll(): List<Transaction>
    {
        return transactions.toList()
    }
}
