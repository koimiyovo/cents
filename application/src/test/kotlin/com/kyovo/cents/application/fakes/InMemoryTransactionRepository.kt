package com.kyovo.cents.application.fakes

import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.model.TransactionId
import com.kyovo.cents.domain.port.output.TransactionRepository

class InMemoryTransactionRepository : TransactionRepository
{
    val saved = mutableListOf<Transaction>()

    override fun save(transaction: Transaction)
    {
        saved.removeAll { it.id == transaction.id }
        saved.add(transaction)
    }

    override fun findById(id: TransactionId): Transaction?
    {
        return saved.find { it.id == id }
    }

    override fun deleteById(id: TransactionId)
    {
        saved.removeAll { it.id == id }
    }

    override fun findAll(): List<Transaction>
    {
        return saved.toList()
    }
}
