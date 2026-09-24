package com.kyovo.cents.application.usecase

import com.kyovo.cents.domain.exception.AccountNotFoundException
import com.kyovo.cents.domain.exception.DuplicateAccountNameException
import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.port.input.UpdateAccountCommand
import com.kyovo.cents.domain.port.input.UpdateAccountUseCase
import com.kyovo.cents.domain.port.output.AccountRepository

class UpdateAccountService(private val accountRepository: AccountRepository) : UpdateAccountUseCase
{
    override fun update(command: UpdateAccountCommand): Account
    {
        val existingAccount =
            accountRepository.findById(command.id) ?: throw AccountNotFoundException()

        if (accountRepository.findAll()
                .filter { it.id != command.id && it.archivedAt == null }
                .any { it.name.matches(command.name) }
        )
        {
            throw DuplicateAccountNameException()
        }

        val updatedAccount = command.toAccount(existingAccount)

        accountRepository.save(updatedAccount)

        return updatedAccount
    }
}