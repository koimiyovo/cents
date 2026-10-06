package com.kyovo.cents.ui.budget

import com.kyovo.cents.MainDispatcherExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

@ExtendWith(MainDispatcherExtension::class)
class BudgetCycleViewModelTest
{
    // "Today" is September 26th, 2026, UTC.
    private val clock = Clock.fixed(Instant.parse("2026-09-26T10:00:00Z"), ZoneOffset.UTC)
    private val calendar = StoredBudgetCalendar()

    // Built once the main dispatcher is in place: it observes from the start.
    private lateinit var viewModel: BudgetCycleViewModel

    @BeforeEach
    fun createTheViewModel()
    {
        viewModel = BudgetCycleViewModel(calendar, calendar, calendar, calendar, clock)
    }

    private val state get() = viewModel.uiState.value

    @Test
    fun `starts with calendar months, day 1, no range, nothing declared`()
    {
        assertThat(state.defaultStartDay).isEqualTo(1)
        assertThat(state.currentMonthLabel).isEqualTo("Septembre 2026")
        assertThat(state.currentRange).isNull()
        assertThat(state.declared).isEmpty()
    }

    @Test
    fun `changing the default start day moves the current cycle`()
    {
        // WHEN cycles open on the 25th, today (September 26th) is in October's
        viewModel.changeDefaultStartDay(25)

        // THEN
        assertThat(state.defaultStartDay).isEqualTo(25)
        assertThat(state.currentMonthLabel).isEqualTo("Octobre 2026")
        assertThat(state.currentRange).isEqualTo("25 sept. – 24 oct.")
    }

    @Test
    fun `declaring a start lists it and moves the current cycle`()
    {
        // WHEN pay came on September 25th
        viewModel.declareStart(LocalDate.of(2026, 9, 25))

        // THEN October's cycle is open, from that day
        assertThat(state.currentMonthLabel).isEqualTo("Octobre 2026")
        assertThat(state.declared)
            .containsExactly(DeclaredStartRow(YearMonth.of(2026, 10), "Octobre 2026", "25 sept. 2026"))
    }

    @Test
    fun `clearing a declared start takes it off the list`()
    {
        // GIVEN
        viewModel.declareStart(LocalDate.of(2026, 9, 25))

        // WHEN
        viewModel.clearStart(YearMonth.of(2026, 10))

        // THEN
        assertThat(state.declared).isEmpty()
        assertThat(state.currentMonthLabel).isEqualTo("Septembre 2026")
    }
}
