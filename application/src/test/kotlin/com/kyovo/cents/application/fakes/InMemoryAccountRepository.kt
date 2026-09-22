package com.kyovo.cents.application.fakes

import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.AccountName
import com.kyovo.cents.domain.port.output.AccountRepository

class InMemoryAccountRepository : AccountRepository
{
    val saved = mutableListOf<Account>()

    override fun save(account: Account)
    {
        saved.add(account)
    }

    override fun existsByName(name: AccountName): Boolean
    {
        return saved.any { it.name.matches(name) }
    }
}