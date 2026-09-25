package com.kyovo.cents.application.fakes

import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.model.TransactionCategory
import com.kyovo.cents.domain.model.TransactionDescription
import com.kyovo.cents.domain.model.SubcategoryId
import com.kyovo.cents.domain.model.TransactionId
import com.kyovo.cents.domain.model.TransactionTitle
import com.kyovo.cents.domain.port.input.RecordTransactionCommand
import com.kyovo.cents.domain.port.input.RecordTransferCommand
import com.kyovo.cents.domain.port.input.UpdateTransactionCommand
import java.time.Instant
import java.util.UUID

fun aTransactionId(value: String = "33333333-3333-3333-3333-333333333333"): TransactionId
{
    return TransactionId(UUID.fromString(value))
}

fun aMoney(value: Long = 0): Money
{
    return Money(value)
}

fun aTransactionTitle(value: String = "Test transaction"): TransactionTitle
{
    return TransactionTitle(value)
}

fun aTransaction(
    id: TransactionId = aTransactionId(),
    accountId: AccountId = anAccountId(),
    amount: Money = aMoney(),
    date: Instant = anInstant(),
    category: TransactionCategory = TransactionCategory.INITIAL_DEPOSIT,
    title: TransactionTitle = aTransactionTitle(),
    subcategoryId: SubcategoryId? = null,
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
                title,
                RecordableTransactionCategory.EXPENSE,
                subcategoryId?.let { aSubcategory(id = it, kind = RecordableTransactionCategory.EXPENSE) },
                description,
                date
            )

        TransactionCategory.INCOME          ->
            Transaction.recorded(
                id,
                accountId,
                amount,
                title,
                RecordableTransactionCategory.INCOME,
                subcategoryId?.let { aSubcategory(id = it, kind = RecordableTransactionCategory.INCOME) },
                description,
                date
            )

        TransactionCategory.TRANSFER_OUT    ->
            Transaction.transferOut(id, accountId, amount, title, date)

        TransactionCategory.TRANSFER_IN      ->
            Transaction.transferIn(id, accountId, amount, title, date)
    }
}

fun aRecordTransactionCommand(
    accountId: AccountId = anAccountId(),
    amount: Money = aMoney(1_000),
    title: TransactionTitle = aTransactionTitle(),
    category: RecordableTransactionCategory = RecordableTransactionCategory.EXPENSE,
    date: Instant = anInstant(),
    subcategoryId: SubcategoryId? = null,
    description: TransactionDescription? = null
): RecordTransactionCommand
{
    return RecordTransactionCommand(accountId, amount, title, category, subcategoryId, description, date)
}

fun aRecordTransferCommand(
    fromAccountId: AccountId = anAccountId("11111111-1111-1111-1111-111111111111"),
    toAccountId: AccountId = anAccountId("22222222-2222-2222-2222-222222222222"),
    amount: Money = aMoney(1_000),
    title: TransactionTitle = aTransactionTitle(),
    date: Instant = anInstant()
): RecordTransferCommand
{
    return RecordTransferCommand(fromAccountId, toAccountId, amount, title, date)
}

fun anUpdateTransactionCommand(
    id: TransactionId = aTransactionId(),
    accountId: AccountId = anAccountId(),
    amount: Money = aMoney(1_000),
    title: TransactionTitle = aTransactionTitle(),
    category: RecordableTransactionCategory = RecordableTransactionCategory.EXPENSE,
    date: Instant = anInstant(),
    subcategoryId: SubcategoryId? = null,
    description: TransactionDescription? = null
): UpdateTransactionCommand
{
    return UpdateTransactionCommand(id, accountId, amount, title, category, subcategoryId, description, date)
}
