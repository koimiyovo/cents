package com.kyovo.cents.domain.model

import com.kyovo.cents.domain.exception.InvalidRestoredTransactionException
import com.kyovo.cents.domain.exception.InvalidTransactionAmountException
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
        // Shown as it is in the transactions list, whose language is French.
        private val OPENING_DEPOSIT_TITLE = TransactionTitle("Dépôt initial")

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
            requireNonZero(amount)

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

        /**
         * A transaction given back from storage, from the values that were stored. For the adapters
         * that read a database: the other factories take what only recording knows (a whole
         * [Subcategory], to check its kind). Not a way around the rules — what was stored went through
         * them — but a combination no factory can produce is refused: only an income or an expense
         * has a subcategory or a description.
         */
        fun restored(
            id: TransactionId,
            accountId: AccountId,
            amount: Money,
            title: TransactionTitle,
            category: TransactionCategory,
            subcategoryId: SubcategoryId?,
            description: TransactionDescription?,
            date: Instant
        ): Transaction
        {
            val canBeDescribed =
                category == TransactionCategory.INCOME || category == TransactionCategory.EXPENSE
            if (!canBeDescribed && (subcategoryId != null || description != null))
            {
                throw InvalidRestoredTransactionException()
            }

            return Transaction(
                id,
                accountId,
                amount,
                title,
                category,
                subcategoryId,
                description,
                date
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
            requireNonZero(amount)

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
            requireNonZero(amount)

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

        private fun requireNonZero(amount: Money)
        {
            if (amount.isZero())
            {
                throw InvalidTransactionAmountException()
            }
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
