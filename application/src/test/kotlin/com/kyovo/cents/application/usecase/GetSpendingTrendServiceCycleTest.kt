package com.kyovo.cents.application.usecase

import com.kyovo.cents.application.fakes.InMemoryBudgetCalendarRepository
import com.kyovo.cents.application.fakes.InMemoryTransactionRepository
import com.kyovo.cents.application.fakes.aMoney
import com.kyovo.cents.application.fakes.aSubcategoryId
import com.kyovo.cents.application.fakes.aTransaction
import com.kyovo.cents.application.fakes.aTransactionId
import com.kyovo.cents.application.fakes.anInstant
import com.kyovo.cents.domain.model.MonthlySpending
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

/** Each point of the trend is one *cycle*: an expense is counted in the cycle open on its day. */
@OptIn(ExperimentalCoroutinesApi::class)
class GetSpendingTrendServiceCycleTest
{
    private val groceriesId = aSubcategoryId("11111111-1111-1111-1111-111111111111")
    private val september = YearMonth.of(2026, 9)
    private val october = YearMonth.of(2026, 10)
    private val november = YearMonth.of(2026, 11)

    private val transactionRepository = InMemoryTransactionRepository()
    private val calendarRepository = InMemoryBudgetCalendarRepository()

    private fun aService(zone: ZoneId = ZoneId.of("UTC")) =
        GetSpendingTrendService(transactionRepository, calendarRepository, zone)

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

    @Test
    fun `each expense counts in the cycle open on its day`() = runTest()
    {
        // GIVEN pay came on September 25th (opening October) and on October 28th (opening November)
        calendarRepository.saveCycleStart(LocalDate.of(2026, 9, 25))
        calendarRepository.saveCycleStart(LocalDate.of(2026, 10, 28))
        expense(1, 1_000, "2026-09-10T12:00:00Z") // September's cycle
        expense(2, 2_000, "2026-09-26T12:00:00Z") // October's
        expense(3, 4_000, "2026-10-27T23:59:59Z") // October's last instant
        expense(4, 8_000, "2026-10-28T00:00:00Z") // November's first instant
        expense(5, 16_000, "2026-11-30T23:59:59Z") // November's last instant (December opens on the 1st by default)
        expense(6, 32_000, "2026-12-01T00:00:00Z") // December's: outside the window

        // WHEN a 3-month window ending at November
        val trend = aService().observe(november, months = 3).first()

        // THEN
        assertThat(trend).containsExactly(
            MonthlySpending(september, aMoney(1_000)),
            MonthlySpending(october, aMoney(6_000)),
            MonthlySpending(november, aMoney(24_000)),
        )
    }

    @Test
    fun `an expense before the first cycle of the window is left out`() = runTest()
    {
        // GIVEN October opens on September 25th
        calendarRepository.saveCycleStart(LocalDate.of(2026, 9, 25))
        expense(1, 1_000, "2026-09-24T12:00:00Z") // September's: the window is October alone
        expense(2, 2_000, "2026-09-25T12:00:00Z")

        // WHEN / THEN
        assertThat(aService().observe(october, months = 1).first())
            .containsExactly(MonthlySpending(october, aMoney(2_000)))
    }

    @Test
    fun `a cycle starts at midnight in the given zone`() = runTest()
    {
        // GIVEN Paris is UTC+2 on September 25th
        calendarRepository.saveCycleStart(LocalDate.of(2026, 9, 25))
        expense(1, 1_000, "2026-09-24T21:59:59Z") // 23:59:59 on Sep 24th in Paris: September's
        expense(2, 2_000, "2026-09-24T22:00:00Z") // 00:00:00 on Sep 25th in Paris: October's

        // WHEN / THEN
        assertThat(aService(ZoneId.of("Europe/Paris")).observe(october, months = 2).first())
            .containsExactly(MonthlySpending(september, aMoney(1_000)), MonthlySpending(october, aMoney(2_000)))
    }

    @Test
    fun `follows the calendar when a start is declared`() = runTest()
    {
        // GIVEN October is the whole calendar month: the expense of the 25th counts in it
        expense(1, 2_000, "2026-10-25T12:00:00Z")
        val seen = mutableListOf<List<MonthlySpending>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler))
        {
            aService().observe(october, months = 1).toList(seen)
        }

        // WHEN October opens early, on September 25th: the expense is still in it, nothing moves; then November
        // opens on October 20th: the expense of the 25th now belongs to November
        calendarRepository.saveCycleStart(LocalDate.of(2026, 9, 25))
        calendarRepository.saveCycleStart(LocalDate.of(2026, 10, 20))

        // THEN
        assertThat(seen.map { it.single().total }).containsExactly(aMoney(2_000), aMoney(0))
    }
}
