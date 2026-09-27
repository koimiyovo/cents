package com.kyovo.cents.ui.budget

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.YearMonth

/**
 * The month a budgets screen shows: `‹ Septembre 2026 ›`. There are **no bounds** — planning several
 * months ahead is normal use, and looking back as far as one likes costs nothing — so the selector only
 * ever moves one month at a time, in either direction, and never refuses.
 */
class MonthSelectorTest
{
    private val september = MonthSelector(YearMonth.of(2026, 9))

    @Test
    fun `goes to the month before and to the month after`()
    {
        assertThat(september.previous().month).isEqualTo(YearMonth.of(2026, 8))
        assertThat(september.next().month).isEqualTo(YearMonth.of(2026, 10))
    }

    @Test
    fun `crosses the turn of a year in both directions`()
    {
        assertThat(MonthSelector(YearMonth.of(2027, 1)).previous().month).isEqualTo(YearMonth.of(2026, 12))
        assertThat(MonthSelector(YearMonth.of(2026, 12)).next().month).isEqualTo(YearMonth.of(2027, 1))
    }

    @Test
    fun `going forward then back, or back then forward, is where it started`()
    {
        assertThat(september.next().previous()).isEqualTo(september)
        assertThat(september.previous().next()).isEqualTo(september)
    }

    @Test
    fun `has no bounds, in the past or in the future`()
    {
        // WHEN thirty years back and ten years ahead, one month at a time
        var back = september
        repeat(30 * 12) { back = back.previous() }
        var ahead = september
        repeat(10 * 12) { ahead = ahead.next() }

        // THEN
        assertThat(back.month).isEqualTo(YearMonth.of(1996, 9))
        assertThat(ahead.month).isEqualTo(YearMonth.of(2036, 9))
    }

    // What the header reads: French, the month spelled out, capitalised like a title.
    @ParameterizedTest(name = "{0}-{1} reads {2}")
    @CsvSource(
        "2026, 1,  Janvier 2026",
        "2026, 2,  Février 2026",
        "2026, 8,  Août 2026",
        "2026, 9,  Septembre 2026",
        "2026, 12, Décembre 2026",
        "2027, 1,  Janvier 2027",
    )
    fun `is labelled with the month spelled out in French and the year`(year: Int, month: Int, label: String)
    {
        assertThat(MonthSelector(YearMonth.of(year, month)).label).isEqualTo(label)
    }

    // The screen offers a way back to "now" only when it is elsewhere, so it has to know whether it is.
    @Test
    fun `is on the current month whatever the day, and only then`()
    {
        // GIVEN today is in September 2026
        val today = YearMonth.of(2026, 9)

        // WHEN / THEN
        assertThat(september.isCurrent(today)).isTrue()
        assertThat(september.previous().isCurrent(today)).isFalse()
        assertThat(september.next().isCurrent(today)).isFalse()
        assertThat(MonthSelector(YearMonth.of(2025, 9)).isCurrent(today)).describedAs("same month, another year").isFalse()
    }
}
