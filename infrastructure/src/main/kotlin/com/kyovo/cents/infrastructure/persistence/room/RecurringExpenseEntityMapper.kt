package com.kyovo.cents.infrastructure.persistence.room

import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.RecurrenceFrequency
import com.kyovo.cents.domain.model.RecurringExpense
import com.kyovo.cents.domain.model.RecurringExpenseId
import com.kyovo.cents.domain.model.SubcategoryId
import com.kyovo.cents.domain.model.TransactionDescription
import com.kyovo.cents.domain.model.TransactionTitle
import java.time.LocalDate

fun RecurringExpense.toEntity(): RecurringExpenseEntity
{
    return RecurringExpenseEntity(
        id = id.value,
        accountId = accountId.value,
        amount = amount.value,
        title = title.value,
        subcategoryId = subcategoryId?.value,
        description = description?.value,
        frequency = frequency.name,
        interval = interval,
        startDate = startDate.toEpochDay(),
        endDate = endDate?.toEpochDay(),
        lastGeneratedDate = lastGeneratedDate?.toEpochDay(),
    )
}

fun RecurringExpenseEntity.toDomain(): RecurringExpense
{
    val recurrenceFrequency = RecurrenceFrequency.entries.find { it.name == frequency }
        ?: throw IllegalStateException("Unknown recurring expense frequency in the database: $frequency")

    return RecurringExpense(
        id = RecurringExpenseId(id),
        accountId = AccountId(accountId),
        amount = Money(amount),
        title = TransactionTitle(title),
        subcategoryId = subcategoryId?.let { SubcategoryId(it) },
        description = TransactionDescription.of(description),
        frequency = recurrenceFrequency,
        interval = interval,
        startDate = LocalDate.ofEpochDay(startDate),
        endDate = endDate?.let { LocalDate.ofEpochDay(it) },
        lastGeneratedDate = lastGeneratedDate?.let { LocalDate.ofEpochDay(it) },
    )
}
