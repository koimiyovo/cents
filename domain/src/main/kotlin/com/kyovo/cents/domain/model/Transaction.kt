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
    val subcategoryId: SubcategoryId?,
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
                subcategoryId = null,
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
            subcategory: Subcategory?,
            description: TransactionDescription?,
            date: Instant
        ): Transaction
        {
            if (subcategory != null && subcategory.kind != category)
            {
                throw InvalidTransactionSubcategoryException()
            }

            return Transaction(
                id = id,
                accountId = accountId,
                amount = amount,
                title = title,
                category = category.toTransactionCategory(),
                subcategoryId = subcategory?.id,
                description = description,
                date = date
            )
        }

        fun transferOut(
            id: TransactionId,
            accountId: AccountId,
            amount: Money,
            title: TransactionTitle,
            date: Instant
        ): Transaction
        {
            return Transaction(
                id = id,
                accountId = accountId,
                amount = amount,
                title = title,
                category = TransactionCategory.TRANSFER_OUT,
                subcategoryId = null,
                description = null,
                date = date
            )
        }

        fun transferIn(
            id: TransactionId,
            accountId: AccountId,
            amount: Money,
            title: TransactionTitle,
            date: Instant
        ): Transaction
        {
            return Transaction(
                id = id,
                accountId = accountId,
                amount = amount,
                title = title,
                category = TransactionCategory.TRANSFER_IN,
                subcategoryId = null,
                description = null,
                date = date
            )
        }
    }

    /**
     * The same transaction, uncategorised. Used when the subcategory it pointed to is deleted: the
     * transaction stays, and nothing else about it (so no balance) changes.
     */
    fun withoutSubcategory(): Transaction
    {
        return copy(subcategoryId = null)
    }

    val signedAmount: Long
        get() = when (category)
        {
            TransactionCategory.EXPENSE,
            TransactionCategory.TRANSFER_OUT -> -amount.value

            TransactionCategory.INCOME,
            TransactionCategory.INITIAL_DEPOSIT,
            TransactionCategory.TRANSFER_IN  -> amount.value
        }
}
