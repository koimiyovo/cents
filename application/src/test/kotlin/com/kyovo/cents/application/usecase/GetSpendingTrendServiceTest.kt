package com.kyovo.cents.application.usecase

import com.kyovo.cents.application.fakes.InMemoryTransactionRepository
import com.kyovo.cents.application.fakes.aMoney
import com.kyovo.cents.application.fakes.anAccountId
import com.kyovo.cents.application.fakes.aSubcategoryId
import com.kyovo.cents.application.fakes.aTransaction
import com.kyovo.cents.application.fakes.aTransactionId
import com.kyovo.cents.application.fakes.anInstant
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.MonthlySpending
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.model.TransactionCategory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.YearMonth
import java.time.ZoneId

/**
 * How much was spent in each of the last few months: one point per month, always, so a trend chart never
 * has a gap — a month with nothing spent is a zero, not an absent entry.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GetSpendingTrendServiceTest
{
    private val groceriesId = aSubcategoryId("11111111-1111-1111-1111-111111111111")
    private val september = YearMonth.of(2026, 9)

    private val transactionRepository = InMemoryTransactionRepository()

    private fun aServiceIn(zone: ZoneId) = GetSpendingTrendService(transactionRepository, zone)

    private fun anExpense(suffix: Int, cents: Long, date: String, accountId: AccountId = anAccountId()): Transaction =
        aTransaction(
            id = aTransactionId("33333333-3333-3333-3333-33333333333$suffix"),
            accountId = accountId,
            amount = aMoney(cents),
            date = anInstant(date),
            category = TransactionCategory.EXPENSE,
            subcategoryId = groceriesId,
        )

    @Test
    fun `one entry per month in the window, oldest first, ending at the month given`() = runTest()
    {
        // WHEN a 3-month window ending at September
        val trend = aServiceIn(ZoneId.of("UTC")).observe(september, months = 3).first()

        // THEN
        assertThat(trend.map { it.month }).containsExactly(
            YearMonth.of(2026, 7),
            YearMonth.of(2026, 8),
            september,
        )
    }

    @Test
    fun `an account given counts only that account's expenses`() = runTest()
    {
        // GIVEN
        val checking = anAccountId("aaaaaaaa-0000-0000-0000-000000000001")
        val cash = anAccountId("aaaaaaaa-0000-0000-0000-000000000002")
        transactionRepository.save(anExpense(1, 4_500, "2026-09-03T10:00:00Z", accountId = checking))
        transactionRepository.save(anExpense(2, 9_000, "2026-09-05T10:00:00Z", accountId = cash))

        // WHEN / THEN
        assertThat(aServiceIn(ZoneId.of("UTC")).observe(september, months = 1, accountId = checking).first())
            .isEqualTo(listOf(MonthlySpending(september, aMoney(4_500))))
        assertThat(aServiceIn(ZoneId.of("UTC")).observe(september, months = 1).first())
            .isEqualTo(listOf(MonthlySpending(september, aMoney(13_500))))
    }

    @Test
    fun `a month with nothing spent is a zero, not an absence`() = runTest()
    {
        // GIVEN nothing at all recorded

        // WHEN
        val trend = aServiceIn(ZoneId.of("UTC")).observe(september, months = 3).first()

        // THEN
        assertThat(trend.map { it.total }).containsExactly(aMoney(0), aMoney(0), aMoney(0))
    }

    @Test
    fun `sums only the expenses of each month, ignoring incomes, transfers and months outside the window`() = runTest()
    {
        // GIVEN
        transactionRepository.save(anExpense(1, 4_500, "2026-09-03T10:00:00Z"))
        transactionRepository.save(anExpense(2, 2_000, "2026-08-05T10:00:00Z"))
        transactionRepository.save(
            aTransaction(
                id = aTransactionId("99999999-9999-9999-9999-999999999999"),
                amount = aMoney(50_000),
                date = anInstant("2026-09-10T10:00:00Z"),
                category = TransactionCategory.INCOME,
                subcategoryId = null,
            )
        )
        transactionRepository.save(anExpense(3, 1_000, "2026-06-10T10:00:00Z")) // before the 3-month window

        // WHEN
        val trend = aServiceIn(ZoneId.of("UTC")).observe(september, months = 3).first()

        // THEN
        assertThat(trend).isEqualTo(
            listOf(
                MonthlySpending(YearMonth.of(2026, 7), aMoney(0)),
                MonthlySpending(YearMonth.of(2026, 8), aMoney(2_000)),
                MonthlySpending(september, aMoney(4_500)),
            )
        )
    }

    // A month starts at midnight where the user lives, not in UTC — the same rule as elsewhere.
    @Test
    fun `the month boundary follows the given zone`() = runTest()
    {
        // GIVEN Paris is UTC+2 in summer
        transactionRepository.save(anExpense(1, 1_000, "2026-08-31T21:59:59Z")) // 23:59:59 on Aug 31st in Paris: August
        transactionRepository.save(anExpense(2, 2_000, "2026-08-31T22:00:00Z")) // 00:00:00 on Sep 1st in Paris: September

        // WHEN
        val trend = aServiceIn(ZoneId.of("Europe/Paris")).observe(september, months = 2).first()

        // THEN
        assertThat(trend).isEqualTo(
            listOf(
                MonthlySpending(YearMonth.of(2026, 8), aMoney(1_000)),
                MonthlySpending(september, aMoney(2_000)),
            )
        )
    }

    @Test
    fun `defaults to a six-month window`() = runTest()
    {
        // WHEN
        val trend = aServiceIn(ZoneId.of("UTC")).observe(september).first()

        // THEN
        assertThat(trend.map { it.month }).containsExactly(
            YearMonth.of(2026, 4),
            YearMonth.of(2026, 5),
            YearMonth.of(2026, 6),
            YearMonth.of(2026, 7),
            YearMonth.of(2026, 8),
            september,
        )
    }

    // A page showing the trend must redraw as expenses are recorded, edited or deleted.
    @Test
    fun `follows the transactions as they are recorded`() = runTest()
    {
        // GIVEN
        val emissions = mutableListOf<List<MonthlySpending>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler))
        {
            aServiceIn(ZoneId.of("UTC")).observe(september, months = 2).collect { emissions += it }
        }

        // WHEN
        transactionRepository.save(anExpense(1, 4_500, "2026-09-03T10:00:00Z"))

        // THEN
        assertThat(emissions).containsExactly(
            listOf(MonthlySpending(YearMonth.of(2026, 8), aMoney(0)), MonthlySpending(september, aMoney(0))),
            listOf(MonthlySpending(YearMonth.of(2026, 8), aMoney(0)), MonthlySpending(september, aMoney(4_500))),
        )
    }
}
