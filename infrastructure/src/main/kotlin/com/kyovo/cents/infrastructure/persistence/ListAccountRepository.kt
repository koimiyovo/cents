package com.kyovo.cents.infrastructure.persistence

import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.AccountName
import com.kyovo.cents.domain.port.output.AccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class ListAccountRepository : AccountRepository
{
    private val accounts = MutableStateFlow<List<Account>>(emptyList())

    override suspend fun save(account: Account)
    {
        val current = accounts.value
        val index = current.indexOfFirst { it.id == account.id }
        accounts.value = if (index >= 0) current.toMutableList().also { it[index] = account } else current + account
    }

    override suspend fun reorder(orderedIds: List<AccountId>)
    {
        val current = accounts.value
        val byId = current.associateBy { it.id }
        val knownIds = orderedIds.filter { it in byId }
        val positions = current.indices.filter { current[it].id in knownIds }
        val reordered = current.toMutableList()
        positions.zip(knownIds).forEach { (position, id) -> reordered[position] = byId.getValue(id) }
        accounts.value = reordered
    }

    override suspend fun existsByName(name: AccountName): Boolean
    {
        return accounts.value.any { it.archivedAt == null && it.name.matches(name) }
    }

    override suspend fun findById(id: AccountId): Account?
    {
        return accounts.value.find { it.id == id }
    }

    override suspend fun findAll(): List<Account>
    {
        return accounts.value
    }

    override suspend fun deleteById(id: AccountId)
    {
        accounts.value = accounts.value.filterNot { it.id == id }
    }

    override fun observeAll(): Flow<List<Account>>
    {
        return accounts
    }

    internal fun snapshot(): List<Account>
    {
        return accounts.value
    }

    internal fun restore(snapshot: List<Account>)
    {
        accounts.value = snapshot
    }
}
