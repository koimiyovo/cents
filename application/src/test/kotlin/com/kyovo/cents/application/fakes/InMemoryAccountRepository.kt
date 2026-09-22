package com.kyovo.cents.application.fakes

import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.AccountName
import com.kyovo.cents.domain.port.output.AccountRepository

class InMemoryAccountRepository : AccountRepository
{
    val saved = mutableListOf<Account>()

    override fun save(account: Account)
    {
        saved.removeAll { it.id == account.id }
        saved.add(account)
    }

    override fun existsByName(name: AccountName): Boolean
    {
        return saved.any { it.name.matches(name) }
    }

    override fun findById(id: AccountId): Account?
    {
        return saved.find { it.id == id }
    }

    override fun findAll(): List<Account>
    {
        return saved.toList()
    }

    override fun deleteById(id: AccountId)
    {
        saved.removeAll { it.id == id }
    }
}