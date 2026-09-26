package com.kyovo.cents.application.usecase

import com.kyovo.cents.application.fakes.InMemoryBudgetRepository
import com.kyovo.cents.application.fakes.InMemorySubcategoryRepository
import com.kyovo.cents.application.fakes.aBudget
import com.kyovo.cents.application.fakes.aMoney
import com.kyovo.cents.application.fakes.aSetBudgetCommand
import com.kyovo.cents.application.fakes.aSubcategory
import com.kyovo.cents.application.fakes.aSubcategoryId
import com.kyovo.cents.application.fakes.assertThatThrownBySuspending
import com.kyovo.cents.domain.exception.InvalidBudgetSubcategoryException
import com.kyovo.cents.domain.exception.SubcategoryNotFoundException
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.SubcategoryName
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.YearMonth

/**
 * A budget is the limit a user sets on an expense subcategory for one month. Setting it is one action
 * whether or not the month already had a limit, which is why the use case is `set`, not `create`.
 */
class SetBudgetServiceTest
{
    private val subcategoryRepository = InMemorySubcategoryRepository()
    private val budgetRepository = InMemoryBudgetRepository()
    private val service = SetBudgetService(budgetRepository, subcategoryRepository)

    @Test
    fun `sets the limit of an expense subcategory for a month, and saves it`() = runTest()
    {
        // GIVEN
        subcategoryRepository.save(aSubcategory())

        // WHEN
        val result = service.set(aSetBudgetCommand())

        // THEN
        val expected = aBudget()
        assertThat(result).isEqualTo(expected)
        assertThat(budgetRepository.saved).containsExactly(expected)
    }

    @Test
    fun `replaces the limit when the month of that subcategory already has one`() = runTest()
    {
        // GIVEN
        subcategoryRepository.save(aSubcategory())
        budgetRepository.save(aBudget(limit = aMoney(30_000)))

        // WHEN
        val result = service.set(aSetBudgetCommand(limit = aMoney(45_000)))

        // THEN one budget for that month, with the new limit: not two
        val expected = aBudget(limit = aMoney(45_000))
        assertThat(result).isEqualTo(expected)
        assertThat(budgetRepository.saved).containsExactly(expected)
    }

    @Test
    fun `throws when the subcategory does not exist, and saves nothing`() = runTest()
    {
        // GIVEN another subcategory exists, so that it is really the id that is unknown
        subcategoryRepository.save(aSubcategory(id = aSubcategoryId("88888888-8888-8888-8888-888888888888")))

        // WHEN / THEN
        assertThatThrownBySuspending { service.set(aSetBudgetCommand(subcategoryId = aSubcategoryId())) }
            .isInstanceOf(SubcategoryNotFoundException::class.java)
        assertThat(budgetRepository.saved).isEmpty()
    }

    // A budget is a ceiling on spending: an income subcategory has nothing to be capped, and a "budget"
    // on it would never be overspent or reached in any sense a user could read.
    @Test
    fun `refuses an income subcategory, and saves nothing`() = runTest()
    {
        // GIVEN
        subcategoryRepository.save(aSubcategory(kind = RecordableTransactionCategory.INCOME, name = SubcategoryName("Salaire")))

        // WHEN / THEN
        assertThatThrownBySuspending { service.set(aSetBudgetCommand()) }
            .isInstanceOf(InvalidBudgetSubcategoryException::class.java)
        assertThat(budgetRepository.saved).isEmpty()
    }

    // A budget belongs to its month: changing September's limit must not rewrite what August was
    // judged against, which is the reason a budget carries its month at all.
    @Test
    fun `leaves the other months and the other subcategories as they were`() = runTest()
    {
        // GIVEN
        val transport = aSubcategoryId("77777777-7777-7777-7777-777777777777")
        subcategoryRepository.save(aSubcategory())
        val august = aBudget(month = YearMonth.of(2026, 8), limit = aMoney(30_000))
        val transportInSeptember = aBudget(subcategoryId = transport, month = YearMonth.of(2026, 9), limit = aMoney(10_000))
        budgetRepository.save(august)
        budgetRepository.save(transportInSeptember)

        // WHEN
        service.set(aSetBudgetCommand(month = YearMonth.of(2026, 9), limit = aMoney(45_000)))

        // THEN
        assertThat(budgetRepository.saved).containsExactlyInAnyOrder(
            august,
            transportInSeptember,
            aBudget(month = YearMonth.of(2026, 9), limit = aMoney(45_000))
        )
    }
}
