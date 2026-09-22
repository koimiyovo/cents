package com.kyovo.cents.application.usecase

import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.port.input.GetAccountUseCase
import com.kyovo.cents.domain.port.output.AccountRepository

class GetAccountService(private val accountRepository: AccountRepository) : GetAccountUseCase
{
    override fun get(id: AccountId): Account?
    {
        return accountRepository.findById(id)
    }
}