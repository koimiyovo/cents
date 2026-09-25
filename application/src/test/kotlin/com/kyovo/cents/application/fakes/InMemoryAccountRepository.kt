package com.kyovo.cents.application.fakes

import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.AccountName
import com.kyovo.cents.domain.port.output.AccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class InMemoryAccountRepository : AccountRepository
{
    private val state = MutableStateFlow<List<Account>>(emptyList())

    /** What is stored, in order (for the tests to look at). */
    val saved: List<Account> get() = state.value

    override suspend fun save(account: Account)
    {
        val current = state.value
        val index = current.indexOfFirst { it.id == account.id }
        state.value = if (index >= 0) current.toMutableList().also { it[index] = account } else current + account
    }

    override suspend fun reorder(orderedIds: List<AccountId>)
    {
        val current = state.value
        val byId = current.associateBy { it.id }
        val positions = current.indices.filter { current[it].id in orderedIds }
        val reordered = current.toMutableList()
        positions.zip(orderedIds).forEach { (position, id) -> reordered[position] = byId.getValue(id) }
        state.value = reordered
    }

    override suspend fun existsByName(name: AccountName): Boolean
    {
        return state.value.any { it.archivedAt == null && it.name.matches(name) }
    }

    override suspend fun findById(id: AccountId): Account?
    {
        return state.value.find { it.id == id }
    }

    override suspend fun findAll(): List<Account>
    {
        return state.value
    }

    override suspend fun deleteById(id: AccountId)
    {
        state.value = state.value.filterNot { it.id == id }
    }

    override fun observeAll(): Flow<List<Account>>
    {
        return state
    }
}
