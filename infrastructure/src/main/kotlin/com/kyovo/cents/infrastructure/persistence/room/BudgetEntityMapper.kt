package com.kyovo.cents.infrastructure.persistence.room

import com.kyovo.cents.domain.model.Budget
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.SubcategoryId
import java.time.YearMonth

fun Budget.toEntity(): BudgetEntity
{
    return BudgetEntity(
        subcategoryId = subcategoryId.value,
        month = month.year * 100 + month.monthValue,
        limitCents = limit.value,
    )
}

fun BudgetEntity.toDomain(): Budget
{
    val year = month / 100
    val monthOfYear = month % 100
    if (monthOfYear !in 1..12)
    {
        throw IllegalStateException("Unknown budget month in the database: $month")
    }
    if (limitCents <= 0)
    {
        throw IllegalStateException("Budget limit in the database must be above zero: $limitCents")
    }
    return Budget(
        subcategoryId = SubcategoryId(subcategoryId),
        month = YearMonth.of(year, monthOfYear),
        limit = Money(limitCents),
    )
}
