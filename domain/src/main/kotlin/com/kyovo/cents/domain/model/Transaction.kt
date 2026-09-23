package com.kyovo.cents.domain.model

import com.kyovo.cents.domain.exception.InvalidTransactionSubcategoryException
import java.time.Instant

@ConsistentCopyVisibility
data class Transaction private constructor(
    val id: TransactionId,
    val accountId: AccountId,
    val amount: Money,
    val title: TransactionTitle,
    val category: TransactionCategory,
    val subcategory: TransactionSubcategory?,
    val description: TransactionDescription?,
    val date: Instant
)
{
    companion object
    {
        private val OPENING_DEPOSIT_TITLE = TransactionTitle("Initial deposit")

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
                title = OPENING_DEPOSIT_TITLE,
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
            title: TransactionTitle,
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
                title = title,
                category = category.toTransactionCategory(),
                subcategory = subcategory,
                description = description,
                date = date
            )
        }
    }

    val signedAmount: Long
        get() = when (category)
        {
            TransactionCategory.EXPENSE                                     -> -amount.value
            TransactionCategory.INCOME, TransactionCategory.INITIAL_DEPOSIT -> amount.value
        }
}
