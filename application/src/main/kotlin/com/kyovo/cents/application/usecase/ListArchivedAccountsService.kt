package com.kyovo.cents.application.usecase

import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.Flow
import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.port.input.ListArchivedAccountsUseCase
import com.kyovo.cents.domain.port.output.AccountRepository

class ListArchivedAccountsService(private val accountRepository: AccountRepository) :
    ListArchivedAccountsUseCase
{
    override fun observe(): Flow<List<Account>>
    {
        return accountRepository.observeAll()
            .map { accounts -> accounts.filter { it.archivedAt != null } }
            .distinctUntilChanged()
    }
}
