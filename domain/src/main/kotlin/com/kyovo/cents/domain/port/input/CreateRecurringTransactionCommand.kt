package com.kyovo.cents.domain.port.input

import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.RecurrenceFrequency
import com.kyovo.cents.domain.model.RecurringTransaction
import com.kyovo.cents.domain.model.RecurringTransactionId
import com.kyovo.cents.domain.model.SubcategoryId
import com.kyovo.cents.domain.model.TransactionDescription
import com.kyovo.cents.domain.model.TransactionTitle
import java.time.LocalDate

data class CreateRecurringTransactionCommand(
    val accountId: AccountId,
    val amount: Money,
    val title: TransactionTitle,
    val subcategoryId: SubcategoryId?,
    val description: TransactionDescription?,
    val frequency: RecurrenceFrequency,
    val interval: Int = 1,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
)
{
    fun toRecurringTransaction(id: RecurringTransactionId): RecurringTransaction
    {
        return RecurringTransaction(
            id, accountId, amount, title, subcategoryId, description, frequency, interval, startDate, endDate
        )
    }
}
