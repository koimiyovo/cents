package com.kyovo.cents.infrastructure.persistence.room

import com.kyovo.cents.domain.model.Budget
import com.kyovo.cents.domain.model.SubcategoryId
import com.kyovo.cents.domain.port.output.BudgetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The budgets, stored in the Room database. */
class RoomBudgetRepository(private val dao: BudgetDao) : BudgetRepository
{
    override suspend fun save(budget: Budget)
    {
        dao.upsert(budget.toEntity())
    }

    override suspend fun deleteBySubcategoryId(subcategoryId: SubcategoryId)
    {
        dao.deleteBySubcategoryId(subcategoryId.value)
    }

    override fun observeAll(): Flow<List<Budget>>
    {
        return dao.observeAll().map { rows -> rows.map { it.toDomain() } }
    }
}
