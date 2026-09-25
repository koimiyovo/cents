package com.kyovo.cents.domain.port.input

import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.model.TransactionId

interface GetTransactionUseCase
{
    suspend fun get(id: TransactionId): Transaction?
}