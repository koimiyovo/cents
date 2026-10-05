package com.kyovo.cents.application.usecase

import com.kyovo.cents.application.fakes.InMemoryBudgetCalendarRepository
import com.kyovo.cents.domain.model.BudgetStartDay
import com.kyovo.cents.domain.model.BudgetCalendar
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * The three ways a user shapes their budget cycles: the default start day, a start declared for one cycle
 * (when pay comes in), and taking that declaration back.
 */
class BudgetCalendarServicesTest
{
    private val calendarRepository = InMemoryBudgetCalendarRepository()
    private val setDefaultStartDay = SetDefaultBudgetStartDayService(calendarRepository)
    private val setCycleStart = SetBudgetCycleStartService(calendarRepository)
    private val clearCycleStart = ClearBudgetCycleStartService(calendarRepository)

    @Test
    fun `sets the default start day`() = runTest()
    {
        // WHEN
        setDefaultStartDay.set(BudgetStartDay(25))

        // THEN
        assertThat(calendarRepository.saved).isEqualTo(BudgetCalendar(defaultStartDay = BudgetStartDay(25)))
    }

    // Pay received on September 25th funds October: the date alone says which cycle it opens.
    @Test
    fun `declares the start of the cycle the date opens`() = runTest()
    {
        // WHEN
        setCycleStart.set(LocalDate.of(2026, 9, 25))

        // THEN
        assertThat(calendarRepository.saved.startOf(YearMonth.of(2026, 10))).isEqualTo(LocalDate.of(2026, 9, 25))
    }

    @Test
    fun `declaring again for the same cycle replaces the earlier date`() = runTest()
    {
        // GIVEN
        setCycleStart.set(LocalDate.of(2026, 9, 25))

        // WHEN the user corrects the date
        setCycleStart.set(LocalDate.of(2026, 9, 27))

        // THEN
        assertThat(calendarRepository.saved.declaredStarts).containsExactly(LocalDate.of(2026, 9, 27))
    }

    @Test
    fun `declaring a start leaves the other cycles and the default day alone`() = runTest()
    {
        // GIVEN
        setDefaultStartDay.set(BudgetStartDay(25))
        setCycleStart.set(LocalDate.of(2026, 10, 28))

        // WHEN
        setCycleStart.set(LocalDate.of(2026, 9, 26))

        // THEN
        assertThat(calendarRepository.saved)
            .isEqualTo(BudgetCalendar(BudgetStartDay(25), setOf(LocalDate.of(2026, 9, 26), LocalDate.of(2026, 10, 28))))
    }

    @Test
    fun `clearing a start brings its cycle back to the default day`() = runTest()
    {
        // GIVEN
        setDefaultStartDay.set(BudgetStartDay(25))
        setCycleStart.set(LocalDate.of(2026, 9, 28)) // opens October
        setCycleStart.set(LocalDate.of(2026, 10, 28)) // opens November

        // WHEN
        clearCycleStart.clear(YearMonth.of(2026, 10))

        // THEN October is back to September 25th; November's declared start is untouched
        assertThat(calendarRepository.saved.startOf(YearMonth.of(2026, 10))).isEqualTo(LocalDate.of(2026, 9, 25))
        assertThat(calendarRepository.saved.declaredStarts).containsExactly(LocalDate.of(2026, 10, 28))
    }

    @Test
    fun `clearing a month with no declared start is a silent no-op`() = runTest()
    {
        // GIVEN
        setCycleStart.set(LocalDate.of(2026, 10, 28))

        // WHEN
        clearCycleStart.clear(YearMonth.of(2026, 10))

        // THEN
        assertThat(calendarRepository.saved.declaredStarts).containsExactly(LocalDate.of(2026, 10, 28))
    }
}
