package com.kyovo.cents.domain.port.input

import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.TransactionTitle
import java.time.Instant

data class RecordTransferCommand(
    val fromAccountId: AccountId,
    val toAccountId: AccountId,
    val amount: Money,
    val title: TransactionTitle,
    val date: Instant
)