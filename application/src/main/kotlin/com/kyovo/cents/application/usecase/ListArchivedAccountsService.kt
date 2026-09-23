package com.kyovo.cents.application.usecase

import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.port.input.ListArchivedAccountsUseCase
import com.kyovo.cents.domain.port.output.AccountRepository

class ListArchivedAccountsService(private val accountRepository: AccountRepository) :
    ListArchivedAccountsUseCase
{
    override fun list(): List<Account>
    {
        return accountRepository.findAll().filter { it.archivedAt != null }
    }
}