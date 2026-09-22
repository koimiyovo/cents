package com.kyovo.cents.domain.port.output

import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.AccountName

interface AccountRepository
{
    fun save(account: Account)
    fun existsByName(name: AccountName): Boolean
    fun findById(id: AccountId): Account?
    fun findAll(): List<Account>
    fun deleteById(id: AccountId)
}