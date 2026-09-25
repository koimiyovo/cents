package com.kyovo.cents.application.usecase

import com.kyovo.cents.domain.exception.DuplicateAccountNameException
import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.port.input.OpenAccountCommand
import com.kyovo.cents.domain.port.input.OpenAccountUseCase
import com.kyovo.cents.domain.port.output.AccountIdGenerator
import com.kyovo.cents.domain.port.output.AccountRepository
import com.kyovo.cents.domain.port.output.TransactionIdGenerator
import com.kyovo.cents.domain.port.output.TransactionRepository
import com.kyovo.cents.domain.port.output.UnitOfWork
import java.time.Clock

class OpenAccountService(
    private val accountRepository: AccountRepository,
    private val accountIdGenerator: AccountIdGenerator,
    private val transactionRepository: TransactionRepository,
    private val transactionIdGenerator: TransactionIdGenerator,
    private val unitOfWork: UnitOfWork,
    private val clock: Clock
) : OpenAccountUseCase
{
    override suspend fun open(command: OpenAccountCommand): Account
    {
        if (accountRepository.existsByName(command.name))
        {
            throw DuplicateAccountNameException()
        }

        return unitOfWork.execute {
            val now = clock.instant()
            val account = command.toAccount(accountIdGenerator.generate(), now)
            accountRepository.save(account)
            if (command.initialAmount.value != 0L)
            {
                val transaction = Transaction.openingDeposit(
                    transactionIdGenerator.generate(),
                    account.id,
                    command.initialAmount,
                    now
                )
                transactionRepository.save(transaction)
            }
            account
        }
    }
}