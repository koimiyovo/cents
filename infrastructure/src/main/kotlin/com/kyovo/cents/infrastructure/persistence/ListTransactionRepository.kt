package com.kyovo.cents.infrastructure.persistence

import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.port.output.TransactionRepository

class ListTransactionRepository : TransactionRepository
{
    private val transactions = mutableListOf<Transaction>()

    override fun save(transaction: Transaction)
    {
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
}
