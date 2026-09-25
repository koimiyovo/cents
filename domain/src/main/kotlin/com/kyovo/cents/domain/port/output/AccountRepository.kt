package com.kyovo.cents.domain.port.output

import kotlinx.coroutines.flow.Flow
import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.AccountName

interface AccountRepository
{
    /** Adds a new account at the end of the list; an existing one is replaced where it stands. */
    suspend fun save(account: Account)
    suspend fun existsByName(name: AccountName): Boolean
    suspend fun findById(id: AccountId): Account?
    suspend fun findAll(): List<Account>
    suspend fun deleteById(id: AccountId)

    /**
     * Puts the listed accounts in the given order, into the positions the listed accounts already
     * hold: accounts that aren't listed don't move. [findAll] returns the accounts in that order.
     */
    suspend fun reorder(orderedIds: List<AccountId>)

    fun observeAll(): Flow<List<Account>>
}