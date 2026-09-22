package com.kyovo.cents.application.fakes

import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.model.TransactionCategory
import com.kyovo.cents.domain.model.TransactionDescription
import com.kyovo.cents.domain.model.TransactionId
import com.kyovo.cents.domain.model.TransactionSubcategory
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
    category: TransactionCategory = TransactionCategory.INITIAL_DEPOSIT,
    subcategory: TransactionSubcategory? = null,
    description: TransactionDescription? = null
): Transaction
{
    return when (category)
    {
        TransactionCategory.INITIAL_DEPOSIT ->
            Transaction.openingDeposit(id, accountId, amount, date)

        TransactionCategory.EXPENSE         ->
            Transaction.recorded(
                id,
                accountId,
                amount,
                RecordableTransactionCategory.EXPENSE,
                subcategory,
                description,
                date
            )

        TransactionCategory.INCOME          ->
            Transaction.recorded(
                id,
                accountId,
                amount,
                RecordableTransactionCategory.INCOME,
                subcategory,
                description,
                date
            )
    }
}

fun aRecordTransactionCommand(
    accountId: AccountId = anAccountId(),
    amount: Money = aMoney(1_000),
    category: RecordableTransactionCategory = RecordableTransactionCategory.EXPENSE,
    date: Instant = anInstant(),
    subcategory: TransactionSubcategory? = null,
    description: TransactionDescription? = null
): RecordTransactionCommand
{
    return RecordTransactionCommand(accountId, amount, category, subcategory, description, date)
}
