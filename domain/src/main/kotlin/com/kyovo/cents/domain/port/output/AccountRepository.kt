package com.kyovo.cents.domain.port.output

import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.AccountName

interface AccountRepository
{
    /** Adds a new account at the end of the list; an existing one is replaced where it stands. */
    fun save(account: Account)
    fun existsByName(name: AccountName): Boolean
    fun findById(id: AccountId): Account?
    fun findAll(): List<Account>
    fun deleteById(id: AccountId)

    /**
     * Puts the listed accounts in the given order, into the positions the listed accounts already
     * hold: accounts that aren't listed don't move. [findAll] returns the accounts in that order.
     */
    fun reorder(orderedIds: List<AccountId>)
}