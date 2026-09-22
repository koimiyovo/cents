package com.kyovo.cents.application.usecase

import com.kyovo.cents.domain.exception.AccountNotFoundException
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.port.input.RecordTransactionCommand
import com.kyovo.cents.domain.port.input.RecordTransactionUseCase
import com.kyovo.cents.domain.port.output.AccountRepository
import com.kyovo.cents.domain.port.output.TransactionIdGenerator
import com.kyovo.cents.domain.port.output.TransactionRepository

class RecordTransactionService(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val transactionIdGenerator: TransactionIdGenerator
) : RecordTransactionUseCase
{
    override fun record(command: RecordTransactionCommand): Transaction
    {
        accountRepository.findById(command.accountId) ?: throw AccountNotFoundException()
        val transaction = command.toTransaction(transactionIdGenerator.generate())
        transactionRepository.save(transaction)
        return transaction
    }
}