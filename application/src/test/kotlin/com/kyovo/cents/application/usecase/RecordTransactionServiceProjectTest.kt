package com.kyovo.cents.application.usecase

import com.kyovo.cents.application.fakes.FixedTransactionIdGenerator
import com.kyovo.cents.application.fakes.InMemoryAccountRepository
import com.kyovo.cents.application.fakes.InMemoryProjectRepository
import com.kyovo.cents.application.fakes.InMemorySubcategoryRepository
import com.kyovo.cents.application.fakes.InMemoryTransactionRepository
import com.kyovo.cents.application.fakes.aProject
import com.kyovo.cents.application.fakes.aProjectId
import com.kyovo.cents.application.fakes.aRecordTransactionCommand
import com.kyovo.cents.application.fakes.aSubcategory
import com.kyovo.cents.application.fakes.aSubcategoryId
import com.kyovo.cents.application.fakes.aTransactionId
import com.kyovo.cents.application.fakes.anAccount
import com.kyovo.cents.application.fakes.anAccountId
import com.kyovo.cents.application.fakes.assertThatThrownBySuspending
import com.kyovo.cents.domain.exception.AccountNotFoundException
import com.kyovo.cents.domain.exception.ProjectNotFoundException
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * An income or an expense can be recorded for a project, next to its subcategory. The project must
 * exist; the new argument of [RecordTransactionService] is the project repository (appended last).
 */
class RecordTransactionServiceProjectTest
{
    private val accountId = anAccountId()
    private val projectId = aProjectId()
    private val accountRepository = InMemoryAccountRepository()
    private val transactionRepository = InMemoryTransactionRepository()
    private val subcategoryRepository = InMemorySubcategoryRepository()
    private val projectRepository = InMemoryProjectRepository()
    private val service = RecordTransactionService(
        accountRepository,
        transactionRepository,
        FixedTransactionIdGenerator(aTransactionId()),
        projectRepository,
        subcategoryRepository,
    )

    @ParameterizedTest
    @EnumSource(RecordableTransactionCategory::class)
    fun `records an income or an expense for an existing project`(category: RecordableTransactionCategory) =
        runTest()
        {
            // GIVEN
            accountRepository.save(anAccount(id = accountId))
            projectRepository.save(aProject(id = projectId))

            // WHEN
            val result = service.record(
                aRecordTransactionCommand(
                    accountId = accountId,
                    category = category,
                    projectId = projectId
                )
            )

            // THEN
            assertThat(result.projectId).isEqualTo(projectId)
            assertThat(transactionRepository.saved).containsExactly(result)
        }

    @Test
    fun `records a transaction for a project and a subcategory at the same time`() = runTest()
    {
        // GIVEN
        val restaurantsId = aSubcategoryId("11111111-1111-1111-1111-111111111111")
        accountRepository.save(anAccount(id = accountId))
        projectRepository.save(aProject(id = projectId))
        subcategoryRepository.save(aSubcategory(id = restaurantsId))

        // WHEN
        val result = service.record(
            aRecordTransactionCommand(
                accountId = accountId,
                subcategoryId = restaurantsId,
                projectId = projectId
            )
        )

        // THEN
        assertThat(result.subcategoryId).isEqualTo(restaurantsId)
        assertThat(result.projectId).isEqualTo(projectId)
    }

    @Test
    fun `records a transaction without a project when none is given`() = runTest()
    {
        // GIVEN
        accountRepository.save(anAccount(id = accountId))

        // WHEN
        val result = service.record(aRecordTransactionCommand(accountId = accountId))

        // THEN
        assertThat(result.projectId).isNull()
    }

    @Test
    fun `throws and saves nothing when the project does not exist`() = runTest()
    {
        // GIVEN
        accountRepository.save(anAccount(id = accountId))

        // WHEN / THEN
        assertThatThrownBySuspending {
            service.record(aRecordTransactionCommand(accountId = accountId, projectId = projectId))
        }.isInstanceOf(ProjectNotFoundException::class.java)
        assertThat(transactionRepository.saved).isEmpty()
    }

    @Test
    fun `reports an unknown account before an unknown project`() = runTest()
    {
        assertThatThrownBySuspending {
            service.record(aRecordTransactionCommand(accountId = accountId, projectId = projectId))
        }.isInstanceOf(AccountNotFoundException::class.java)
    }
}
