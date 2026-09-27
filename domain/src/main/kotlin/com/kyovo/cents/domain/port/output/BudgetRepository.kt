package com.kyovo.cents.domain.port.output

import com.kyovo.cents.domain.model.Budget
import com.kyovo.cents.domain.model.SubcategoryId
import kotlinx.coroutines.flow.Flow

interface BudgetRepository
{
    suspend fun save(budget: Budget)
    suspend fun deleteBySubcategoryId(subcategoryId: SubcategoryId)
    fun observeAll(): Flow<List<Budget>>
}