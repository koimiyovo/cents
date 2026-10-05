package com.kyovo.cents.infrastructure.persistence.room

import androidx.room3.Entity
import androidx.room3.PrimaryKey

/**
 * A row of the `budget_cycle_starts` table: the start date the user declared for one budget cycle.
 *
 * The primary key is the cycle the date opens, as a single number, `year * 100 + month` (the cycle that
 * starts on 25 September 2026 is October's, `202610`), so declaring a start again for the same cycle
 * replaces it. The date is stored as its epoch day: a start is a day, not an instant.
 */
@Entity(tableName = "budget_cycle_starts")
data class BudgetCycleStartEntity(
    @PrimaryKey val month: Int,
    val startEpochDay: Long,
)

/**
 * The single row of the `budget_settings` table (its `id` is always [ROW_ID]): the day of the month a
 * budget cycle starts on when none was declared for it. No row yet means the default of the domain.
 */
@Entity(tableName = "budget_settings")
data class BudgetSettingsEntity(
    @PrimaryKey val id: Int = ROW_ID,
    val defaultStartDay: Int,
)
{
    companion object
    {
        const val ROW_ID = 1
    }
}
