package com.kyovo.cents.application.fakes

import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.RecordableTransactionType
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.model.TransactionId
import com.kyovo.cents.domain.model.TransactionType
import com.kyovo.cents.domain.port.input.RecordTransactionCommand
import java.time.Instant
import kotlin.uuid.Uuid

fun aTransactionId(value: String = "33333333-3333-3333-3333-333333333333"): TransactionId
{
    return TransactionId(Uuid.parse(value))
}

fun aMoney(value: Long = 0): Money
{
    return Money(value)
}

fun aTransaction(
    id: TransactionId = aTransactionId(),
    accountId: AccountId = anAccountId(),
    amount: Money = aMoney(),
    date: Instant = anInstant(),
    type: TransactionType = TransactionType.INITIAL_DEPOSIT
): Transaction
{
    return when (type)
    {
        TransactionType.INITIAL_DEPOSIT -> Transaction.openingDeposit(id, accountId, amount, date)
        TransactionType.EXPENSE ->
            Transaction.recorded(id, accountId, amount, RecordableTransactionType.EXPENSE, date)
        TransactionType.INCOME ->
            Transaction.recorded(id, accountId, amount, RecordableTransactionType.INCOME, date)
    }
}

fun aRecordTransactionCommand(
    accountId: AccountId = anAccountId(),
    amount: Money = aMoney(1_000),
    type: RecordableTransactionType = RecordableTransactionType.EXPENSE,
    date: Instant = anInstant()
): RecordTransactionCommand
{
    return RecordTransactionCommand(accountId, amount, type, date)
}
