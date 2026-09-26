package com.kyovo.cents.application.usecase

import com.kyovo.cents.application.fakes.InMemoryBudgetRepository
import com.kyovo.cents.application.fakes.InMemoryTransactionRepository
import com.kyovo.cents.application.fakes.aBudget
import com.kyovo.cents.application.fakes.aMoney
import com.kyovo.cents.application.fakes.aSubcategoryId
import com.kyovo.cents.application.fakes.aTransaction
import com.kyovo.cents.application.fakes.aTransactionId
import com.kyovo.cents.application.fakes.anInstant
import com.kyovo.cents.domain.model.BudgetProgress
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
 * What a budget has "consumed" is not stored: it is worked out from the transactions, so it can never
 * disagree with them (edit or delete an expense and the progress follows). Only expenses of the budget's
 * subcategory, dated within its month, count.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GetBudgetProgressServiceTest
{
    private val groceriesId = aSubcategoryId("11111111-1111-1111-1111-111111111111")
    private val fuelId = aSubcategoryId("22222222-2222-2222-2222-222222222222")
    private val september = YearMonth.of(2026, 9)

    private val budgetRepository = InMemoryBudgetRepository()
    private val transactionRepository = InMemoryTransactionRepository()

    private fun aServiceIn(zone: ZoneId) =
        GetBudgetProgressService(budgetRepository, transactionRepository, zone)

    private fun anExpense(suffix: Int, cents: Long, date: String, subcategoryId: SubcategoryId? = groceriesId): Transaction =
        aTransaction(
            id = aTransactionId("33333333-3333-3333-3333-33333333333$suffix"),
            amount = aMoney(cents),
            date = anInstant(date),
            category = TransactionCategory.EXPENSE,
            subcategoryId = subcategoryId,
        )

    @Test
    fun `spent is the sum of the expenses of the subcategory during the month`() = runTest()
    {
        // GIVEN
        budgetRepository.save(aBudget(subcategoryId = groceriesId, month = september, limit = aMoney(30_000)))
        transactionRepository.save(anExpense(1, 4_500, "2026-09-03T10:00:00Z"))
        transactionRepository.save(anExpense(2, 12_050, "2026-09-15T18:30:00Z"))
        transactionRepository.save(anExpense(3, 800, "2026-09-28T07:00:00Z"))

        // WHEN
        val progress = aServiceIn(ZoneId.of("UTC")).observe(groceriesId, september).first()

        // THEN
        assertThat(progress?.limit).isEqualTo(aMoney(30_000))
        assertThat(progress?.spent).isEqualTo(aMoney(17_350))
    }

    @Test
    fun `ignores other subcategories, uncategorised expenses and other months`() = runTest()
    {
        // GIVEN
        budgetRepository.save(aBudget(subcategoryId = groceriesId, month = september))
        transactionRepository.save(anExpense(1, 4_500, "2026-09-10T10:00:00Z"))
        transactionRepository.save(anExpense(2, 9_000, "2026-09-10T10:00:00Z", subcategoryId = fuelId))
        transactionRepository.save(anExpense(3, 7_000, "2026-09-10T10:00:00Z", subcategoryId = null))
        transactionRepository.save(anExpense(4, 6_000, "2026-08-10T10:00:00Z"))
        transactionRepository.save(anExpense(5, 5_000, "2026-10-10T10:00:00Z"))

        // WHEN
        val progress = aServiceIn(ZoneId.of("UTC")).observe(groceriesId, september).first()

        // THEN
        assertThat(progress?.spent).isEqualTo(aMoney(4_500))
    }

    // A month starts at midnight where the user lives, not in UTC: an expense made at 00:30 in Paris on the
    // 1st of September is September's, though it is still August 31st at 22:30 in UTC.
    @Test
    fun `the month starts and ends at midnight in the given zone`() = runTest()
    {
        // GIVEN Paris is UTC+2 in summer
        budgetRepository.save(aBudget(subcategoryId = groceriesId, month = september))
        transactionRepository.save(anExpense(1, 1_000, "2026-08-31T21:59:59Z")) // 23:59:59 on Aug 31st in Paris: August
        transactionRepository.save(anExpense(2, 2_000, "2026-08-31T22:00:00Z")) // 00:00:00 on Sep 1st in Paris: September
        transactionRepository.save(anExpense(3, 4_000, "2026-09-30T21:59:59Z")) // 23:59:59 on Sep 30th in Paris: September
        transactionRepository.save(anExpense(4, 8_000, "2026-09-30T22:00:00Z")) // 00:00:00 on Oct 1st in Paris: October

        // WHEN
        val progress = aServiceIn(ZoneId.of("Europe/Paris")).observe(groceriesId, september).first()

        // THEN
        assertThat(progress?.spent).isEqualTo(aMoney(6_000))
    }

    // A budget is set once and then carries on: a month with none of its own uses the limit of the most
    // recent earlier month, so nobody has to set the same budget every month. Setting a budget for a month
    // never rewrites earlier months, which is the reason a budget carries its month.
    private suspend fun limitFor(subcategoryId: SubcategoryId, month: YearMonth) =
        aServiceIn(ZoneId.of("UTC")).observe(subcategoryId, month).first()?.limit

    @Test
    fun `has no progress when the subcategory has no budget at all`() = runTest()
    {
        // GIVEN a budget exists, but for another subcategory
        budgetRepository.save(aBudget(subcategoryId = fuelId, month = september))

        // WHEN / THEN
        assertThat(aServiceIn(ZoneId.of("UTC")).observe(groceriesId, september).first()).isNull()
    }

    @Test
    fun `a month without a budget of its own uses the most recent earlier one`() = runTest()
    {
        // GIVEN
        budgetRepository.save(aBudget(subcategoryId = groceriesId, month = YearMonth.of(2026, 6), limit = aMoney(20_000)))
        budgetRepository.save(aBudget(subcategoryId = groceriesId, month = YearMonth.of(2026, 8), limit = aMoney(30_000)))

        // WHEN / THEN August's, not June's, and still in force two months later
        assertThat(limitFor(groceriesId, YearMonth.of(2026, 9))).isEqualTo(aMoney(30_000))
        assertThat(limitFor(groceriesId, YearMonth.of(2026, 10))).isEqualTo(aMoney(30_000))
    }

    @Test
    fun `the budget of the month itself wins over an earlier one`() = runTest()
    {
        // GIVEN
        budgetRepository.save(aBudget(subcategoryId = groceriesId, month = YearMonth.of(2026, 8), limit = aMoney(30_000)))
        budgetRepository.save(aBudget(subcategoryId = groceriesId, month = september, limit = aMoney(45_000)))

        // WHEN / THEN
        assertThat(limitFor(groceriesId, september)).isEqualTo(aMoney(45_000))
        assertThat(limitFor(groceriesId, YearMonth.of(2026, 8))).isEqualTo(aMoney(30_000))
    }

    @Test
    fun `a budget set for a later month does not apply to an earlier one`() = runTest()
    {
        // GIVEN
        budgetRepository.save(aBudget(subcategoryId = groceriesId, month = september, limit = aMoney(30_000)))

        // WHEN / THEN nothing was in force in August, which is not judged against September's limit
        assertThat(aServiceIn(ZoneId.of("UTC")).observe(groceriesId, YearMonth.of(2026, 8)).first()).isNull()
    }

    // A screen showing a budget must redraw when its limit changes, though no expense moved: the budgets
    // are observed, not just read once when a transaction happens to change.
    @Test
    fun `follows the budget as its limit is set, changed and deleted`() = runTest()
    {
        // GIVEN a page showing the progress of a month that has no budget yet
        val emissions = mutableListOf<BudgetProgress?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler))
        {
            aServiceIn(ZoneId.of("UTC")).observe(groceriesId, september).collect { emissions += it }
        }

        // WHEN
        budgetRepository.save(aBudget(subcategoryId = groceriesId, month = september, limit = aMoney(30_000)))
        budgetRepository.save(aBudget(subcategoryId = groceriesId, month = september, limit = aMoney(45_000)))
        budgetRepository.deleteBySubcategoryId(groceriesId)

        // THEN
        assertThat(emissions).containsExactly(
            null,
            BudgetProgress(aMoney(30_000), aMoney(0)),
            BudgetProgress(aMoney(45_000), aMoney(0)),
            null,
        )
    }

    // A page showing one subcategory's progress has nothing to redraw when another one's budget moves.
    @Test
    fun `does not emit again for a budget of another subcategory`() = runTest()
    {
        // GIVEN
        budgetRepository.save(aBudget(subcategoryId = groceriesId, month = september, limit = aMoney(30_000)))
        val emissions = mutableListOf<BudgetProgress?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler))
        {
            aServiceIn(ZoneId.of("UTC")).observe(groceriesId, september).collect { emissions += it }
        }

        // WHEN
        budgetRepository.save(aBudget(subcategoryId = fuelId, month = september, limit = aMoney(10_000)))
        budgetRepository.save(aBudget(subcategoryId = fuelId, month = september, limit = aMoney(12_000)))

        // THEN
        assertThat(emissions).containsExactly(BudgetProgress(aMoney(30_000), aMoney(0)))
    }

    @Test
    fun `an inherited limit is checked against the expenses of the month asked for`() = runTest()
    {
        // GIVEN a limit set in August, expenses in August and in September
        budgetRepository.save(aBudget(subcategoryId = groceriesId, month = YearMonth.of(2026, 8), limit = aMoney(30_000)))
        transactionRepository.save(anExpense(1, 9_000, "2026-08-15T10:00:00Z"))
        transactionRepository.save(anExpense(2, 4_000, "2026-09-15T10:00:00Z"))

        // WHEN
        val progress = aServiceIn(ZoneId.of("UTC")).observe(groceriesId, september).first()

        // THEN
        assertThat(progress?.limit).isEqualTo(aMoney(30_000))
        assertThat(progress?.spent).isEqualTo(aMoney(4_000))
    }
}
