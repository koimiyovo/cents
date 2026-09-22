package com.kyovo.cents.domain.model

import java.time.Instant

data class Transaction(
    val id: TransactionId,
    val accountId: AccountId,
    val amount: Money,
    val type: TransactionType,
    val date: Instant
)