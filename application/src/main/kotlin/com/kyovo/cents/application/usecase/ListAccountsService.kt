package com.kyovo.cents.application.usecase

import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.Flow
import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.port.input.ListAccountsUseCase
import com.kyovo.cents.domain.port.output.AccountRepository

class ListAccountsService(private val accountRepository: AccountRepository) : ListAccountsUseCase
{
    override fun observe(nameFilter: String): Flow<List<Account>>
    {
        return accountRepository.observeAll()
            .map { accounts -> accounts.filter { it.archivedAt == null && it.name.contains(nameFilter) } }
            .distinctUntilChanged()
    }
}
