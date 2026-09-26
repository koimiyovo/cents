package com.kyovo.cents.domain.model

import com.kyovo.cents.domain.exception.InvalidBudgetLimitException
import java.time.YearMonth

data class Budget(val subcategoryId: SubcategoryId, val month: YearMonth, val limit: Money)
{
    init
    {
        if (limit.isZero())
        {
            throw InvalidBudgetLimitException()
        }
    }
}
