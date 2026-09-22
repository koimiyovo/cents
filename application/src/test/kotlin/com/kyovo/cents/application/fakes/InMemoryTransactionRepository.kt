package com.kyovo.cents.application.fakes

import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.port.output.TransactionRepository

class InMemoryTransactionRepository : TransactionRepository
{
    val saved = mutableListOf<Transaction>()

    override fun save(transaction: Transaction)
    {
        saved.add(transaction)
    }
}
