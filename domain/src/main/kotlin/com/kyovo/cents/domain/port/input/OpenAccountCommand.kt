package com.kyovo.cents.domain.port.input

import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.AccountCurrency
import com.kyovo.cents.domain.model.AccountDescription
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.AccountName
import com.kyovo.cents.domain.model.AccountType
import com.kyovo.cents.domain.model.Money
import java.time.Instant

data class OpenAccountCommand(
    val name: AccountName,
    val type: AccountType,
    val currency: AccountCurrency,
    val initialAmount: Money = Money(0),
    val description: AccountDescription? = null
)
{
    fun toAccount(id: AccountId, createdAt: Instant): Account
    {
        return Account(id, name, type, currency, createdAt, description = description)
    }
}