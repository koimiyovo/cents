package com.kyovo.cents.application.usecase

import com.kyovo.cents.domain.exception.CannotDeleteInitialDepositException
import com.kyovo.cents.domain.exception.CannotDeleteTransferException
import com.kyovo.cents.domain.model.TransactionCategory.INITIAL_DEPOSIT
import com.kyovo.cents.domain.model.TransactionCategory.TRANSFER_IN
import com.kyovo.cents.domain.model.TransactionCategory.TRANSFER_OUT
import com.kyovo.cents.domain.model.TransactionId
import com.kyovo.cents.domain.port.input.DeleteTransactionUseCase
import com.kyovo.cents.domain.port.output.TransactionRepository

class DeleteTransactionService(private val transactionRepository: TransactionRepository) :
    DeleteTransactionUseCase
{
    override suspend fun delete(id: TransactionId)
    {
        val transaction = transactionRepository.findById(id) ?: return

        if (transaction.category == INITIAL_DEPOSIT)
        {
            throw CannotDeleteInitialDepositException()
        }

        if (transaction.category == TRANSFER_OUT || transaction.category == TRANSFER_IN)
        {
            throw CannotDeleteTransferException()
        }

        transactionRepository.deleteById(id)
    }
}