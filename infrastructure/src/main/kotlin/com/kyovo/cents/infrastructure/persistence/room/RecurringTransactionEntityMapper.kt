package com.kyovo.cents.infrastructure.persistence.room

import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.RecurrenceFrequency
import com.kyovo.cents.domain.model.RecurringTransaction
import com.kyovo.cents.domain.model.RecurringTransactionId
import com.kyovo.cents.domain.model.SubcategoryId
import com.kyovo.cents.domain.model.TransactionDescription
import com.kyovo.cents.domain.model.TransactionTitle
import java.time.LocalDate

fun RecurringTransaction.toEntity(): RecurringTransactionEntity
{
    return RecurringTransactionEntity(
        id = id.value,
        accountId = accountId.value,
        category = category.name,
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

fun RecurringTransactionEntity.toDomain(): RecurringTransaction
{
    val recurrenceFrequency = RecurrenceFrequency.entries.find { it.name == frequency }
        ?: throw IllegalStateException("Unknown recurring transaction frequency in the database: $frequency")

    val recordableCategory = RecordableTransactionCategory.entries.find { it.name == category }
        ?: throw IllegalStateException("Unknown recurring transaction category in the database: $category")

    return RecurringTransaction(
        id = RecurringTransactionId(id),
        accountId = AccountId(accountId),
        category = recordableCategory,
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
