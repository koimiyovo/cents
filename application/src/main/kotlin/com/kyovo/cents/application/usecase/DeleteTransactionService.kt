package com.kyovo.cents.application.usecase

import com.kyovo.cents.domain.exception.CannotDeleteInitialDepositException
import com.kyovo.cents.domain.model.TransactionCategory
import com.kyovo.cents.domain.model.TransactionId
import com.kyovo.cents.domain.port.input.DeleteTransactionUseCase
import com.kyovo.cents.domain.port.output.TransactionRepository

class DeleteTransactionService(private val transactionRepository: TransactionRepository) :
    DeleteTransactionUseCase
{
    override fun delete(id: TransactionId)
    {
        val transaction = transactionRepository.findById(id) ?: return
        
        if (transaction.category == TransactionCategory.INITIAL_DEPOSIT)
        {
            throw CannotDeleteInitialDepositException()
        }

        transactionRepository.deleteById(id)
    }
}