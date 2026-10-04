package com.kyovo.cents.application.usecase

import com.kyovo.cents.application.fakes.InMemoryProjectRepository
import com.kyovo.cents.application.fakes.InMemoryTransactionRepository
import com.kyovo.cents.application.fakes.InMemoryUnitOfWork
import com.kyovo.cents.application.fakes.aMoney
import com.kyovo.cents.application.fakes.aProject
import com.kyovo.cents.application.fakes.aProjectId
import com.kyovo.cents.application.fakes.aTransaction
import com.kyovo.cents.application.fakes.aTransactionId
import com.kyovo.cents.application.fakes.anInstant
import com.kyovo.cents.domain.model.ProjectId
import com.kyovo.cents.domain.model.ProjectName
import com.kyovo.cents.domain.model.TransactionCategory
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Deleting a project is never refused: its transactions are kept, *without a project*, and nothing else
 * about them changes (so no balance moves) - the same rule as deleting a subcategory. The project and the
 * change to its transactions happen in one all-or-nothing step.
 */
class DeleteProjectServiceTest
{
    private val japanId = aProjectId("11111111-1111-1111-1111-111111111111")
    private val kitchenId = aProjectId("22222222-2222-2222-2222-222222222222")

    private val projectRepository = InMemoryProjectRepository()
    private val transactionRepository = InMemoryTransactionRepository()
    private val unitOfWork = InMemoryUnitOfWork()
    private val service = DeleteProjectService(projectRepository, transactionRepository, unitOfWork)

    private val japan = aProject(id = japanId, name = ProjectName("Voyage au Japon"))
    private val kitchen = aProject(id = kitchenId, name = ProjectName("Travaux cuisine"))

    private fun anExpenseIn(suffix: Int, projectId: ProjectId?) =
        aTransaction(
            id = aTransactionId("33333333-3333-3333-3333-33333333333$suffix"),
            amount = aMoney(8_000),
            date = anInstant("2026-09-20T08:30:00Z"),
            category = TransactionCategory.EXPENSE,
            projectId = projectId,
        )

    @Test
    fun `deletes the project and leaves the other projects alone`() = runTest()
    {
        // GIVEN
        projectRepository.save(japan)
        projectRepository.save(kitchen)

        // WHEN
        service.delete(japanId)

        // THEN
        assertThat(projectRepository.saved).containsExactly(kitchen)
    }

    @Test
    fun `keeps the transactions of the project, without a project, with everything else unchanged`() = runTest()
    {
        // GIVEN
        projectRepository.save(japan)
        transactionRepository.save(anExpenseIn(1, japanId))
        transactionRepository.save(anExpenseIn(2, japanId))

        // WHEN
        service.delete(japanId)

        // THEN
        assertThat(transactionRepository.saved).containsExactlyInAnyOrder(
            anExpenseIn(1, projectId = null),
            anExpenseIn(2, projectId = null),
        )
    }

    // A refund attached to the project is a transaction like the others.
    @ParameterizedTest
    @EnumSource(value = TransactionCategory::class, names = ["EXPENSE", "INCOME"])
    fun `works the same for an income as for an expense`(category: TransactionCategory) = runTest()
    {
        // GIVEN
        projectRepository.save(japan)
        transactionRepository.save(aTransaction(category = category, projectId = japanId))

        // WHEN
        service.delete(japanId)

        // THEN
        assertThat(transactionRepository.saved).containsExactly(aTransaction(category = category, projectId = null))
    }

    @Test
    fun `does not touch the transactions of other projects, nor those with none`() = runTest()
    {
        // GIVEN
        projectRepository.save(japan)
        projectRepository.save(kitchen)
        val inKitchen = anExpenseIn(1, kitchenId)
        val withNone = anExpenseIn(2, null)
        val deposit = aTransaction(id = aTransactionId("44444444-4444-4444-4444-444444444444"))
        transactionRepository.save(inKitchen)
        transactionRepository.save(withNone)
        transactionRepository.save(deposit)
        transactionRepository.save(anExpenseIn(3, japanId))

        // WHEN
        service.delete(japanId)

        // THEN
        assertThat(transactionRepository.saved).containsExactlyInAnyOrder(
            inKitchen, withNone, deposit, anExpenseIn(3, projectId = null),
        )
    }

    @Test
    fun `does not change any balance`() = runTest()
    {
        // GIVEN
        projectRepository.save(japan)
        transactionRepository.save(anExpenseIn(1, japanId))
        transactionRepository.save(anExpenseIn(2, japanId))
        val before = transactionRepository.saved.sumOf { it.signedAmount }

        // WHEN
        service.delete(japanId)

        // THEN
        assertThat(transactionRepository.saved.sumOf { it.signedAmount }).isEqualTo(before)
    }

    @Test
    fun `does nothing when no project matches the given id`() = runTest()
    {
        // GIVEN
        projectRepository.save(kitchen)
        val transaction = anExpenseIn(1, kitchenId)
        transactionRepository.save(transaction)

        // WHEN / THEN it is not an error, and nothing changes
        service.delete(japanId)
        assertThat(projectRepository.saved).containsExactly(kitchen)
        assertThat(transactionRepository.saved).containsExactly(transaction)
    }

    @Test
    fun `deletes the project and updates its transactions in a single unit of work`() = runTest()
    {
        // GIVEN
        projectRepository.save(japan)
        transactionRepository.save(anExpenseIn(1, japanId))

        // WHEN
        service.delete(japanId)

        // THEN
        assertThat(unitOfWork.executionCount).isEqualTo(1)
    }
}
