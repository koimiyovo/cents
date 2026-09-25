package com.kyovo.cents.infrastructure.persistence.room

import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.AccountName
import com.kyovo.cents.domain.port.output.AccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The accounts, stored in the Room database: the adapter that replaces the in-memory list. */
class RoomAccountRepository(private val dao: AccountDao) : AccountRepository
{
    override suspend fun save(account: Account)
    {
        // The position is decided by the DAO (where the account stands, or the end): 0 is ignored.
        dao.save(account.toEntity(position = 0))
    }

    override suspend fun existsByName(name: AccountName): Boolean
    {
        // The comparison is the domain's own (case-insensitive), and only active accounts count.
        return dao.findActive().any { it.toDomain().name.matches(name) }
    }

    override suspend fun findById(id: AccountId): Account?
    {
        return dao.findById(id.value)?.toDomain()
    }

    override suspend fun findAll(): List<Account>
    {
        return dao.findAll().map { it.toDomain() }
    }

    override suspend fun deleteById(id: AccountId)
    {
        dao.deleteById(id.value)
    }

    override suspend fun reorder(orderedIds: List<AccountId>)
    {
        dao.reorder(orderedIds.map { it.value })
    }

    override fun observeAll(): Flow<List<Account>>
    {
        return dao.observeAll().map { rows -> rows.map { it.toDomain() } }
    }
}
