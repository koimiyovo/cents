package com.kyovo.cents.application.usecase

import com.kyovo.cents.application.fakes.InMemoryBudgetCalendarRepository
import com.kyovo.cents.domain.model.BudgetStartDay
import com.kyovo.cents.domain.model.BudgetCalendar
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * The calendar as the rest of the app reads it: the whole [BudgetCalendar] to show where a cycle starts and
 * ends, and a one-shot "which cycle is this day in" for what happens once (the alert check after a
 * transaction, the daily worker) and has no reason to stay subscribed.
 */
class GetBudgetCalendarServiceTest
{
    private val calendarRepository = InMemoryBudgetCalendarRepository()
    private val service = GetBudgetCalendarService(calendarRepository)

    @Test
    fun `observes the calendar of the repository, calendar months when nothing is set`() = runTest()
    {
        // WHEN / THEN
        assertThat(service.observe().first()).isEqualTo(BudgetCalendar())

        // AND it follows what the user sets
        calendarRepository.saveDefaultStartDay(BudgetStartDay(25))
        assertThat(service.observe().first()).isEqualTo(BudgetCalendar(defaultStartDay = BudgetStartDay(25)))
    }

    @Test
    fun `without anything set, a day is in the cycle of its calendar month`() = runTest()
    {
        // WHEN / THEN
        assertThat(service.cycleOf(LocalDate.of(2026, 9, 30))).isEqualTo(YearMonth.of(2026, 9))
        assertThat(service.cycleOf(LocalDate.of(2026, 10, 1))).isEqualTo(YearMonth.of(2026, 10))
    }

    @Test
    fun `a day is in the cycle that is open on it, declared starts included`() = runTest()
    {
        // GIVEN pay came on September 28th: October's cycle opened that day
        calendarRepository.saveCycleStart(LocalDate.of(2026, 9, 28))

        // WHEN / THEN
        assertThat(service.cycleOf(LocalDate.of(2026, 9, 27))).isEqualTo(YearMonth.of(2026, 9))
        assertThat(service.cycleOf(LocalDate.of(2026, 9, 28))).isEqualTo(YearMonth.of(2026, 10))
    }
}
