package com.kyovo.cents.domain.port.output

import com.kyovo.cents.domain.model.BudgetAlert
import com.kyovo.cents.domain.model.SubcategoryId
import java.time.YearMonth

interface BudgetAlertRepository
{
    suspend fun findByMonth(month: YearMonth): Set<BudgetAlert>
    suspend fun record(alert: BudgetAlert)

    /** Forgets one alert (so that reaching it again is reported again). Silent when it was never recorded. */
    suspend fun delete(alert: BudgetAlert)

    suspend fun deleteBySubcategoryId(subcategoryId: SubcategoryId)
}