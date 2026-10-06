package com.kyovo.cents.application.usecase

import com.kyovo.cents.application.fakes.InMemoryProjectRepository
import com.kyovo.cents.application.fakes.InMemoryTransactionRepository
import com.kyovo.cents.application.fakes.aMoney
import com.kyovo.cents.application.fakes.aProject
import com.kyovo.cents.application.fakes.aProjectId
import com.kyovo.cents.application.fakes.aTransaction
import com.kyovo.cents.application.fakes.aTransactionId
import com.kyovo.cents.application.fakes.anAccountId
import com.kyovo.cents.domain.model.AlertThreshold
import com.kyovo.cents.domain.model.ProjectId
import com.kyovo.cents.domain.model.ProjectProgress
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.model.TransactionCategory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * What a project has cost is not stored: it is worked out from the transactions that belong to it, so it
 * can never disagree with them. Expenses add up, incomes (a refund) come off; whatever the account - an
 * archived account's spending is history and counts too - and whatever the date: a project is not a month.
 */
class GetProjectProgressServiceTest
{
    private val japanId = aProjectId("11111111-1111-1111-1111-111111111111")
    private val kitchenId = aProjectId("22222222-2222-2222-2222-222222222222")

    private val projectRepository = InMemoryProjectRepository()
    private val transactionRepository = InMemoryTransactionRepository()
    private val service = GetProjectProgressService(projectRepository, transactionRepository)

    private fun aTransactionOf(
        suffix: Int,
        cents: Long,
        category: TransactionCategory = TransactionCategory.EXPENSE,
        projectId: ProjectId? = japanId,
        accountSuffix: String = "1",
    ): Transaction =
        aTransaction(
            id = aTransactionId("33333333-3333-3333-3333-33333333333$suffix"),
            accountId = anAccountId("11111111-1111-1111-1111-11111111111$accountSuffix"),
            amount = aMoney(cents),
            category = category,
            projectId = projectId,
        )

    @Test
    fun `adds up the expenses of the project and counts its transactions`() = runTest()
    {
        // GIVEN
        projectRepository.save(aProject(id = japanId, target = aMoney(300_000)))
        transactionRepository.save(aTransactionOf(1, 80_000))
        transactionRepository.save(aTransactionOf(2, 45_000))

        // WHEN
        val progress = service.observe(japanId).first()

        // THEN
        assertThat(progress).isEqualTo(
            ProjectProgress(aMoney(300_000), expenses = aMoney(125_000), incomes = aMoney(0), transactionCount = 2)
        )
    }

    // The alert level is the progress's to work out, with the threshold of the project it is about.
    @Test
    fun `carries the alert threshold of the project`() = runTest()
    {
        // GIVEN
        projectRepository.save(aProject(id = japanId, target = aMoney(100_000), alertThreshold = AlertThreshold(60)))
        projectRepository.save(aProject(id = kitchenId, target = aMoney(100_000)))
        transactionRepository.save(aTransactionOf(1, 65_000))
        transactionRepository.save(aTransactionOf(2, 65_000, projectId = kitchenId))

        // WHEN
        val all = service.observeAll().first()

        // THEN 65 % is close for the 60 % project and not yet for the 80 % one
        assertThat(all.getValue(japanId).alertThreshold).isEqualTo(AlertThreshold(60))
        assertThat(all.getValue(japanId).alertLevel()).isEqualTo(com.kyovo.cents.domain.model.BudgetAlertLevel.CLOSE_TO_LIMIT)
        assertThat(all.getValue(kitchenId).alertThreshold).isEqualTo(AlertThreshold.DEFAULT)
        assertThat(all.getValue(kitchenId).alertLevel()).isNull()
        assertThat(service.observe(japanId).first()!!.alertThreshold).isEqualTo(AlertThreshold(60))
    }

    @Test
    fun `an income attached to the project comes off what it cost`() = runTest()
    {
        // GIVEN
        projectRepository.save(aProject(id = japanId))
        transactionRepository.save(aTransactionOf(1, 80_000))
        transactionRepository.save(aTransactionOf(2, 12_000, TransactionCategory.INCOME))

        // WHEN
        val progress = service.observe(japanId).first()!!

        // THEN
        assertThat(progress.expenses).isEqualTo(aMoney(80_000))
        assertThat(progress.incomes).isEqualTo(aMoney(12_000))
        assertThat(progress.net).isEqualTo(68_000)
        assertThat(progress.transactionCount).isEqualTo(2)
    }

    @Test
    fun `counts the transactions of every account, archived or not`() = runTest()
    {
        // GIVEN
        projectRepository.save(aProject(id = japanId))
        transactionRepository.save(aTransactionOf(1, 80_000, accountSuffix = "1"))
        transactionRepository.save(aTransactionOf(2, 20_000, accountSuffix = "2"))

        // WHEN / THEN
        assertThat(service.observe(japanId).first()!!.net).isEqualTo(100_000)
    }

    @Test
    fun `ignores the transactions of other projects and those with none`() = runTest()
    {
        // GIVEN
        projectRepository.save(aProject(id = japanId))
        transactionRepository.save(aTransactionOf(1, 80_000))
        transactionRepository.save(aTransactionOf(2, 5_000, projectId = kitchenId))
        transactionRepository.save(aTransactionOf(3, 7_000, projectId = null))

        // WHEN
        val progress = service.observe(japanId).first()!!

        // THEN
        assertThat(progress.net).isEqualTo(80_000)
        assertThat(progress.transactionCount).isEqualTo(1)
    }

    @Test
    fun `a project without transactions has spent nothing, and keeps its target`() = runTest()
    {
        // GIVEN
        projectRepository.save(aProject(id = japanId, target = aMoney(300_000)))

        // WHEN
        val progress = service.observe(japanId).first()!!

        // THEN
        assertThat(progress.net).isZero()
        assertThat(progress.target).isEqualTo(aMoney(300_000))
        assertThat(progress.transactionCount).isZero()
    }

    @Test
    fun `is null while there is no such project`() = runTest()
    {
        // GIVEN transactions that point to a project that does not exist (orphans are ignored)
        transactionRepository.save(aTransactionOf(1, 80_000))

        // WHEN / THEN
        assertThat(service.observe(japanId).first()).isNull()
    }

    @Test
    fun `follows the transactions and the target as they change`() = runTest()
    {
        // GIVEN
        projectRepository.save(aProject(id = japanId, target = aMoney(100_000)))
        transactionRepository.save(aTransactionOf(1, 80_000))
        assertThat(service.observe(japanId).first()!!.isOverTarget).isFalse()

        // WHEN more is spent
        transactionRepository.save(aTransactionOf(2, 30_000))

        // THEN
        assertThat(service.observe(japanId).first()!!.isOverTarget).isTrue()

        // WHEN the target is raised
        projectRepository.save(aProject(id = japanId, target = aMoney(200_000)))

        // THEN
        assertThat(service.observe(japanId).first()!!.isOverTarget).isFalse()
    }

    @Test
    fun `observeAll lists every existing project, empty ones included`() = runTest()
    {
        // GIVEN
        projectRepository.save(aProject(id = japanId))
        projectRepository.save(aProject(id = kitchenId, target = aMoney(900_000)))
        transactionRepository.save(aTransactionOf(1, 80_000))
        transactionRepository.save(aTransactionOf(2, 5_000, projectId = null))

        // WHEN
        val all = service.observeAll().first()

        // THEN
        assertThat(all.keys).containsExactlyInAnyOrder(japanId, kitchenId)
        assertThat(all.getValue(japanId).net).isEqualTo(80_000)
        assertThat(all.getValue(kitchenId).net).isZero()
        assertThat(all.getValue(kitchenId).target).isEqualTo(aMoney(900_000))
    }

    @Test
    fun `observeAll ignores the transactions of a project that does not exist`() = runTest()
    {
        // GIVEN
        transactionRepository.save(aTransactionOf(1, 80_000, projectId = aProjectId("99999999-9999-9999-9999-999999999999")))

        // WHEN / THEN
        assertThat(service.observeAll().first()).isEmpty()
    }
}
