package com.kyovo.cents.ui.budget

import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.MonthlySpending
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MONTH_LABEL_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("LLL", Locale.FRENCH)

/**
 * One bar of the trend chart: a month's total, sized as a fraction of the window's highest month (1 for the
 * highest, 0 when nothing was spent anywhere in the window). [isSelected] marks the month the chart's window
 * ends at — the one bar whose amount is worth labelling; the [label] (a short month name) carries the rest.
 */
data class SpendingTrendBar(
    val month: YearMonth,
    val label: String,
    val total: Money,
    val barFraction: Float,
    val isSelected: Boolean,
)

/**
 * [average] is the window's own mean, null when nothing was spent anywhere in it — there is nothing to
 * judge the selected month against. [averageFraction] places it on the same 0..1 scale as every bar's
 * [SpendingTrendBar.barFraction] (against the window's highest month), so a chart can draw it as a
 * reference line level with the bars themselves, not just say the number apart from them.
 */
data class SpendingTrend(val bars: List<SpendingTrendBar>, val average: Money?, val averageFraction: Float?)

/** Turns the raw monthly totals of [com.kyovo.cents.domain.port.input.GetSpendingTrendUseCase] into bars,
 * oldest first as given, the last one marked [SpendingTrendBar.isSelected]. */
fun spendingTrend(monthly: List<MonthlySpending>): SpendingTrend
{
    val max = monthly.maxOfOrNull { it.total.value } ?: 0L
    val total = monthly.sumOf { it.total.value }
    val average = if (total == 0L) null else Money(total / monthly.size)
    val averageFraction = if (max == 0L || average == null) null else (average.value.toDouble() / max).toFloat()
    val lastMonth = monthly.lastOrNull()?.month

    val bars = monthly.map {
        SpendingTrendBar(
            month = it.month,
            label = it.month.format(MONTH_LABEL_FORMAT),
            total = it.total,
            barFraction = if (max == 0L) 0f else (it.total.value.toDouble() / max).toFloat(),
            isSelected = it.month == lastMonth,
        )
    }
    return SpendingTrend(bars, average, averageFraction)
}
