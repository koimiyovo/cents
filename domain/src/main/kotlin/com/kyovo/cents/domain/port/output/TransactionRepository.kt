package com.kyovo.cents.domain.port.output

import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.model.TransactionId

interface TransactionRepository
{
    fun save(transaction: Transaction)
    fun findById(id: TransactionId): Transaction?
    fun deleteById(id: TransactionId)
}