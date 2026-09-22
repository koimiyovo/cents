package com.kyovo.cents.infrastructure.persistence

import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.AccountName
import com.kyovo.cents.domain.port.output.AccountRepository

class ListAccountRepository : AccountRepository
{
    private val accounts = mutableListOf<Account>()

    override fun save(account: Account)
    {
        accounts.removeAll { it.id == account.id }
        accounts.add(account)
    }

    override fun existsByName(name: AccountName): Boolean
    {
        return accounts.any { it.name.matches(name) }
    }

    override fun findById(id: AccountId): Account?
    {
        return accounts.find { it.id == id }
    }

    override fun findAll(): List<Account>
    {
        return accounts.toList()
    }

    override fun deleteById(id: AccountId)
    {
        accounts.removeAll { it.id == id }
    }
}