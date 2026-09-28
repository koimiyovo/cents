package com.kyovo.cents.infrastructure.persistence.room

import com.kyovo.cents.domain.model.BudgetAlert
import com.kyovo.cents.domain.model.SubcategoryId
import com.kyovo.cents.domain.port.output.BudgetAlertRepository
import java.time.YearMonth

/** The budget alerts already reported, stored in the Room database. */
class RoomBudgetAlertRepository(private val dao: BudgetAlertDao) : BudgetAlertRepository
{
    override suspend fun findByMonth(month: YearMonth): Set<BudgetAlert>
    {
        return dao.findByMonth(month.toDatabaseMonth()).map { it.toDomain() }.toSet()
    }

    override suspend fun record(alert: BudgetAlert)
    {
        dao.upsert(alert.toEntity())
    }

    override suspend fun deleteBySubcategoryId(subcategoryId: SubcategoryId)
    {
        dao.deleteBySubcategoryId(subcategoryId.value)
    }
}
