package com.kyovo.cents.domain.port.input

import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.Subcategory
import com.kyovo.cents.domain.model.SubcategoryId
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.model.TransactionDescription
import com.kyovo.cents.domain.model.TransactionId
import com.kyovo.cents.domain.model.TransactionTitle
import java.time.Instant

/**
 * Replaces the transaction's editable state, its account included: like the other update commands
 * it carries the whole new state, so leaving [accountId] as it is means passing the current one.
 */
data class UpdateTransactionCommand(
    val id: TransactionId,
    val accountId: AccountId,
    val amount: Money,
    val title: TransactionTitle,
    val category: RecordableTransactionCategory,
    val subcategoryId: SubcategoryId?,
    val description: TransactionDescription?,
    val date: Instant
)
{
    /** [subcategory] is the one [subcategoryId] designates, resolved by the caller (null when there is none). */
    fun toTransaction(transaction: Transaction, subcategory: Subcategory?): Transaction
    {
        return Transaction.recorded(
            transaction.id,
            accountId,
            amount,
            title,
            category,
            subcategory,
            description,
            date
        )
    }
}
