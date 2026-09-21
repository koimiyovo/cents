package com.kyovo.cents.domain.port.input

import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.AccountCurrency
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.AccountName
import com.kyovo.cents.domain.model.AccountType
import java.time.Instant

data class OpenAccountCommand(
    val name: AccountName,
    val type: AccountType,
    val currency: AccountCurrency
)
{
    fun toAccount(id: AccountId, createdAt: Instant): Account
    {
        return Account(id, name, type, currency, createdAt)
    }
}