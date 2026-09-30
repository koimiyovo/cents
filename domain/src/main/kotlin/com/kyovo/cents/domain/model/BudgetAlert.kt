package com.kyovo.cents.domain.model

import java.time.YearMonth

data class BudgetAlert(
    val subcategoryId: SubcategoryId,
    val month: YearMonth,
    val level: BudgetAlertLevel
)
