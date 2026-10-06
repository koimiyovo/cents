package com.kyovo.cents.ui.budget

import com.kyovo.cents.domain.model.BudgetStartDay
import com.kyovo.cents.domain.model.BudgetCalendar
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.YearMonth

class BudgetCyclesTest
{
    private val october = YearMonth.of(2026, 10)

    @Test
    fun `a plain calendar month needs no range, its name says it`()
    {
        assertThat(budgetCycleRangeLabel(BudgetCalendar(), october)).isNull()
    }

    @Test
    fun `a cycle that is not a calendar month shows the days it covers`()
    {
        // GIVEN October's cycle opens on September 25th and November's on October 28th
        val calendar = BudgetCalendar(declaredStarts = setOf(LocalDate.of(2026, 9, 25), LocalDate.of(2026, 10, 28)))

        // WHEN / THEN the end is the day before the next cycle's start
        assertThat(budgetCycleRangeLabel(calendar, october)).isEqualTo("25 sept. – 27 oct.")
    }

    @Test
    fun `a cycle of a default start day shows its range too`()
    {
        assertThat(budgetCycleRangeLabel(BudgetCalendar(defaultStartDay = BudgetStartDay(25)), october)).isEqualTo("25 sept. – 24 oct.")
    }

    // Pay on the 28th with calendar months in force: the 28th opens next month's cycle, three days early.
    @Test
    fun `an income a few days before a cycle's usual start suggests opening it that day`()
    {
        // WHEN
        val suggestion = cycleStartSuggestion(LocalDate.of(2026, 9, 28), BudgetCalendar())

        // THEN
        assertThat(suggestion).isEqualTo(CycleStartSuggestion(LocalDate.of(2026, 9, 28), october))
    }

    @Test
    fun `an income a few days after a cycle's usual start suggests it too`()
    {
        assertThat(cycleStartSuggestion(LocalDate.of(2026, 10, 4), BudgetCalendar()))
            .isEqualTo(CycleStartSuggestion(LocalDate.of(2026, 10, 4), october))
    }

    @Test
    fun `nothing is suggested when the income is on the cycle's usual start`()
    {
        assertThat(cycleStartSuggestion(LocalDate.of(2026, 9, 25), BudgetCalendar(defaultStartDay = BudgetStartDay(25)))).isNull()
    }

    @Test
    fun `nothing is suggested for an income far from any cycle start`()
    {
        // WHEN September 15th, with calendar months: the nearest start is a fortnight away
        assertThat(cycleStartSuggestion(LocalDate.of(2026, 9, 12), BudgetCalendar())).isNull()
    }

    @Test
    fun `nothing is suggested when the user already chose that cycle's start`()
    {
        // GIVEN October's start was declared on September 26th
        val calendar = BudgetCalendar(declaredStarts = setOf(LocalDate.of(2026, 9, 26)))

        // WHEN / THEN another income close to it does not ask again
        assertThat(cycleStartSuggestion(LocalDate.of(2026, 9, 28), calendar)).isNull()
    }

    @Test
    fun `the declared starts are listed most recent cycle first, with their dates`()
    {
        // GIVEN
        val calendar = BudgetCalendar(declaredStarts = setOf(LocalDate.of(2026, 8, 27), LocalDate.of(2026, 9, 28)))

        // WHEN / THEN
        assertThat(declaredStartRows(calendar)).containsExactly(
            DeclaredStartRow(YearMonth.of(2026, 10), "Octobre 2026", "28 sept. 2026"),
            DeclaredStartRow(YearMonth.of(2026, 9), "Septembre 2026", "27 août 2026"),
        )
    }
}
