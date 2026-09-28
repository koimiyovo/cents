package com.kyovo.cents.infrastructure.persistence.room

import com.kyovo.cents.domain.model.BudgetAlert
import com.kyovo.cents.domain.model.BudgetAlertLevel
import com.kyovo.cents.domain.model.SubcategoryId
import java.time.YearMonth

internal fun YearMonth.toDatabaseMonth(): Int = year * 100 + monthValue

fun BudgetAlert.toEntity(): BudgetAlertEntity
{
    return BudgetAlertEntity(
        subcategoryId = subcategoryId.value,
        month = month.toDatabaseMonth(),
        level = level.name,
    )
}

fun BudgetAlertEntity.toDomain(): BudgetAlert
{
    val year = month / 100
    val monthOfYear = month % 100
    if (monthOfYear !in 1..12)
    {
        throw IllegalStateException("Unknown budget alert month in the database: $month")
    }
    val alertLevel = BudgetAlertLevel.entries.find { it.name == level }
        ?: throw IllegalStateException("Unknown budget alert level in the database: $level")
    return BudgetAlert(
        subcategoryId = SubcategoryId(subcategoryId),
        month = YearMonth.of(year, monthOfYear),
        level = alertLevel,
    )
}
