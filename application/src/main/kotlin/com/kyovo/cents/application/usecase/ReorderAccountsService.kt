package com.kyovo.cents.application.usecase

import com.kyovo.cents.domain.exception.AccountNotFoundException
import com.kyovo.cents.domain.exception.InvalidAccountOrderException
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.port.input.ReorderAccountsUseCase
import com.kyovo.cents.domain.port.output.AccountRepository

class ReorderAccountsService(private val accountRepository: AccountRepository) :
    ReorderAccountsUseCase
{
    override fun reorder(orderedIds: List<AccountId>)
    {
        if (orderedIds.toSet().size != orderedIds.size)
        {
            throw InvalidAccountOrderException()
        }

        if (orderedIds.any { accountRepository.findById(it) == null })
        {
            throw AccountNotFoundException()
        }

        accountRepository.reorder(orderedIds)
    }
}
