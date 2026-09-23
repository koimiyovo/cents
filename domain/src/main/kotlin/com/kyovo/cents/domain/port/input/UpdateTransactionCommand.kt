package com.kyovo.cents.domain.port.input

import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.model.TransactionDescription
import com.kyovo.cents.domain.model.TransactionId
import com.kyovo.cents.domain.model.TransactionSubcategory
import com.kyovo.cents.domain.model.TransactionTitle
import java.time.Instant

data class UpdateTransactionCommand(
    val id: TransactionId,
    val amount: Money,
    val title: TransactionTitle,
    val category: RecordableTransactionCategory,
    val subcategory: TransactionSubcategory?,
    val description: TransactionDescription?,
    val date: Instant
)
{
    fun applyTo(transaction: Transaction): Transaction
    {
        return Transaction.recorded(
            transaction.id,
            transaction.accountId,
            amount,
            title,
            category,
            subcategory,
            description,
            date
        )
    }
}
