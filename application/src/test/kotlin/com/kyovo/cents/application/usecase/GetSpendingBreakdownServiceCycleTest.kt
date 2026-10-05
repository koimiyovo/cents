package com.kyovo.cents.application.usecase

import com.kyovo.cents.application.fakes.InMemoryBudgetCalendarRepository
import com.kyovo.cents.application.fakes.InMemoryTransactionRepository
import com.kyovo.cents.application.fakes.aMoney
import com.kyovo.cents.application.fakes.aSubcategoryId
import com.kyovo.cents.application.fakes.aTransaction
import com.kyovo.cents.application.fakes.aTransactionId
import com.kyovo.cents.application.fakes.anInstant
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.SubcategoryId
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

/** The breakdown of a month is the breakdown of its *cycle*: same bounds as a budget's progress. */
@OptIn(ExperimentalCoroutinesApi::class)
class GetSpendingBreakdownServiceCycleTest
{
    private val groceriesId = aSubcategoryId("11111111-1111-1111-1111-111111111111")
    private val october = YearMonth.of(2026, 10)

    private val transactionRepository = InMemoryTransactionRepository()
    private val calendarRepository = InMemoryBudgetCalendarRepository()

    private fun aService(zone: ZoneId = ZoneId.of("UTC")) =
        GetSpendingBreakdownService(transactionRepository, calendarRepository, zone)

    private suspend fun expense(suffix: Int, cents: Long, date: String, subcategoryId: SubcategoryId? = groceriesId)
    {
        transactionRepository.save(
            aTransaction(
                id = aTransactionId("33333333-3333-3333-3333-33333333333$suffix"),
                amount = aMoney(cents),
                date = anInstant(date),
                category = TransactionCategory.EXPENSE,
                subcategoryId = subcategoryId,
            )
        )
    }

    @Test
    fun `counts the expenses from the cycle's start to the next cycle's start, uncategorised ones included`() = runTest()
    {
        // GIVEN pay came on September 25th (opening October) and on October 28th (opening November)
        calendarRepository.saveCycleStart(LocalDate.of(2026, 9, 25))
        calendarRepository.saveCycleStart(LocalDate.of(2026, 10, 28))
        expense(1, 1_000, "2026-09-24T23:59:59Z") // September's cycle
        expense(2, 2_000, "2026-09-25T00:00:00Z") // the first instant of October's
        expense(3, 4_000, "2026-10-27T23:59:59Z", subcategoryId = null) // the last instant of October's
        expense(4, 8_000, "2026-10-28T00:00:00Z") // November's

        // WHEN
        val breakdown = aService().observe(october).first()

        // THEN
        assertThat(breakdown).isEqualTo(mapOf<SubcategoryId?, Money>(groceriesId to aMoney(2_000), null to aMoney(4_000)))
    }

    @Test
    fun `a cycle starts at midnight in the given zone`() = runTest()
    {
        // GIVEN Paris is UTC+2 on September 25th
        calendarRepository.saveCycleStart(LocalDate.of(2026, 9, 25))
        expense(1, 1_000, "2026-09-24T21:59:59Z") // 23:59:59 on Sep 24th in Paris: September's
        expense(2, 2_000, "2026-09-24T22:00:00Z") // 00:00:00 on Sep 25th in Paris: October's

        // WHEN / THEN
        assertThat(aService(ZoneId.of("Europe/Paris")).observe(october).first())
            .isEqualTo(mapOf<SubcategoryId?, Money>(groceriesId to aMoney(2_000)))
    }

    @Test
    fun `follows the calendar when a start is declared`() = runTest()
    {
        // GIVEN October is the whole calendar month: both expenses count
        expense(1, 1_000, "2026-10-10T12:00:00Z")
        expense(2, 2_000, "2026-10-29T12:00:00Z")
        val seen = mutableListOf<Map<SubcategoryId?, Money>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler))
        {
            aService().observe(october).toList(seen)
        }

        // WHEN October 28th is declared as November's start: October now ends on the 27th
        calendarRepository.saveCycleStart(LocalDate.of(2026, 10, 28))

        // THEN
        assertThat(seen.map { it[groceriesId] }).containsExactly(aMoney(3_000), aMoney(1_000))
    }
}
