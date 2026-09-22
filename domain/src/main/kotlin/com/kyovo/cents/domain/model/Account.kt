package com.kyovo.cents.domain.model

import java.time.Instant

data class Account(
    val id: AccountId,
    val name: AccountName,
    val type: AccountType,
    val currency: AccountCurrency,
    val createdAt: Instant
)