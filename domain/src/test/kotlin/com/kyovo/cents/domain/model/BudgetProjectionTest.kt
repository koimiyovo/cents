package com.kyovo.cents.domain.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.LocalDate
import java.time.YearMonth

/**
 * Where a budget is heading if spending keeps the same pace for the rest of the month: extrapolated
 * linearly from how many days have already gone by. Only meaningful for the month actually running.
 */
class BudgetProjectionTest
{
    private val september = YearMonth.of(2026, 9) // 30 days

    private fun progress(limit: Long, spent: Long) = BudgetProgress(Money(limit), Money(spent))

    @Test
    fun `is null for a month other than the one today falls in`()
    {
        // GIVEN today is in September, asked about August and about October
        val today = LocalDate.of(2026, 9, 15)

        // WHEN / THEN
        assertThat(progress(30_000, 12_000).project(YearMonth.of(2026, 8), today)).isNull()
        assertThat(progress(30_000, 12_000).project(YearMonth.of(2026, 10), today)).isNull()
    }

    @Test
    fun `on the first day, it extrapolates as if every remaining day spent the same as day one`()
    {
        // GIVEN spent so far only on day 1 of a 30-day month
        val today = LocalDate.of(2026, 9, 1)

        // WHEN
        val projection = progress(limit = 30_000, spent = 1_000).project(september, today)

        // THEN 1_000 * 30 / 1
        assertThat(projection?.projectedSpend).isEqualTo(Money(30_000))
    }

    @Test
    fun `on the last day of the month, the projection is exactly what was spent`()
    {
        // GIVEN
        val today = LocalDate.of(2026, 9, 30)

        // WHEN
        val projection = progress(limit = 30_000, spent = 18_000).project(september, today)

        // THEN
        assertThat(projection?.projectedSpend).isEqualTo(Money(18_000))
    }

    @Test
    fun `nothing spent projects to nothing spent, whatever the day`()
    {
        val today = LocalDate.of(2026, 9, 15)

        val projection = progress(limit = 30_000, spent = 0).project(september, today)

        assertThat(projection?.projectedSpend).isEqualTo(Money(0))
    }

    @Test
    fun `truncates rather than rounds, like the rest of Money`()
    {
        // GIVEN 100 spent by day 7 of a 30-day month: 100 * 30 / 7 = 428.57...
        val today = LocalDate.of(2026, 9, 7)

        // WHEN
        val projection = progress(limit = 1_000_000, spent = 100).project(september, today)

        // THEN
        assertThat(projection?.projectedSpend).isEqualTo(Money(428))
    }

    @Test
    fun `carries the limit and the spent amount unchanged, alongside the projected one`()
    {
        val today = LocalDate.of(2026, 9, 15)

        val projection = progress(limit = 30_000, spent = 12_000).project(september, today)

        assertThat(projection).isEqualTo(BudgetProjection(Money(30_000), Money(12_000), Money(24_000)))
    }

    // The whole point: a warning before the limit is actually reached, from the pace alone.
    @ParameterizedTest(name = "limit {0}, spent {1} by day {2} of {3} -> pacing to exceed {4}")
    @CsvSource(
        "30000, 12000, 15, 30, false", // projects to 24000, under the limit
        "30000, 16000, 15, 30, true",  // projects to 32000, over the limit
        "30000, 15000, 15, 30, false", // projects to exactly 30000: not exceeding
        "30000, 15001, 15, 30, true",  // projects to 30002: just over
    )
    fun `is pacing to exceed only when the projection lands strictly above the limit`(
        limit: Long,
        spent: Long,
        dayOfMonth: Int,
        daysInMonth: Int,
        pacingToExceed: Boolean
    )
    {
        // GIVEN (daysInMonth is always 30 here, matching September 2026)
        val today = LocalDate.of(2026, 9, dayOfMonth)

        // WHEN
        val projection = progress(limit, spent).project(september, today)

        // THEN
        assertThat(projection?.isPacingToExceed).isEqualTo(pacingToExceed)
    }
}
