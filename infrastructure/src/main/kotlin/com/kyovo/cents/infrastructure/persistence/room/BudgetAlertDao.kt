package com.kyovo.cents.infrastructure.persistence.room

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import java.util.UUID

/** The SQL of the `budget_alerts` table, checked against the schema at build time (KSP) like the others. */
@Dao
interface BudgetAlertDao
{
    /** Recording the same crossing twice (the service never does, but the table still guards it) replaces
     * the row rather than duplicating it — there is nothing to update besides the key itself anyway. */
    @Upsert
    suspend fun upsert(alert: BudgetAlertEntity)

    @Query("SELECT * FROM budget_alerts WHERE month = :month")
    suspend fun findByMonth(month: Int): List<BudgetAlertEntity>

    @Query("DELETE FROM budget_alerts WHERE subcategoryId = :subcategoryId")
    suspend fun deleteBySubcategoryId(subcategoryId: UUID)
}
