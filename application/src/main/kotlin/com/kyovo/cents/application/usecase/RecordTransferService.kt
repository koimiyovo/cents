package com.kyovo.cents.application.usecase

import com.kyovo.cents.domain.exception.AccountNotFoundException
import com.kyovo.cents.domain.exception.CannotRecordTransactionOnArchivedAccountException
import com.kyovo.cents.domain.exception.InvalidTransferDestinationAmountException
import com.kyovo.cents.domain.exception.TransferToSameAccountException
import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.ExchangeRates
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.model.TransferResult
import com.kyovo.cents.domain.port.input.RecordTransferCommand
import com.kyovo.cents.domain.port.input.RecordTransferUseCase
import com.kyovo.cents.domain.port.output.AccountRepository
import com.kyovo.cents.domain.port.output.ExchangeRateRepository
import com.kyovo.cents.domain.port.output.TransactionIdGenerator
import com.kyovo.cents.domain.port.output.TransactionRepository
import com.kyovo.cents.domain.port.output.UnitOfWork
import kotlinx.coroutines.flow.first
import java.time.LocalDate

class RecordTransferService(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val transactionIdGenerator: TransactionIdGenerator,
    private val unitOfWork: UnitOfWork,
    private val exchangeRateRepository: ExchangeRateRepository
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

        val receivedAmount = amountReceived(command, fromAccount, toAccount)

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
                receivedAmount,
                command.title,
                command.date
            )
            transactionRepository.save(debit)
            transactionRepository.save(credit)
            TransferResult(debit, credit)
        }
    }

    /**
     * What the destination account receives. In one currency, the amount sent. Between two currencies, what the
     * user typed (the rate of their bank), else what the exchange rates kept on the device make of the amount sent;
     * with neither (and no fixed rate to fall back on), nothing can be recorded: converting throws.
     */
    private suspend fun amountReceived(command: RecordTransferCommand, from: Account, to: Account): Money
    {
        if (from.currency == to.currency)
        {
            if (command.destinationAmount != null && command.destinationAmount != command.amount)
            {
                throw InvalidTransferDestinationAmountException()
            }
            return command.amount
        }

        command.destinationAmount?.let { return it }

        // With no rates fetched yet, the euro and the CFA franc still convert into each other: their rate is fixed.
        val rates = exchangeRateRepository.observe().first() ?: ExchangeRates(LocalDate.ofEpochDay(0), emptyMap())
        return rates.convert(command.amount, from.currency, to.currency)
    }
}
