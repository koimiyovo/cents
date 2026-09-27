package com.kyovo.cents.application.usecase

import com.kyovo.cents.application.fakes.InMemoryBudgetRepository
import com.kyovo.cents.application.fakes.InMemorySubcategoryRepository
import com.kyovo.cents.application.fakes.InMemoryTransactionRepository
import com.kyovo.cents.application.fakes.InMemoryUnitOfWork
import com.kyovo.cents.application.fakes.aBudget
import com.kyovo.cents.application.fakes.aMoney
import com.kyovo.cents.application.fakes.aSubcategory
import com.kyovo.cents.application.fakes.aSubcategoryId
import com.kyovo.cents.application.fakes.aTransaction
import com.kyovo.cents.application.fakes.aTransactionId
import com.kyovo.cents.application.fakes.aTransactionTitle
import com.kyovo.cents.application.fakes.anAccountId
import com.kyovo.cents.application.fakes.anInstant
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.SubcategoryId
import com.kyovo.cents.domain.model.SubcategoryName
import com.kyovo.cents.domain.model.TransactionCategory
import com.kyovo.cents.domain.model.TransactionDescription
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.YearMonth

/**
 * Deleting a subcategory is never refused: the transactions that used it are kept and are left
 * *uncategorised* (no subcategory) — a subcategory is optional on a transaction anyway. Nothing else
 * about them changes, so no balance moves. The subcategory and the change to its transactions happen
 * in one all-or-nothing step (a unit of work), like deleting an account together with its history.
 */
class DeleteSubcategoryServiceTest
{
    private val groceriesId = aSubcategoryId("11111111-1111-1111-1111-111111111111")
    private val fuelId = aSubcategoryId("22222222-2222-2222-2222-222222222222")

    private val subcategoryRepository = InMemorySubcategoryRepository()
    private val transactionRepository = InMemoryTransactionRepository()
    private val unitOfWork = InMemoryUnitOfWork()
    private val budgetRepository = InMemoryBudgetRepository()
    private val service =
        DeleteSubcategoryService(
            subcategoryRepository,
            transactionRepository,
            budgetRepository,
            unitOfWork
        )

    private val groceries = aSubcategory(id = groceriesId, name = SubcategoryName("Alimentation"))
    private val fuel = aSubcategory(id = fuelId, name = SubcategoryName("Carburant"))

    private fun anExpenseIn(suffix: Int, subcategoryId: SubcategoryId?) =
        aTransaction(
            id = aTransactionId("33333333-3333-3333-3333-33333333333$suffix"),
            accountId = anAccountId(),
            amount = aMoney(1_250),
            date = anInstant("2026-09-20T08:30:00Z"),
            category = TransactionCategory.EXPENSE,
            title = aTransactionTitle("Courses"),
            subcategoryId = subcategoryId,
            description = TransactionDescription.of("Marché du samedi"),
        )

    @Test
    fun `deletes the subcategory`() = runTest()
    {
        // GIVEN
        subcategoryRepository.save(groceries)

        // WHEN
        service.delete(groceriesId)

        // THEN
        assertThat(subcategoryRepository.saved).isEmpty()
        assertThat(subcategoryRepository.findById(groceriesId)).isNull()
    }

    @Test
    fun `leaves the other subcategories alone`() = runTest()
    {
        // GIVEN
        subcategoryRepository.save(groceries)
        subcategoryRepository.save(fuel)

        // WHEN
        service.delete(groceriesId)

        // THEN
        assertThat(subcategoryRepository.saved).containsExactly(fuel)
    }

    @Test
    fun `keeps the transactions that used it, uncategorised, with everything else unchanged`() =
        runTest()
        {
            // GIVEN
            subcategoryRepository.save(groceries)
            transactionRepository.save(anExpenseIn(1, groceriesId))
            transactionRepository.save(anExpenseIn(2, groceriesId))

            // WHEN
            service.delete(groceriesId)

            // THEN
            assertThat(transactionRepository.saved).containsExactlyInAnyOrder(
                anExpenseIn(1, subcategoryId = null),
                anExpenseIn(2, subcategoryId = null),
            )
        }

    @ParameterizedTest
    @EnumSource(RecordableTransactionCategory::class)
    fun `works the same for an income subcategory as for an expense one`(kind: RecordableTransactionCategory) =
        runTest()
        {
            // GIVEN
            val subcategory = aSubcategory(id = groceriesId, kind = kind)
            val category =
                if (kind == RecordableTransactionCategory.INCOME) TransactionCategory.INCOME
                else TransactionCategory.EXPENSE
            subcategoryRepository.save(subcategory)
            transactionRepository.save(
                aTransaction(
                    category = category,
                    subcategoryId = groceriesId
                )
            )

            // WHEN
            service.delete(groceriesId)

            // THEN
            assertThat(transactionRepository.saved).containsExactly(
                aTransaction(category = category, subcategoryId = null),
            )
        }

    @Test
    fun `does not touch the transactions of other subcategories, nor those with none`() = runTest()
    {
        // GIVEN
        subcategoryRepository.save(groceries)
        subcategoryRepository.save(fuel)
        val withFuel = anExpenseIn(1, fuelId)
        val uncategorised = anExpenseIn(2, null)
        val deposit = aTransaction(
            id = aTransactionId("44444444-4444-4444-4444-444444444444"),
            category = TransactionCategory.INITIAL_DEPOSIT,
        )
        transactionRepository.save(withFuel)
        transactionRepository.save(uncategorised)
        transactionRepository.save(deposit)
        transactionRepository.save(anExpenseIn(3, groceriesId))

        // WHEN
        service.delete(groceriesId)

        // THEN
        assertThat(transactionRepository.saved).containsExactlyInAnyOrder(
            withFuel,
            uncategorised,
            deposit,
            anExpenseIn(3, subcategoryId = null),
        )
    }

    @Test
    fun `does not change any balance`() = runTest()
    {
        // GIVEN
        subcategoryRepository.save(groceries)
        transactionRepository.save(anExpenseIn(1, groceriesId))
        transactionRepository.save(anExpenseIn(2, groceriesId))
        val balanceBefore = transactionRepository.saved.sumOf { it.signedAmount }

        // WHEN
        service.delete(groceriesId)

        // THEN
        assertThat(transactionRepository.saved.sumOf { it.signedAmount }).isEqualTo(balanceBefore)
    }

    // Unlike a transaction, a budget means nothing without its subcategory: there is no "uncategorised
    // budget" to keep. So the budgets go with it, every month's, and only that subcategory's.
    @Test
    fun `deletes the budgets of the subcategory, for every month, and keeps the others`() =
        runTest()
        {
            // GIVEN
            subcategoryRepository.save(groceries)
            subcategoryRepository.save(fuel)
            budgetRepository.save(
                aBudget(
                    subcategoryId = groceriesId,
                    month = YearMonth.of(2026, 8)
                )
            )
            budgetRepository.save(
                aBudget(
                    subcategoryId = groceriesId,
                    month = YearMonth.of(2026, 9)
                )
            )
            val fuelBudget = aBudget(subcategoryId = fuelId, month = YearMonth.of(2026, 9))
            budgetRepository.save(fuelBudget)

            // WHEN
            service.delete(groceriesId)

            // THEN
            assertThat(budgetRepository.saved).containsExactly(fuelBudget)
        }

    @Test
    fun `does nothing when no subcategory matches the given id`() = runTest()
    {
        // GIVEN
        subcategoryRepository.save(fuel)
        val transaction = anExpenseIn(1, fuelId)
        transactionRepository.save(transaction)

        // WHEN / THEN it is not an error, and nothing changes
        service.delete(groceriesId)
        assertThat(subcategoryRepository.saved).containsExactly(fuel)
        assertThat(transactionRepository.saved).containsExactly(transaction)
    }

    @Test
    fun `deletes the subcategory and updates its transactions in a single unit of work`() =
        runTest()
        {
            // GIVEN
            subcategoryRepository.save(groceries)
            transactionRepository.save(anExpenseIn(1, groceriesId))
            transactionRepository.save(anExpenseIn(2, groceriesId))

            // WHEN
            service.delete(groceriesId)

            // THEN
            assertThat(unitOfWork.executionCount).isEqualTo(1)
        }
}
