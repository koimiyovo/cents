package com.kyovo.cents.infrastructure.persistence.room

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

/** The SQL of the two tables that make up the budget calendar, checked against the schema at build time (KSP). */
@Dao
interface BudgetCalendarDao
{
    @Upsert
    suspend fun upsertStart(start: BudgetCycleStartEntity)

    @Query("DELETE FROM budget_cycle_starts WHERE month = :month")
    suspend fun deleteStart(month: Int)

    @Upsert
    suspend fun upsertSettings(settings: BudgetSettingsEntity)

    /** Emits the declared starts now, then again each time the table changes. */
    @Query("SELECT * FROM budget_cycle_starts ORDER BY month")
    fun observeStarts(): Flow<List<BudgetCycleStartEntity>>

    /** Empty until the user changes the default start day for the first time. */
    @Query("SELECT * FROM budget_settings")
    fun observeSettings(): Flow<List<BudgetSettingsEntity>>
}
