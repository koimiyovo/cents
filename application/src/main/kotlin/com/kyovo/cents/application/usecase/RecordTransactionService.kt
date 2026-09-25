package com.kyovo.cents.application.usecase

import com.kyovo.cents.domain.exception.AccountNotFoundException
import com.kyovo.cents.domain.exception.CannotRecordTransactionOnArchivedAccountException
import com.kyovo.cents.domain.exception.SubcategoryNotFoundException
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.port.input.RecordTransactionCommand
import com.kyovo.cents.domain.port.input.RecordTransactionUseCase
import com.kyovo.cents.domain.port.output.AccountRepository
import com.kyovo.cents.domain.port.output.SubcategoryRepository
import com.kyovo.cents.domain.port.output.TransactionIdGenerator
import com.kyovo.cents.domain.port.output.TransactionRepository

class RecordTransactionService(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val transactionIdGenerator: TransactionIdGenerator,
    private val subcategoryRepository: SubcategoryRepository
) : RecordTransactionUseCase
{
    override fun record(command: RecordTransactionCommand): Transaction
    {
        val account =
            accountRepository.findById(command.accountId) ?: throw AccountNotFoundException()

        if (account.archivedAt != null)
        {
            throw CannotRecordTransactionOnArchivedAccountException()
        }

        val subcategory = command.subcategoryId?.let {
            subcategoryRepository.findById(it) ?: throw SubcategoryNotFoundException()
        }

        val transaction = command.toTransaction(transactionIdGenerator.generate(), subcategory)
        transactionRepository.save(transaction)
        return transaction
    }
}