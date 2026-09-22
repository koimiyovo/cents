package com.kyovo.cents.domain.model

import com.kyovo.cents.domain.exception.InvalidTransactionSubcategoryException
import java.time.Instant

@ConsistentCopyVisibility
data class Transaction private constructor(
    val id: TransactionId,
    val accountId: AccountId,
    val amount: Money,
    val category: TransactionCategory,
    val subcategory: TransactionSubcategory?,
    val description: TransactionDescription?,
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
            return Transaction(
                id = id,
                accountId = accountId,
                amount = amount,
                category = TransactionCategory.INITIAL_DEPOSIT,
                subcategory = null,
                description = null,
                date = date
            )
        }

        fun recorded(
            id: TransactionId,
            accountId: AccountId,
            amount: Money,
            category: RecordableTransactionCategory,
            subcategory: TransactionSubcategory?,
            description: TransactionDescription?,
            date: Instant
        ): Transaction
        {
            if (subcategory != null && !category.accepts(subcategory))
            {
                throw InvalidTransactionSubcategoryException()
            }

            return Transaction(
                id = id,
                accountId = accountId,
                amount = amount,
                category = category.toTransactionCategory(),
                subcategory = subcategory,
                description = description,
                date = date
            )
        }
    }
}