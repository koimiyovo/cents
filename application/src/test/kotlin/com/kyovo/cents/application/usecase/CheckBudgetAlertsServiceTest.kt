package com.kyovo.cents.application.usecase

import com.kyovo.cents.application.fakes.InMemoryBudgetAlertRepository
import com.kyovo.cents.application.fakes.aMoney
import com.kyovo.cents.application.fakes.aSubcategoryId
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.BudgetAlert
import com.kyovo.cents.domain.model.BudgetAlertLevel
import com.kyovo.cents.domain.model.BudgetProgress
import com.kyovo.cents.domain.model.SubcategoryId
import com.kyovo.cents.domain.port.input.GetBudgetProgressUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.YearMonth

/**
 * Always answers with the same progress map, whatever the month asked — enough for a service that
 * only ever checks one month at a time, unlike the real [GetBudgetProgressUseCase] whose progress
 * differs by month.
 */
private class FakeGetBudgetProgress(
    private val progress: Map<SubcategoryId, BudgetProgress>,
) : GetBudgetProgressUseCase
{
    override fun observe(subcategoryId: SubcategoryId, month: YearMonth, accountId: AccountId?): Flow<BudgetProgress?> =
        flowOf(progress[subcategoryId])

    override fun observeAll(month: YearMonth, accountId: AccountId?): Flow<Map<SubcategoryId, BudgetProgress>> =
        flowOf(progress)
}

/**
 * Which budgets just crossed an alert threshold (close or over) and have not already been reported —
 * this is what a periodic WorkManager check asks, so a crossing already reported this month is never
 * repeated, however many times the check runs.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CheckBudgetAlertsServiceTest
{
    private val groceriesId = aSubcategoryId("11111111-1111-1111-1111-111111111111")
    private val fuelId = aSubcategoryId("22222222-2222-2222-2222-222222222222")
    private val september = YearMonth.of(2026, 9)

    private val budgetAlertRepository = InMemoryBudgetAlertRepository()

    private fun aServiceWith(progress: Map<SubcategoryId, BudgetProgress>) =
        CheckBudgetAlertsService(FakeGetBudgetProgress(progress), budgetAlertRepository)

    @Test
    fun `a budget crossing its alert threshold for the first time is reported and recorded`() = runTest()
    {
        // GIVEN a budget exactly at its limit, not yet reported this month
        val progress = mapOf(groceriesId to BudgetProgress(aMoney(30_000), aMoney(30_000)))
        val service = aServiceWith(progress)

        // WHEN
        val alerts = service.check(september)

        // THEN
        val expected = BudgetAlert(groceriesId, september, BudgetAlertLevel.CLOSE_TO_LIMIT)
        assertThat(alerts).containsExactly(expected)
        assertThat(budgetAlertRepository.saved).containsExactly(expected)
    }

    @Test
    fun `a level already reported this month for the same subcategory is not reported again`() = runTest()
    {
        // GIVEN a level already reported (a first check, or set by hand for the test)
        val progress = mapOf(groceriesId to BudgetProgress(aMoney(30_000), aMoney(30_000)))
        val service = aServiceWith(progress)
        service.check(september)

        // WHEN checking again, nothing about the spending changed
        val alerts = service.check(september)

        // THEN
        assertThat(alerts).isEmpty()
        assertThat(budgetAlertRepository.saved).hasSize(1)
    }

    // CLOSE_TO_LIMIT and OVER are two distinct crossings of the same budget: reporting one does not
    // excuse the other from being reported too, once the spending actually reaches it.
    @Test
    fun `crossing over the limit after close was already reported is reported as its own alert`() = runTest()
    {
        // GIVEN close already reported
        val closeProgress = mapOf(groceriesId to BudgetProgress(aMoney(30_000), aMoney(30_000)))
        val service = aServiceWith(closeProgress)
        service.check(september)

        // WHEN spending grows past the limit
        val overProgress = mapOf(groceriesId to BudgetProgress(aMoney(30_000), aMoney(30_001)))
        val alerts = aServiceWith(overProgress).check(september)

        // THEN
        val expected = BudgetAlert(groceriesId, september, BudgetAlertLevel.OVER)
        assertThat(alerts).containsExactly(expected)
        assertThat(budgetAlertRepository.saved).containsExactlyInAnyOrder(
            BudgetAlert(groceriesId, september, BudgetAlertLevel.CLOSE_TO_LIMIT),
            expected,
        )
    }

    @Test
    fun `a budget on track has nothing to report`() = runTest()
    {
        // GIVEN well below the alert threshold
        val progress = mapOf(groceriesId to BudgetProgress(aMoney(30_000), aMoney(1_000)))
        val service = aServiceWith(progress)

        // WHEN
        val alerts = service.check(september)

        // THEN
        assertThat(alerts).isEmpty()
        assertThat(budgetAlertRepository.saved).isEmpty()
    }

    @Test
    fun `each subcategory is checked and reported on its own`() = runTest()
    {
        // GIVEN groceries over its limit, fuel on track, both checked at once
        val progress = mapOf(
            groceriesId to BudgetProgress(aMoney(30_000), aMoney(30_001)),
            fuelId to BudgetProgress(aMoney(10_000), aMoney(1_000)),
        )
        val service = aServiceWith(progress)

        // WHEN
        val alerts = service.check(september)

        // THEN only groceries has anything to report
        assertThat(alerts).containsExactly(BudgetAlert(groceriesId, september, BudgetAlertLevel.OVER))
    }
}
