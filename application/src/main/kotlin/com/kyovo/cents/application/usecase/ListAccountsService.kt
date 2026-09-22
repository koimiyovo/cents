package com.kyovo.cents.application.usecase

import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.port.input.ListAccountsUseCase
import com.kyovo.cents.domain.port.output.AccountRepository

class ListAccountsService(private val accountRepository: AccountRepository) : ListAccountsUseCase
{
    override fun list(nameFilter: String): List<Account>
    {
        return accountRepository.findAll().filter { it.name.contains(nameFilter) }
    }
}