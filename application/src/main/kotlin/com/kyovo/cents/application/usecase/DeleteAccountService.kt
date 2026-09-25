package com.kyovo.cents.application.usecase

import com.kyovo.cents.domain.exception.CannotDeleteAccountWithTransactionsException
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.port.input.DeleteAccountUseCase
import com.kyovo.cents.domain.port.output.AccountRepository
import com.kyovo.cents.domain.port.output.TransactionRepository
import com.kyovo.cents.domain.port.output.UnitOfWork

class DeleteAccountService(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val unitOfWork: UnitOfWork
) : DeleteAccountUseCase
{
    override suspend fun delete(id: AccountId, deleteTransactions: Boolean)
    {
        if (!deleteTransactions && transactionRepository.findAll().any { it.accountId == id })
        {
            throw CannotDeleteAccountWithTransactionsException()
        }

        unitOfWork.execute {
            val account = accountRepository.findById(id)
            if (account != null)
            {
                transactionRepository.findAll()
                    .filter { it.accountId == id }
                    .forEach { transactionRepository.deleteById(it.id) }
                accountRepository.deleteById(id)
            }
        }
    }
}