package com.kyovo.cents.application.usecase

import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.Flow
import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.port.input.GetAccountUseCase
import com.kyovo.cents.domain.port.output.AccountRepository

class GetAccountService(private val accountRepository: AccountRepository) : GetAccountUseCase
{
    override suspend fun get(id: AccountId): Account?
    {
        return accountRepository.findById(id)
    }

    override fun observe(id: AccountId): Flow<Account?>
    {
        return accountRepository.observeAll().map { accounts -> accounts.find { it.id == id } }.distinctUntilChanged()
    }
}
