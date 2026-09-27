package com.kyovo.cents.domain.model

import java.time.YearMonth

/** How much was spent in one month — a data point of a spending trend. */
data class MonthlySpending(val month: YearMonth, val total: Money)
