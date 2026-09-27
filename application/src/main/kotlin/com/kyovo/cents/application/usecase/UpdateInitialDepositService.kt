package com.kyovo.cents.application.usecase

import com.kyovo.cents.domain.exception.InvalidInitialDepositAmountException
import com.kyovo.cents.domain.exception.NotAnInitialDepositException
import com.kyovo.cents.domain.exception.TransactionNotFoundException
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.model.TransactionCategory
import com.kyovo.cents.domain.model.TransactionId
import com.kyovo.cents.domain.port.input.UpdateInitialDepositUseCase
import com.kyovo.cents.domain.port.output.TransactionRepository

class UpdateInitialDepositService(private val transactionRepository: TransactionRepository) :
    UpdateInitialDepositUseCase
{
    override suspend fun update(id: TransactionId, amount: Money): Transaction
    {
        if (amount.isZero())
        {
            throw InvalidInitialDepositAmountException()
        }

        val transaction = transactionRepository.findById(id) ?: throw TransactionNotFoundException()

        if (transaction.category != TransactionCategory.INITIAL_DEPOSIT)
        {
            throw NotAnInitialDepositException()
        }

        val updatedTransaction = Transaction.openingDeposit(
            id,
            transaction.accountId,
            amount,
            transaction.date
        )
        transactionRepository.save(updatedTransaction)

        return updatedTransaction
    }
}
