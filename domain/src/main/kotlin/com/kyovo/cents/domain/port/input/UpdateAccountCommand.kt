package com.kyovo.cents.domain.port.input

import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.AccountName
import com.kyovo.cents.domain.model.AccountType

data class UpdateAccountCommand(val id: AccountId, val name: AccountName, val type: AccountType)
{
    fun toAccount(account: Account): Account
    {
        return account.copy(name = name, type = type)
    }
}