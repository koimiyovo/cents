package com.kyovo.cents.domain.port.input

import com.kyovo.cents.domain.model.Budget
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.SubcategoryId
import java.time.YearMonth

data class SetBudgetCommand(
    val subcategoryId: SubcategoryId,
    val month: YearMonth,
    val limit: Money
)
{
    fun toBudget(): Budget
    {
        return Budget(subcategoryId, month, limit)
    }
}