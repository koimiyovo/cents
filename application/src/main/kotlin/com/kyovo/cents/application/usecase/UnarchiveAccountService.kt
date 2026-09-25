package com.kyovo.cents.application.usecase

import com.kyovo.cents.domain.exception.AccountNotArchivedException
import com.kyovo.cents.domain.exception.AccountNotFoundException
import com.kyovo.cents.domain.exception.DuplicateAccountNameException
import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.port.input.UnarchiveAccountUseCase
import com.kyovo.cents.domain.port.output.AccountRepository

class UnarchiveAccountService(private val accountRepository: AccountRepository) :
    UnarchiveAccountUseCase
{
    override suspend fun unarchive(id: AccountId): Account
    {
        val existingAccount = accountRepository.findById(id) ?: throw AccountNotFoundException()

        if (existingAccount.archivedAt == null)
        {
            throw AccountNotArchivedException()
        }

        if (accountRepository.existsByName(existingAccount.name))
        {
            throw DuplicateAccountNameException()
        }

        val unarchivedAccount = existingAccount.copy(archivedAt = null)
        accountRepository.save(unarchivedAccount)
        return unarchivedAccount
    }
}