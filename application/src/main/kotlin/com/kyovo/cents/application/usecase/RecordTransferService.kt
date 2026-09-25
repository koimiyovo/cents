package com.kyovo.cents.application.usecase

import com.kyovo.cents.domain.exception.AccountNotFoundException
import com.kyovo.cents.domain.exception.CannotRecordTransactionOnArchivedAccountException
import com.kyovo.cents.domain.exception.TransferToSameAccountException
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.model.TransferResult
import com.kyovo.cents.domain.port.input.RecordTransferCommand
import com.kyovo.cents.domain.port.input.RecordTransferUseCase
import com.kyovo.cents.domain.port.output.AccountRepository
import com.kyovo.cents.domain.port.output.TransactionIdGenerator
import com.kyovo.cents.domain.port.output.TransactionRepository
import com.kyovo.cents.domain.port.output.UnitOfWork

class RecordTransferService(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val transactionIdGenerator: TransactionIdGenerator,
    private val unitOfWork: UnitOfWork
) : RecordTransferUseCase
{
    override suspend fun record(command: RecordTransferCommand): TransferResult
    {
        if (command.fromAccountId == command.toAccountId)
        {
            throw TransferToSameAccountException()
        }

        val fromAccount =
            accountRepository.findById(command.fromAccountId) ?: throw AccountNotFoundException()
        val toAccount =
            accountRepository.findById(command.toAccountId) ?: throw AccountNotFoundException()

        if (fromAccount.archivedAt != null || toAccount.archivedAt != null)
            throw CannotRecordTransactionOnArchivedAccountException()

        return unitOfWork.execute {
            val debit = Transaction.transferOut(
                transactionIdGenerator.generate(),
                command.fromAccountId,
                command.amount,
                command.title,
                command.date
            )
            val credit = Transaction.transferIn(
                transactionIdGenerator.generate(),
                command.toAccountId,
                command.amount,
                command.title,
                command.date
            )
            transactionRepository.save(debit)
            transactionRepository.save(credit)
            TransferResult(debit, credit)
        }
    }
}