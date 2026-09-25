package com.kyovo.cents.infrastructure.persistence.room

import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.AccountCurrency
import com.kyovo.cents.domain.model.AccountDescription
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.AccountName
import com.kyovo.cents.domain.model.AccountType
import java.util.Currency

fun Account.toEntity(position: Int): AccountEntity
{
    return AccountEntity(
        id = id.value,
        name = name.value,
        type = type.name,
        currency = currency.value.currencyCode,
        createdAt = createdAt.toEpochNanos(),
        archivedAt = archivedAt?.toEpochNanos(),
        description = description?.value,
        position = position,
    )
}

fun AccountEntity.toDomain(): Account
{
    val accountType = AccountType.entries.find { it.name == type }
        ?: throw IllegalStateException("Unknown account type in the database: $type")
    return Account(
        id = AccountId(id),
        name = AccountName(name),
        type = accountType,
        currency = AccountCurrency(Currency.getInstance(currency)),
        createdAt = createdAt.toInstantFromEpochNanos(),
        archivedAt = archivedAt?.toInstantFromEpochNanos(),
        description = AccountDescription.of(description),
    )
}
