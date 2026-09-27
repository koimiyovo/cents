package com.kyovo.cents.ui.home

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * The Monday-to-Sunday week the weekday-spending chart is scoped to, always — independent of the
 * Historique period filter, so a recurring expense generated weeks or months ahead (see
 * GenerateRecurringExpensesService's lookahead) never stands in for a day that hasn't happened yet.
 */
class CurrentWeekRangeTest
{
    @Test
    fun `a Monday is the start of its own week`()
    {
        // GIVEN 2026-09-14 is a Monday
        val monday = LocalDate.of(2026, 9, 14)

        // WHEN
        val (start, end) = currentWeekRange(monday)

        // THEN
        assertThat(start).isEqualTo(monday)
        assertThat(end).isEqualTo(LocalDate.of(2026, 9, 20))
    }

    @Test
    fun `a Sunday belongs to the week that started the Monday before`()
    {
        // GIVEN 2026-09-20 is a Sunday
        val sunday = LocalDate.of(2026, 9, 20)

        // WHEN
        val (start, end) = currentWeekRange(sunday)

        // THEN
        assertThat(start).isEqualTo(LocalDate.of(2026, 9, 14))
        assertThat(end).isEqualTo(sunday)
    }

    @Test
    fun `a midweek day resolves to the same Monday-to-Sunday span`()
    {
        // GIVEN 2026-09-17 is a Thursday
        val thursday = LocalDate.of(2026, 9, 17)

        // WHEN
        val (start, end) = currentWeekRange(thursday)

        // THEN
        assertThat(start).isEqualTo(LocalDate.of(2026, 9, 14))
        assertThat(end).isEqualTo(LocalDate.of(2026, 9, 20))
    }
}

class WeekRangeLabelTest
{
    @Test
    fun `a week within one month shows the day only for the start`()
    {
        assertThat(weekRangeLabel(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 20))).isEqualTo("14 – 20 sept.")
    }

    @Test
    fun `a week crossing a month boundary names the month on both ends`()
    {
        assertThat(weekRangeLabel(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 4))).isEqualTo("28 sept. – 4 oct.")
    }
}
