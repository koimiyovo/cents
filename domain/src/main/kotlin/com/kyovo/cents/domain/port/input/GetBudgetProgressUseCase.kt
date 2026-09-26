package com.kyovo.cents.domain.port.input

import com.kyovo.cents.domain.model.BudgetProgress
import com.kyovo.cents.domain.model.SubcategoryId
import kotlinx.coroutines.flow.Flow
import java.time.YearMonth

interface GetBudgetProgressUseCase
{
    fun observe(subcategoryId: SubcategoryId, month: YearMonth): Flow<BudgetProgress?>
}