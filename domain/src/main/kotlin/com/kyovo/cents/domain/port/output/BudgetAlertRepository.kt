package com.kyovo.cents.domain.port.output

import com.kyovo.cents.domain.model.BudgetAlert
import com.kyovo.cents.domain.model.SubcategoryId
import java.time.YearMonth

interface BudgetAlertRepository
{
    suspend fun findByMonth(month: YearMonth): Set<BudgetAlert>
    suspend fun record(alert: BudgetAlert)
    suspend fun deleteBySubcategoryId(subcategoryId: SubcategoryId)
}