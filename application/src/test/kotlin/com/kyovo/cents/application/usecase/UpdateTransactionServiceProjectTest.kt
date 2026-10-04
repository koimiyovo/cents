package com.kyovo.cents.application.usecase

import com.kyovo.cents.application.fakes.InMemoryAccountRepository
import com.kyovo.cents.application.fakes.InMemoryProjectRepository
import com.kyovo.cents.application.fakes.InMemorySubcategoryRepository
import com.kyovo.cents.application.fakes.InMemoryTransactionRepository
import com.kyovo.cents.application.fakes.aProject
import com.kyovo.cents.application.fakes.aProjectId
import com.kyovo.cents.application.fakes.aTransaction
import com.kyovo.cents.application.fakes.aTransactionId
import com.kyovo.cents.application.fakes.anAccountId
import com.kyovo.cents.application.fakes.anUpdateTransactionCommand
import com.kyovo.cents.application.fakes.assertThatThrownBySuspending
import com.kyovo.cents.domain.exception.CannotUpdateTransferException
import com.kyovo.cents.domain.exception.ProjectNotFoundException
import com.kyovo.cents.domain.model.TransactionCategory
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Like the other update commands, [com.kyovo.cents.domain.port.input.UpdateTransactionCommand] carries the
 * whole new state, so its `projectId` is required-nullable: null takes the transaction out of its project.
 * The new argument of [UpdateTransactionService] is the project repository (appended last).
 */
class UpdateTransactionServiceProjectTest
{
    private val id = aTransactionId()
    private val accountId = anAccountId()
    private val japanId = aProjectId("11111111-1111-1111-1111-111111111111")
    private val kitchenId = aProjectId("22222222-2222-2222-2222-222222222222")

    private val transactionRepository = InMemoryTransactionRepository()
    private val projectRepository = InMemoryProjectRepository()
    private val service = UpdateTransactionService(
        InMemoryAccountRepository(),
        transactionRepository,
        InMemorySubcategoryRepository(),
        projectRepository,
    )

    private suspend fun givenAnExpense(projectId: com.kyovo.cents.domain.model.ProjectId? = null)
    {
        transactionRepository.save(
            aTransaction(id = id, accountId = accountId, category = TransactionCategory.EXPENSE, projectId = projectId)
        )
    }

    @Test
    fun `puts a transaction in a project`() = runTest()
    {
        // GIVEN
        givenAnExpense()
        projectRepository.save(aProject(id = japanId))

        // WHEN
        val result = service.update(anUpdateTransactionCommand(id = id, accountId = accountId, projectId = japanId))

        // THEN
        assertThat(result.projectId).isEqualTo(japanId)
        assertThat(transactionRepository.saved).containsExactly(result)
    }

    @Test
    fun `moves a transaction from a project to another`() = runTest()
    {
        // GIVEN
        givenAnExpense(projectId = japanId)
        projectRepository.save(aProject(id = japanId))
        projectRepository.save(aProject(id = kitchenId))

        // WHEN
        val result = service.update(anUpdateTransactionCommand(id = id, accountId = accountId, projectId = kitchenId))

        // THEN
        assertThat(result.projectId).isEqualTo(kitchenId)
    }

    @Test
    fun `a null project takes the transaction out of its project`() = runTest()
    {
        // GIVEN
        givenAnExpense(projectId = japanId)
        projectRepository.save(aProject(id = japanId))

        // WHEN
        val result = service.update(anUpdateTransactionCommand(id = id, accountId = accountId, projectId = null))

        // THEN
        assertThat(result.projectId).isNull()
    }

    @Test
    fun `throws and leaves the transaction as it was when the project does not exist`() = runTest()
    {
        // GIVEN
        givenAnExpense(projectId = japanId)
        val before = transactionRepository.saved.single()

        // WHEN / THEN
        assertThatThrownBySuspending {
            service.update(anUpdateTransactionCommand(id = id, accountId = accountId, projectId = kitchenId))
        }.isInstanceOf(ProjectNotFoundException::class.java)
        assertThat(transactionRepository.saved).containsExactly(before)
    }

    // The project is resolved after the other checks: a transfer leg is refused for what it is.
    @Test
    fun `refuses a transfer leg before looking at the project`() = runTest()
    {
        // GIVEN
        transactionRepository.save(aTransaction(id = id, accountId = accountId, category = TransactionCategory.TRANSFER_OUT))

        // WHEN / THEN
        assertThatThrownBySuspending {
            service.update(anUpdateTransactionCommand(id = id, accountId = accountId, projectId = kitchenId))
        }.isInstanceOf(CannotUpdateTransferException::class.java)
    }

    @Test
    fun `editing something else keeps the project when the command carries it`() = runTest()
    {
        // GIVEN
        givenAnExpense(projectId = japanId)
        projectRepository.save(aProject(id = japanId))

        // WHEN
        val result = service.update(
            anUpdateTransactionCommand(id = id, accountId = accountId, amount = com.kyovo.cents.application.fakes.aMoney(9_900), projectId = japanId)
        )

        // THEN
        assertThat(result.projectId).isEqualTo(japanId)
        assertThat(result.amount.value).isEqualTo(9_900)
    }
}
