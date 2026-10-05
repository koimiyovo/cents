package com.kyovo.cents.application.usecase

import com.kyovo.cents.application.fakes.InMemoryBudgetCalendarRepository
import com.kyovo.cents.application.fakes.InMemoryTransactionRepository
import com.kyovo.cents.application.fakes.aMoney
import com.kyovo.cents.application.fakes.anAccountId
import com.kyovo.cents.application.fakes.aSubcategoryId
import com.kyovo.cents.application.fakes.aTransaction
import com.kyovo.cents.application.fakes.aTransactionId
import com.kyovo.cents.application.fakes.anInstant
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.SubcategoryId
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
 * Where the money went this month, by subcategory: the same "expenses of the month" rule as a budget's
 * progress ([GetBudgetProgressService]), but for every subcategory at once and with no budget involved.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GetSpendingBreakdownServiceTest
{
    private val groceriesId = aSubcategoryId("11111111-1111-1111-1111-111111111111")
    private val fuelId = aSubcategoryId("22222222-2222-2222-2222-222222222222")
    private val september = YearMonth.of(2026, 9)

    private val transactionRepository = InMemoryTransactionRepository()

    private fun aServiceIn(zone: ZoneId) = GetSpendingBreakdownService(transactionRepository, InMemoryBudgetCalendarRepository(), zone)

    private fun anExpense(
        suffix: Int,
        cents: Long,
        date: String,
        subcategoryId: SubcategoryId? = groceriesId,
        accountId: AccountId = anAccountId(),
    ): Transaction =
        aTransaction(
            id = aTransactionId("33333333-3333-3333-3333-33333333333$suffix"),
            accountId = accountId,
            amount = aMoney(cents),
            date = anInstant(date),
            category = TransactionCategory.EXPENSE,
            subcategoryId = subcategoryId,
        )

    @Test
    fun `sums the expenses of the month by subcategory, uncategorised expenses kept apart`() = runTest()
    {
        // GIVEN
        transactionRepository.save(anExpense(1, 4_500, "2026-09-03T10:00:00Z"))
        transactionRepository.save(anExpense(2, 2_000, "2026-09-05T10:00:00Z"))
        transactionRepository.save(anExpense(3, 9_000, "2026-09-10T10:00:00Z", subcategoryId = fuelId))
        transactionRepository.save(anExpense(4, 7_000, "2026-09-12T10:00:00Z", subcategoryId = null))

        // WHEN
        val breakdown = aServiceIn(ZoneId.of("UTC")).observe(september).first()

        // THEN
        assertThat(breakdown).isEqualTo(
            mapOf(
                groceriesId to aMoney(6_500),
                fuelId to aMoney(9_000),
                null to aMoney(7_000),
            )
        )
    }

    @Test
    fun `an account given counts only that account's expenses`() = runTest()
    {
        // GIVEN
        val checking = anAccountId("aaaaaaaa-0000-0000-0000-000000000001")
        val cash = anAccountId("aaaaaaaa-0000-0000-0000-000000000002")
        transactionRepository.save(anExpense(1, 4_500, "2026-09-03T10:00:00Z", accountId = checking))
        transactionRepository.save(anExpense(2, 9_000, "2026-09-05T10:00:00Z", subcategoryId = fuelId, accountId = cash))

        // WHEN / THEN
        assertThat(aServiceIn(ZoneId.of("UTC")).observe(september, accountId = checking).first())
            .isEqualTo(mapOf(groceriesId to aMoney(4_500)))
        assertThat(aServiceIn(ZoneId.of("UTC")).observe(september).first())
            .isEqualTo(mapOf(groceriesId to aMoney(4_500), fuelId to aMoney(9_000)))
    }

    @Test
    fun `ignores incomes, transfers and other months`() = runTest()
    {
        // GIVEN
        transactionRepository.save(anExpense(1, 4_500, "2026-09-10T10:00:00Z"))
        transactionRepository.save(
            aTransaction(
                id = aTransactionId("99999999-9999-9999-9999-999999999999"),
                amount = aMoney(50_000),
                date = anInstant("2026-09-10T10:00:00Z"),
                category = TransactionCategory.INCOME,
                subcategoryId = null,
            )
        )
        transactionRepository.save(anExpense(2, 6_000, "2026-08-10T10:00:00Z"))
        transactionRepository.save(anExpense(3, 5_000, "2026-10-10T10:00:00Z"))

        // WHEN
        val breakdown = aServiceIn(ZoneId.of("UTC")).observe(september).first()

        // THEN
        assertThat(breakdown).isEqualTo(mapOf(groceriesId to aMoney(4_500)))
    }

    // A month starts at midnight where the user lives, not in UTC — the same rule as a budget's progress.
    @Test
    fun `the month starts and ends at midnight in the given zone`() = runTest()
    {
        // GIVEN Paris is UTC+2 in summer
        transactionRepository.save(anExpense(1, 1_000, "2026-08-31T21:59:59Z")) // 23:59:59 on Aug 31st in Paris: August
        transactionRepository.save(anExpense(2, 2_000, "2026-08-31T22:00:00Z")) // 00:00:00 on Sep 1st in Paris: September
        transactionRepository.save(anExpense(3, 4_000, "2026-09-30T21:59:59Z")) // 23:59:59 on Sep 30th in Paris: September
        transactionRepository.save(anExpense(4, 8_000, "2026-09-30T22:00:00Z")) // 00:00:00 on Oct 1st in Paris: October

        // WHEN
        val breakdown = aServiceIn(ZoneId.of("Europe/Paris")).observe(september).first()

        // THEN
        assertThat(breakdown).isEqualTo(mapOf(groceriesId to aMoney(6_000)))
    }

    @Test
    fun `is empty when nothing was spent that month`() = runTest()
    {
        assertThat(aServiceIn(ZoneId.of("UTC")).observe(september).first()).isEmpty()
    }

    // A page showing the breakdown must redraw as expenses are recorded, edited or deleted.
    @Test
    fun `follows the transactions as they are recorded`() = runTest()
    {
        // GIVEN
        val emissions = mutableListOf<Map<SubcategoryId?, Money>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler))
        {
            aServiceIn(ZoneId.of("UTC")).observe(september).collect { emissions += it }
        }

        // WHEN
        transactionRepository.save(anExpense(1, 4_500, "2026-09-03T10:00:00Z"))
        transactionRepository.save(anExpense(2, 2_000, "2026-09-05T10:00:00Z"))

        // THEN
        assertThat(emissions).containsExactly(
            emptyMap(),
            mapOf(groceriesId to aMoney(4_500)),
            mapOf(groceriesId to aMoney(6_500)),
        )
    }
}
