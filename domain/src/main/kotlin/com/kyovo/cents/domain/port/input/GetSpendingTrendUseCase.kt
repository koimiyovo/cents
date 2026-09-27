package com.kyovo.cents.domain.port.input

import com.kyovo.cents.domain.model.MonthlySpending
import kotlinx.coroutines.flow.Flow
import java.time.YearMonth

/**
 * Total expenses of each month in a window ending at [month] (inclusive), oldest first, always [months]
 * entries: a month with nothing spent is a zero, not an absence, so a trend chart has one point per month.
 */
interface GetSpendingTrendUseCase
{
    fun observe(month: YearMonth, months: Int = 6): Flow<List<MonthlySpending>>
}
