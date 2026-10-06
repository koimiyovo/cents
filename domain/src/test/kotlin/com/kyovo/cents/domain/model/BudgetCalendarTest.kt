package com.kyovo.cents.domain.model

import com.kyovo.cents.domain.exception.DuplicateBudgetCycleStartException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * A budget month is a *cycle*, not a calendar month: it starts on the day the user's money comes in. A cycle
 * is named after the month that holds most of its days, so pay received on September 28th opens "October".
 * Concretely: a start on the 1st..15th names its own month, a start on the 16th..31st names the next one.
 * A cycle ends the day before the next one starts, so the cycles tile the calendar: no day belongs to two
 * of them, none to zero.
 *
 * A start is either *declared* (a date chosen for that cycle: pay came on the 25th in September, on the 28th
 * in October) or falls back to the *default start day*, 1..28 (never 29..31, which February could not honour).
 */
class BudgetCalendarTest
{
    private val september = YearMonth.of(2026, 9)
    private val october = YearMonth.of(2026, 10)
    private val november = YearMonth.of(2026, 11)

    @Test
    fun `a start on the 1st to the 15th opens its own month, later ones open the next month`()
    {
        // WHEN / THEN
        assertThat(BudgetCalendar.monthStartingOn(LocalDate.of(2026, 9, 1))).isEqualTo(september)
        assertThat(BudgetCalendar.monthStartingOn(LocalDate.of(2026, 9, 15))).isEqualTo(september)
        assertThat(BudgetCalendar.monthStartingOn(LocalDate.of(2026, 9, 16))).isEqualTo(october)
        assertThat(BudgetCalendar.monthStartingOn(LocalDate.of(2026, 9, 28))).isEqualTo(october)
        assertThat(BudgetCalendar.monthStartingOn(LocalDate.of(2026, 12, 20))).isEqualTo(YearMonth.of(2027, 1))
    }

    @Test
    fun `without anything set, a cycle is a calendar month`()
    {
        // GIVEN
        val calendar = BudgetCalendar()

        // WHEN / THEN
        assertThat(calendar.startOf(september)).isEqualTo(LocalDate.of(2026, 9, 1))
        assertThat(calendar.endOf(september)).isEqualTo(LocalDate.of(2026, 10, 1))
    }

    @Test
    fun `a default start day after the 15th starts each cycle in the previous month`()
    {
        // GIVEN
        val calendar = BudgetCalendar(defaultStartDay = BudgetStartDay(25))

        // WHEN / THEN October's cycle begins on September 25th, the day pay usually comes
        assertThat(calendar.startOf(october)).isEqualTo(LocalDate.of(2026, 9, 25))
        assertThat(calendar.endOf(october)).isEqualTo(LocalDate.of(2026, 10, 25))
    }

    @Test
    fun `a default start day up to the 15th starts each cycle in its own month`()
    {
        // GIVEN
        val calendar = BudgetCalendar(defaultStartDay = BudgetStartDay(5))

        // WHEN / THEN
        assertThat(calendar.startOf(october)).isEqualTo(LocalDate.of(2026, 10, 5))
        assertThat(calendar.endOf(october)).isEqualTo(LocalDate.of(2026, 11, 5))
    }

    @Test
    fun `a declared start replaces the default one for the cycle it opens only`()
    {
        // GIVEN pay came on September 25th (October's cycle) and on October 28th (November's), default the 1st
        val calendar = BudgetCalendar(declaredStarts = setOf(LocalDate.of(2026, 9, 25), LocalDate.of(2026, 10, 28)))

        // WHEN / THEN October runs 25 Sep → 27 Oct (the end is exclusive), November 28 Oct → 30 Nov
        assertThat(calendar.startOf(october)).isEqualTo(LocalDate.of(2026, 9, 25))
        assertThat(calendar.endOf(october)).isEqualTo(LocalDate.of(2026, 10, 28))
        assertThat(calendar.startOf(november)).isEqualTo(LocalDate.of(2026, 10, 28))
        assertThat(calendar.endOf(november)).isEqualTo(LocalDate.of(2026, 12, 1))
        // September had no declared start and ends where October's begins
        assertThat(calendar.startOf(september)).isEqualTo(LocalDate.of(2026, 9, 1))
        assertThat(calendar.endOf(september)).isEqualTo(LocalDate.of(2026, 9, 25))
    }

    @Test
    fun `a cycle ends where the next one starts, across a year end`()
    {
        // GIVEN pay came on December 28th, which opens January
        val calendar = BudgetCalendar(defaultStartDay = BudgetStartDay(25), declaredStarts = setOf(LocalDate.of(2026, 12, 28)))

        // WHEN / THEN
        assertThat(calendar.endOf(YearMonth.of(2026, 12))).isEqualTo(LocalDate.of(2026, 12, 28))
        assertThat(calendar.startOf(YearMonth.of(2027, 1))).isEqualTo(LocalDate.of(2026, 12, 28))
    }

    @Test
    fun `a date belongs to the cycle that started on or before it`()
    {
        // GIVEN default day 25, but pay of October 28th (opening November) came later than usual
        val calendar = BudgetCalendar(defaultStartDay = BudgetStartDay(25), declaredStarts = setOf(LocalDate.of(2026, 10, 28)))

        // WHEN / THEN
        assertThat(calendar.cycleOf(LocalDate.of(2026, 9, 24))).isEqualTo(september)
        assertThat(calendar.cycleOf(LocalDate.of(2026, 9, 25))).isEqualTo(october)
        assertThat(calendar.cycleOf(LocalDate.of(2026, 10, 27))).isEqualTo(october)
        assertThat(calendar.cycleOf(LocalDate.of(2026, 10, 28))).isEqualTo(november)
        assertThat(calendar.cycleOf(LocalDate.of(2026, 11, 24))).isEqualTo(november)
        assertThat(calendar.cycleOf(LocalDate.of(2026, 11, 25))).isEqualTo(YearMonth.of(2026, 12))
    }

    @Test
    fun `the days around a year end belong to the cycle that is open`()
    {
        // GIVEN January's cycle starts on December 25th
        val calendar = BudgetCalendar(defaultStartDay = BudgetStartDay(25))

        // WHEN / THEN
        assertThat(calendar.cycleOf(LocalDate.of(2026, 12, 24))).isEqualTo(YearMonth.of(2026, 12))
        assertThat(calendar.cycleOf(LocalDate.of(2026, 12, 25))).isEqualTo(YearMonth.of(2027, 1))
        assertThat(calendar.cycleOf(LocalDate.of(2027, 1, 3))).isEqualTo(YearMonth.of(2027, 1))
    }

    // The guarantee everything else leans on: spending is attributed by date, so each day must have exactly
    // one cycle, and `startOf`/`endOf`/`cycleOf` must agree about where its edges are.
    @Test
    fun `every day of a year lies inside exactly the cycle that cycleOf names`()
    {
        // GIVEN a late default day and some declared starts, early and late in the month
        val calendar = BudgetCalendar(
            defaultStartDay = BudgetStartDay(28),
            declaredStarts = setOf(LocalDate.of(2026, 3, 2), LocalDate.of(2026, 9, 25), LocalDate.of(2026, 10, 28))
        )

        // WHEN / THEN
        generateSequence(LocalDate.of(2026, 1, 1)) { it.plusDays(1) }
            .takeWhile { it.year == 2026 }
            .forEach { day ->
                val cycle = calendar.cycleOf(day)
                assertThat(calendar.startOf(cycle)).isBeforeOrEqualTo(day)
                assertThat(day).isBefore(calendar.endOf(cycle))
            }
    }

    // Two starts opening the same cycle would make it begin twice.
    @Test
    fun `refuses two declared starts that open the same cycle`()
    {
        // WHEN / THEN September 25th and October 3rd both open October
        assertThatThrownBy {
            BudgetCalendar(declaredStarts = setOf(LocalDate.of(2026, 9, 25), LocalDate.of(2026, 10, 3)))
        }.isInstanceOf(DuplicateBudgetCycleStartException::class.java)
    }

    @Test
    fun `two starts in the same calendar month are fine when they open different cycles`()
    {
        // WHEN / THEN September 5th opens September, September 25th opens October
        val calendar = BudgetCalendar(declaredStarts = setOf(LocalDate.of(2026, 9, 5), LocalDate.of(2026, 9, 25)))

        assertThat(calendar.startOf(september)).isEqualTo(LocalDate.of(2026, 9, 5))
        assertThat(calendar.startOf(october)).isEqualTo(LocalDate.of(2026, 9, 25))
    }

    // The progress service uses the calendar as a flow value and must not redraw for an identical one.
    @Test
    fun `two calendars with the same day and starts are equal`()
    {
        // GIVEN
        val first = BudgetCalendar(BudgetStartDay(25), setOf(LocalDate.of(2026, 9, 26), LocalDate.of(2026, 10, 28)))
        val second = BudgetCalendar(BudgetStartDay(25), setOf(LocalDate.of(2026, 10, 28), LocalDate.of(2026, 9, 26)))

        // WHEN / THEN
        assertThat(first).isEqualTo(second)
    }
}
