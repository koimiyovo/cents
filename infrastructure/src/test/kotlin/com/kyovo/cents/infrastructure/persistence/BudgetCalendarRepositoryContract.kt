package com.kyovo.cents.infrastructure.persistence

import com.kyovo.cents.domain.model.BudgetStartDay
import com.kyovo.cents.domain.model.BudgetCalendar
import com.kyovo.cents.domain.port.output.BudgetCalendarRepository
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * What every [BudgetCalendarRepository] must do, whatever it stores the calendar in (today Room): the
 * port's specification, written as tests. The calendar is read by observing it, as one value: the default
 * start day and the declared starts together. A declared start is filed under the cycle its date opens
 * (see [BudgetCalendar.monthStartingOn]), so saving another date for the same cycle replaces it.
 */
abstract class BudgetCalendarRepositoryContract
{
    /** Fresh, empty storage. Called before each test. */
    protected abstract fun createRepository(): BudgetCalendarRepository

    protected lateinit var repository: BudgetCalendarRepository

    @BeforeEach
    fun createTheRepository()
    {
        repository = createRepository()
    }

    private suspend fun stored() = repository.observe().first()

    @Test
    fun `an empty repository gives the default calendar, calendar months`() = realTime()
    {
        assertThat(stored()).isEqualTo(BudgetCalendar())
    }

    @Test
    fun `gives back the default start day that was saved`() = realTime()
    {
        // WHEN
        repository.saveDefaultStartDay(BudgetStartDay(25))

        // THEN
        assertThat(stored()).isEqualTo(BudgetCalendar(defaultStartDay = BudgetStartDay(25)))
    }

    @Test
    fun `saving the default start day again replaces it`() = realTime()
    {
        // GIVEN
        repository.saveDefaultStartDay(BudgetStartDay(25))

        // WHEN
        repository.saveDefaultStartDay(BudgetStartDay(3))

        // THEN
        assertThat(stored().defaultStartDay).isEqualTo(BudgetStartDay(3))
    }

    @Test
    fun `gives back a cycle start that was saved, to the day`() = realTime()
    {
        // WHEN
        repository.saveCycleStart(LocalDate.of(2026, 9, 28))

        // THEN
        assertThat(stored()).isEqualTo(BudgetCalendar(declaredStarts = setOf(LocalDate.of(2026, 9, 28))))
    }

    @Test
    fun `keeps the starts of different cycles side by side`() = realTime()
    {
        // WHEN September 28th opens October, October 27th opens November
        repository.saveCycleStart(LocalDate.of(2026, 9, 28))
        repository.saveCycleStart(LocalDate.of(2026, 10, 27))

        // THEN
        assertThat(stored().declaredStarts).containsExactlyInAnyOrder(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 27))
    }

    // The same cycle is opened by one date only: that is what the key is.
    @Test
    fun `saving another date for the same cycle replaces the first`() = realTime()
    {
        // GIVEN September 25th opens October
        repository.saveCycleStart(LocalDate.of(2026, 9, 25))

        // WHEN the user corrects it to September 27th, which opens October too
        repository.saveCycleStart(LocalDate.of(2026, 9, 27))

        // THEN
        assertThat(stored().declaredStarts).containsExactly(LocalDate.of(2026, 9, 27))
    }

    @Test
    fun `a date at the end of the year opens the cycle of the next year`() = realTime()
    {
        // GIVEN December 28th opens January
        repository.saveCycleStart(LocalDate.of(2026, 12, 28))
        repository.saveCycleStart(LocalDate.of(2027, 1, 27)) // opens February: not the same cycle

        // WHEN / THEN
        assertThat(stored().startOf(YearMonth.of(2027, 1))).isEqualTo(LocalDate.of(2026, 12, 28))
        assertThat(stored().declaredStarts).hasSize(2)
    }

    @Test
    fun `deleting a cycle start brings that cycle back to the default day`() = realTime()
    {
        // GIVEN
        repository.saveDefaultStartDay(BudgetStartDay(25))
        repository.saveCycleStart(LocalDate.of(2026, 9, 28))
        repository.saveCycleStart(LocalDate.of(2026, 10, 27))

        // WHEN October's cycle loses its declared start
        repository.deleteCycleStart(YearMonth.of(2026, 10))

        // THEN November's stays, the default day too
        assertThat(stored()).isEqualTo(BudgetCalendar(BudgetStartDay(25), setOf(LocalDate.of(2026, 10, 27))))
    }

    @Test
    fun `deleting a cycle that has no declared start changes nothing`() = realTime()
    {
        // GIVEN
        repository.saveCycleStart(LocalDate.of(2026, 10, 27))

        // WHEN
        repository.deleteCycleStart(YearMonth.of(2026, 9))

        // THEN
        assertThat(stored().declaredStarts).containsExactly(LocalDate.of(2026, 10, 27))
    }

    @Test
    fun `saving a cycle start leaves the default day alone, and the other way round`() = realTime()
    {
        // WHEN
        repository.saveDefaultStartDay(BudgetStartDay(25))
        repository.saveCycleStart(LocalDate.of(2026, 9, 28))
        repository.saveDefaultStartDay(BudgetStartDay(10))

        // THEN
        assertThat(stored()).isEqualTo(BudgetCalendar(BudgetStartDay(10), setOf(LocalDate.of(2026, 9, 28))))
    }

    // A screen watching the calendar must see it change, whichever of the two parts was written.
    @Test
    fun `an observer sees the calendar change`() = realTime()
    {
        // GIVEN an observer that has seen the empty calendar
        val latest = repository.observe().stateIn(this, SharingStarted.Eagerly, null).filterNotNull()
        latest.awaitMatching { it == BudgetCalendar() }

        // WHEN / THEN
        repository.saveCycleStart(LocalDate.of(2026, 9, 28))
        latest.awaitMatching { it == BudgetCalendar(declaredStarts = setOf(LocalDate.of(2026, 9, 28))) }

        repository.saveDefaultStartDay(BudgetStartDay(25))
        latest.awaitMatching { it == BudgetCalendar(BudgetStartDay(25), setOf(LocalDate.of(2026, 9, 28))) }

        repository.deleteCycleStart(YearMonth.of(2026, 10))
        latest.awaitMatching { it == BudgetCalendar(defaultStartDay = BudgetStartDay(25)) }

        coroutineContext.cancelChildren()
    }
}
