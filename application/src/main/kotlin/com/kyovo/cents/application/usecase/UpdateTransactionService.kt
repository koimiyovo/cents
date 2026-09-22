package com.kyovo.cents.application.usecase

import com.kyovo.cents.domain.exception.CannotUpdateInitialDepositException
import com.kyovo.cents.domain.exception.TransactionNotFoundException
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.model.TransactionCategory
import com.kyovo.cents.domain.port.input.UpdateTransactionCommand
import com.kyovo.cents.domain.port.input.UpdateTransactionUseCase
import com.kyovo.cents.domain.port.output.TransactionRepository

class UpdateTransactionService(private val transactionRepository: TransactionRepository) :
    UpdateTransactionUseCase
{
    override fun update(command: UpdateTransactionCommand): Transaction
    {
        val existing =
            transactionRepository.findById(command.id) ?: throw TransactionNotFoundException()
        if (existing.category == TransactionCategory.INITIAL_DEPOSIT)
        {
            throw CannotUpdateInitialDepositException()
        }
        val updated = command.applyTo(existing)
        transactionRepository.save(updated)
        return updated
    }
}