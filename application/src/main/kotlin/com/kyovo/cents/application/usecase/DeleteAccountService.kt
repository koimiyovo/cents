package com.kyovo.cents.application.usecase

import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.port.input.DeleteAccountUseCase
import com.kyovo.cents.domain.port.output.AccountRepository

class DeleteAccountService(private val accountRepository: AccountRepository) : DeleteAccountUseCase
{
    override fun delete(id: AccountId)
    {
        accountRepository.deleteById(id)
    }
}