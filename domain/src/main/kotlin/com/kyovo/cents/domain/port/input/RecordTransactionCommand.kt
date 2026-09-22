package com.kyovo.cents.domain.port.input

import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.model.TransactionDescription
import com.kyovo.cents.domain.model.TransactionId
import com.kyovo.cents.domain.model.TransactionSubcategory
import java.time.Instant

data class RecordTransactionCommand(
    val accountId: AccountId,
    val amount: Money,
    val category: RecordableTransactionCategory,
    val subcategory: TransactionSubcategory?,
    val description: TransactionDescription?,
    val date: Instant
)
{
    fun toTransaction(id: TransactionId): Transaction
    {
        return Transaction.recorded(
            id,
            accountId,
            amount,
            category,
            subcategory,
            description,
            date
        )
    }
}