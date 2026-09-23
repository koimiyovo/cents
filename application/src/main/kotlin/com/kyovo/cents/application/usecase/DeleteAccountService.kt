package com.kyovo.cents.application.usecase

import com.kyovo.cents.domain.exception.CannotDeleteAccountWithTransactionsException
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.port.input.DeleteAccountUseCase
import com.kyovo.cents.domain.port.output.AccountRepository
import com.kyovo.cents.domain.port.output.TransactionRepository

class DeleteAccountService(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository
) : DeleteAccountUseCase
{
    override fun delete(id: AccountId)
    {
        if (transactionRepository.findAll().any { it.accountId == id })
        {
            throw CannotDeleteAccountWithTransactionsException()
        }
        
        accountRepository.deleteById(id)
    }
}