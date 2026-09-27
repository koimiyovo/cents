package com.kyovo.cents.ui.budget

import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.MonthlySpending
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import java.time.YearMonth

/**
 * Turns the raw monthly totals into bars: each sized against the window's own highest month, the last one
 * (the month the chart's window ends at) marked as the one worth labelling.
 */
class SpendingTrendTest
{
    private val july = YearMonth.of(2026, 7)
    private val august = YearMonth.of(2026, 8)
    private val september = YearMonth.of(2026, 9)

    @Test
    fun `a bar's fraction is its share of the window's highest month`()
    {
        // WHEN
        val trend = spendingTrend(
            listOf(
                MonthlySpending(july, Money(10_000)),
                MonthlySpending(august, Money(5_000)),
                MonthlySpending(september, Money(20_000)),
            )
        )

        // THEN
        assertThat(trend.bars.map { it.barFraction }).containsExactly(0.5f, 0.25f, 1f)
    }

    @Test
    fun `only the last month is marked selected`()
    {
        // WHEN
        val trend = spendingTrend(
            listOf(MonthlySpending(july, Money(1_000)), MonthlySpending(august, Money(2_000)))
        )

        // THEN
        assertThat(trend.bars.map { it.isSelected }).containsExactly(false, true)
    }

    @Test
    fun `each bar carries a short French month label`()
    {
        // WHEN
        val trend = spendingTrend(listOf(MonthlySpending(september, Money(1_000))))

        // THEN
        assertThat(trend.bars.single().label).isEqualTo("sept.")
    }

    @Test
    fun `the average is the mean of the window, truncated to whole cents`()
    {
        // WHEN 10_000 + 5_000 + 20_000 = 35_000 over 3 months
        val trend = spendingTrend(
            listOf(
                MonthlySpending(july, Money(10_000)),
                MonthlySpending(august, Money(5_000)),
                MonthlySpending(september, Money(20_000)),
            )
        )

        // THEN
        assertThat(trend.average).isEqualTo(Money(11_666))
    }

    // So a chart can draw the average as a reference line level with the bars themselves, not just print
    // the number apart from them.
    @Test
    fun `the average's fraction is placed on the same scale as the bars, against the window's highest month`()
    {
        // WHEN average is 11_666, the highest month is 20_000
        val trend = spendingTrend(
            listOf(
                MonthlySpending(july, Money(10_000)),
                MonthlySpending(august, Money(5_000)),
                MonthlySpending(september, Money(20_000)),
            )
        )

        // THEN
        assertThat(trend.averageFraction).isCloseTo(0.5833f, within(0.001f))
    }

    @Test
    fun `the average and its fraction are both null when nothing was spent anywhere in the window`()
    {
        // WHEN
        val trend = spendingTrend(listOf(MonthlySpending(july, Money(0)), MonthlySpending(august, Money(0))))

        // THEN
        assertThat(trend.average).isNull()
        assertThat(trend.averageFraction).isNull()
        assertThat(trend.bars.map { it.barFraction }).containsExactly(0f, 0f)
    }

    @Test
    fun `an empty window has no bars and no average`()
    {
        // WHEN
        val trend = spendingTrend(emptyList())

        // THEN
        assertThat(trend.bars).isEmpty()
        assertThat(trend.average).isNull()
    }

    @Test
    fun `bars keep the order given`()
    {
        // WHEN
        val trend = spendingTrend(
            listOf(MonthlySpending(july, Money(1_000)), MonthlySpending(august, Money(2_000)), MonthlySpending(september, Money(3_000)))
        )

        // THEN
        assertThat(trend.bars.map { it.month }).containsExactly(july, august, september)
        assertThat(trend.bars.map { it.barFraction }).allSatisfy { assertThat(it).isBetween(0f, 1f) }
        // sanity check on the closeness helper's presence (no floating point surprises expected here)
        assertThat(trend.bars[2].barFraction).isCloseTo(1f, within(0.0001f))
    }
}
