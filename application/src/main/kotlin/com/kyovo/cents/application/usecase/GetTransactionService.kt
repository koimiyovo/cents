package com.kyovo.cents.application.usecase

import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.model.TransactionId
import com.kyovo.cents.domain.port.input.GetTransactionUseCase
import com.kyovo.cents.domain.port.output.TransactionRepository

class GetTransactionService(private val transactionRepository: TransactionRepository) :
    GetTransactionUseCase
{
    override suspend fun get(id: TransactionId): Transaction?
    {
        return transactionRepository.findById(id)
    }
}