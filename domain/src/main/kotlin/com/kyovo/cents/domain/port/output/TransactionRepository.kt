package com.kyovo.cents.domain.port.output

import com.kyovo.cents.domain.model.Transaction

interface TransactionRepository
{
    fun save(transaction: Transaction)
}