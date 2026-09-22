package com.kyovo.cents.infrastructure.persistence

import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.AccountName
import com.kyovo.cents.domain.port.output.AccountRepository

class ListAccountRepository : AccountRepository
{
    private val accounts = mutableListOf<Account>()

    override fun save(account: Account)
    {
        accounts.add(account)
    }

    override fun existsByName(name: AccountName): Boolean
    {
        return accounts.any { it.name.matches(name) }
    }
}