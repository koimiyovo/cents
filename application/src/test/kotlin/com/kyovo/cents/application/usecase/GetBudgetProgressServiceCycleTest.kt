package com.kyovo.cents.application.usecase

import com.kyovo.cents.application.fakes.InMemoryBudgetCalendarRepository
import com.kyovo.cents.application.fakes.InMemoryBudgetRepository
import com.kyovo.cents.application.fakes.InMemoryTransactionRepository
import com.kyovo.cents.application.fakes.aBudget
import com.kyovo.cents.application.fakes.aMoney
import com.kyovo.cents.application.fakes.aSubcategoryId
import com.kyovo.cents.application.fakes.aTransaction
import com.kyovo.cents.application.fakes.aTransactionId
import com.kyovo.cents.application.fakes.anInstant
import com.kyovo.cents.domain.model.BudgetStartDay
import com.kyovo.cents.domain.model.BudgetProgress
import com.kyovo.cents.domain.model.TransactionCategory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * A budget month is a cycle of the [com.kyovo.cents.domain.model.BudgetCalendar]: "September" runs from the
 * start of September's cycle to the start of October's, wherever the user declared those. The budgets keep
 * their `(subcategory, month)` key and their inheritance (a month without a budget uses the most recent
 * earlier one); only *which expenses fall in a month* changes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GetBudgetProgressServiceCycleTest
{
    private val groceriesId = aSubcategoryId("11111111-1111-1111-1111-111111111111")
    private val september = YearMonth.of(2026, 9)
    private val october = YearMonth.of(2026, 10)
    private val november = YearMonth.of(2026, 11)
    private val utc = ZoneId.of("UTC")

    private val budgetRepository = InMemoryBudgetRepository()
    private val transactionRepository = InMemoryTransactionRepository()
    private val calendarRepository = InMemoryBudgetCalendarRepository()

    private fun aServiceIn(zone: ZoneId) =
        GetBudgetProgressService(budgetRepository, transactionRepository, calendarRepository, zone)

    private suspend fun expense(suffix: Int, cents: Long, date: String)
    {
        transactionRepository.save(
            aTransaction(
                id = aTransactionId("33333333-3333-3333-3333-33333333333$suffix"),
                amount = aMoney(cents),
                date = anInstant(date),
                category = TransactionCategory.EXPENSE,
                subcategoryId = groceriesId,
            )
        )
    }

    private suspend fun spentIn(month: YearMonth, zone: ZoneId = utc) =
        aServiceIn(zone).observe(groceriesId, month).first()?.spent

    @Test
    fun `a month counts the expenses from its declared start up to the next declared start`() = runTest()
    {
        // GIVEN pay came on September 25th (opening October) and on October 28th (opening November)
        calendarRepository.saveCycleStart(LocalDate.of(2026, 9, 25))
        calendarRepository.saveCycleStart(LocalDate.of(2026, 10, 28))
        budgetRepository.save(aBudget(subcategoryId = groceriesId, month = october))
        expense(1, 1_000, "2026-09-24T23:59:59Z") // the day before: September's cycle
        expense(2, 2_000, "2026-09-25T00:00:00Z") // the first instant of October's
        expense(3, 4_000, "2026-10-10T12:00:00Z") // the 10th of October is still October's cycle
        expense(4, 8_000, "2026-10-27T23:59:59Z") // the last instant of October's
        expense(5, 16_000, "2026-10-28T00:00:00Z") // November's

        // WHEN / THEN
        assertThat(spentIn(october)).isEqualTo(aMoney(14_000))
    }

    @Test
    fun `the next cycle starts where the previous one ended`() = runTest()
    {
        // GIVEN November opens on October 28th, December has no declared start: it opens on December 1st
        calendarRepository.saveCycleStart(LocalDate.of(2026, 9, 25))
        calendarRepository.saveCycleStart(LocalDate.of(2026, 10, 28))
        budgetRepository.save(aBudget(subcategoryId = groceriesId, month = november))
        expense(1, 8_000, "2026-10-27T23:59:59Z") // October's
        expense(2, 16_000, "2026-10-28T00:00:00Z") // November's first instant
        expense(3, 32_000, "2026-11-30T23:59:59Z") // November's last instant
        expense(4, 64_000, "2026-12-01T00:00:00Z") // December's

        // WHEN / THEN
        assertThat(spentIn(november)).isEqualTo(aMoney(48_000))
    }

    @Test
    fun `the default start day applies to cycles with no declared start`() = runTest()
    {
        // GIVEN September's cycle then runs from August 25th to September 24th
        calendarRepository.saveDefaultStartDay(BudgetStartDay(25))
        budgetRepository.save(aBudget(subcategoryId = groceriesId, month = september))
        expense(1, 1_000, "2026-08-24T12:00:00Z")
        expense(2, 2_000, "2026-08-25T12:00:00Z")
        expense(3, 4_000, "2026-09-24T12:00:00Z")
        expense(4, 8_000, "2026-09-25T12:00:00Z")

        // WHEN / THEN
        assertThat(spentIn(september)).isEqualTo(aMoney(6_000))
    }

    // A cycle starts at midnight where the user lives, like a calendar month used to.
    @Test
    fun `a cycle starts and ends at midnight in the given zone`() = runTest()
    {
        // GIVEN Paris is UTC+2 on September 25th but UTC+1 on October 28th (the clocks went back on the 25th)
        calendarRepository.saveCycleStart(LocalDate.of(2026, 9, 25))
        calendarRepository.saveCycleStart(LocalDate.of(2026, 10, 28))
        budgetRepository.save(aBudget(subcategoryId = groceriesId, month = october))
        expense(1, 1_000, "2026-09-24T21:59:59Z") // 23:59:59 on Sep 24th in Paris: September's
        expense(2, 2_000, "2026-09-24T22:00:00Z") // 00:00:00 on Sep 25th in Paris: October's
        expense(3, 4_000, "2026-10-27T22:59:59Z") // 23:59:59 on Oct 27th in Paris: October's
        expense(4, 8_000, "2026-10-27T23:00:00Z") // 00:00:00 on Oct 28th in Paris: November's

        // WHEN / THEN
        assertThat(spentIn(october, ZoneId.of("Europe/Paris"))).isEqualTo(aMoney(6_000))
    }

    @Test
    fun `an inherited limit is judged against the cycle of the month asked`() = runTest()
    {
        // GIVEN October's budget carries over to November, whose cycle is 28 Oct → 30 Nov
        calendarRepository.saveCycleStart(LocalDate.of(2026, 9, 25))
        calendarRepository.saveCycleStart(LocalDate.of(2026, 10, 28))
        budgetRepository.save(aBudget(subcategoryId = groceriesId, month = october, limit = aMoney(30_000)))
        expense(1, 9_000, "2026-10-20T12:00:00Z") // October's cycle
        expense(2, 5_000, "2026-11-05T12:00:00Z") // November's

        // WHEN
        val progress = aServiceIn(utc).observe(groceriesId, november).first()

        // THEN
        assertThat(progress).isEqualTo(BudgetProgress(aMoney(30_000), aMoney(5_000)))
    }

    @Test
    fun `observeAll follows the calendar too`() = runTest()
    {
        // GIVEN October's cycle opens on September 25th
        calendarRepository.saveCycleStart(LocalDate.of(2026, 9, 25))
        budgetRepository.save(aBudget(subcategoryId = groceriesId, month = october))
        expense(1, 1_000, "2026-09-10T12:00:00Z") // before the cycle
        expense(2, 2_000, "2026-09-26T12:00:00Z")

        // WHEN
        val all = aServiceIn(utc).observeAll(october).first()

        // THEN
        assertThat(all[groceriesId]?.spent).isEqualTo(aMoney(2_000))
    }

    // Declaring a start is what the user does when pay comes in: the progress they are looking at must move
    // with it, without reopening the screen.
    @Test
    fun `the progress is recomputed when a cycle start is declared or the default day changes`() = runTest()
    {
        // GIVEN September is the whole calendar month: both expenses count
        budgetRepository.save(aBudget(subcategoryId = groceriesId, month = september))
        expense(1, 1_000, "2026-09-10T12:00:00Z")
        expense(2, 2_000, "2026-09-26T12:00:00Z")
        val seen = mutableListOf<BudgetProgress?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler))
        {
            aServiceIn(utc).observe(groceriesId, september).toList(seen)
        }

        // WHEN
        calendarRepository.saveCycleStart(LocalDate.of(2026, 9, 25)) // pay came: October opens, September ends on the 24th
        calendarRepository.saveDefaultStartDay(BudgetStartDay(28)) // changes nothing for September: its end is declared
        calendarRepository.deleteCycleStart(october) // October falls back to the 28th, so September runs to the 27th

        // THEN
        assertThat(seen.map { it?.spent }).containsExactly(aMoney(3_000), aMoney(1_000), aMoney(3_000))
    }

    @Test
    fun `does not redraw for a calendar change that leaves the progress as it was`() = runTest()
    {
        // GIVEN
        budgetRepository.save(aBudget(subcategoryId = groceriesId, month = september))
        expense(1, 2_000, "2026-09-26T12:00:00Z")
        val seen = mutableListOf<BudgetProgress?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler))
        {
            aServiceIn(utc).observe(groceriesId, september).toList(seen)
        }

        // WHEN a start is declared that moves September's end without moving any expense in or out
        calendarRepository.saveCycleStart(LocalDate.of(2026, 10, 3))

        // THEN
        assertThat(seen).hasSize(1)
    }
}
