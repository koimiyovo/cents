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
        val index = saved.indexOfFirst { it.id == account.id }
        if (index >= 0) saved[index] = account else saved.add(account)
    }

    override fun reorder(orderedIds: List<AccountId>)
    {
        val byId = saved.associateBy { it.id }
        val positions = saved.indices.filter { saved[it].id in orderedIds }
        positions.zip(orderedIds).forEach { (position, id) -> saved[position] = byId.getValue(id) }
    }

    override fun existsByName(name: AccountName): Boolean
    {
        return saved.any { it.archivedAt == null && it.name.matches(name) }
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