package com.kyovo.cents.infrastructure.persistence.room

import com.kyovo.cents.domain.exception.InvalidRestoredTransactionException
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.SubcategoryId
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.model.TransactionCategory
import com.kyovo.cents.domain.model.TransactionDescription
import com.kyovo.cents.domain.model.TransactionId
import com.kyovo.cents.domain.model.TransactionTitle

fun Transaction.toEntity(): TransactionEntity
{
    return TransactionEntity(
        id = id.value,
        accountId = accountId.value,
        amount = amount.value,
        title = title.value,
        category = category.name,
        subcategoryId = subcategoryId?.value,
        description = description?.value,
        date = date.toEpochNanos(),
    )
}

fun TransactionEntity.toDomain(): Transaction
{
    val transactionCategory = TransactionCategory.entries.find { it.name == category }
        ?: throw IllegalStateException("Unknown transaction category in the database: $category")
    return try
    {
        Transaction.restored(
            id = TransactionId(id),
            accountId = AccountId(accountId),
            amount = Money(amount),
            title = TransactionTitle(title),
            category = transactionCategory,
            subcategoryId = subcategoryId?.let { SubcategoryId(it) },
            description = TransactionDescription.of(description),
            date = date.toInstantFromEpochNanos(),
        )
    } catch (e: InvalidRestoredTransactionException)
    {
        // The domain's message is generic; say which row is wrong. (Both are IllegalStateExceptions.)
        throw IllegalStateException("Inconsistent transaction row in the database: $id ($category)", e)
    }
}
