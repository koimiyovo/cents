package com.kyovo.cents.domain.model

import java.time.Instant

data class Transaction private constructor(
    val id: TransactionId,
    val accountId: AccountId,
    val amount: Money,
    val type: TransactionType,
    val date: Instant
)
{
    companion object
    {
        fun openingDeposit(
            id: TransactionId,
            accountId: AccountId,
            amount: Money,
            date: Instant
        ): Transaction
        {
            return Transaction(id, accountId, amount, TransactionType.INITIAL_DEPOSIT, date)
        }

        fun recorded(
            id: TransactionId,
            accountId: AccountId,
            amount: Money,
            type: RecordableTransactionType,
            date: Instant
        ): Transaction
        {
            return Transaction(id, accountId, amount, type.toTransactionType(), date)
        }
    }
}