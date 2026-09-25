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
        // An existing account is replaced where it stands: saving it again (a rename, an archival)
        // must not send it to the end of a list the user may have ordered by hand.
        val index = accounts.indexOfFirst { it.id == account.id }
        if (index >= 0) accounts[index] = account else accounts.add(account)
    }

    override fun reorder(orderedIds: List<AccountId>)
    {
        val byId = accounts.associateBy { it.id }
        // Ids matching no account are ignored, so the listed accounts always fit their own slots.
        val knownIds = orderedIds.filter { it in byId }
        val positions = accounts.indices.filter { accounts[it].id in knownIds }
        positions.zip(knownIds).forEach { (position, id) -> accounts[position] = byId.getValue(id) }
    }

    override fun existsByName(name: AccountName): Boolean
    {
        return accounts.any { it.archivedAt == null && it.name.matches(name) }
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

    internal fun snapshot(): List<Account>
    {
        return accounts.toList()
    }

    internal fun restore(snapshot: List<Account>)
    {
        accounts.clear()
        accounts.addAll(snapshot)
    }
}