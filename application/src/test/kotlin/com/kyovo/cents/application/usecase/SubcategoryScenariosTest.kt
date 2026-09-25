package com.kyovo.cents.application.usecase

import com.kyovo.cents.application.fakes.assertThatThrownBySuspending
import kotlinx.coroutines.test.runTest
import com.kyovo.cents.application.fakes.InMemoryAccountRepository
import com.kyovo.cents.application.fakes.InMemorySubcategoryRepository
import com.kyovo.cents.application.fakes.InMemoryTransactionRepository
import com.kyovo.cents.application.fakes.InMemoryUnitOfWork
import com.kyovo.cents.application.fakes.SequentialSubcategoryIdGenerator
import com.kyovo.cents.application.fakes.SequentialTransactionIdGenerator
import com.kyovo.cents.application.fakes.aCreateSubcategoryCommand
import com.kyovo.cents.application.fakes.aMoney
import com.kyovo.cents.application.fakes.aRecordTransactionCommand
import com.kyovo.cents.application.fakes.aSubcategoryId
import com.kyovo.cents.application.fakes.aTransactionId
import com.kyovo.cents.application.fakes.anAccount
import com.kyovo.cents.application.fakes.anAccountId
import com.kyovo.cents.application.fakes.anUpdateSubcategoryCommand
import com.kyovo.cents.domain.exception.SubcategoryNotFoundException
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.SubcategoryId
import com.kyovo.cents.domain.model.SubcategoryName
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * The subcategory use cases working together, through the real services and in-memory storage: what
 * a user living with their subcategories would go through. Each service has its own tests; these
 * pin what only shows when they meet.
 */
class SubcategoryScenariosTest
{
    private val accountId = anAccountId()
    private val first = aSubcategoryId("11111111-1111-1111-1111-111111111111")
    private val second = aSubcategoryId("22222222-2222-2222-2222-222222222222")

    private val accountRepository = InMemoryAccountRepository()
    private val transactionRepository = InMemoryTransactionRepository()
    private val subcategoryRepository = InMemorySubcategoryRepository()

    private val createSubcategory =
        CreateSubcategoryService(subcategoryRepository, SequentialSubcategoryIdGenerator(listOf(first, second)))
    private val updateSubcategory = UpdateSubcategoryService(subcategoryRepository)
    private val deleteSubcategory =
        DeleteSubcategoryService(subcategoryRepository, transactionRepository, InMemoryUnitOfWork())
    private val recordTransaction = RecordTransactionService(
        accountRepository,
        transactionRepository,
        SequentialTransactionIdGenerator(
            List(4) { aTransactionId("33333333-3333-3333-3333-33333333333$it") },
        ),
        subcategoryRepository,
    )
    private val listTransactions = ListTransactionsService(transactionRepository)
    private val getBalance = GetAccountBalanceService(accountRepository, transactionRepository)

    init
    {
        accountRepository.save(anAccount(id = accountId))
    }

    private fun spend(cents: Long, subcategoryId: SubcategoryId?) =
        recordTransaction.record(
            aRecordTransactionCommand(
                accountId = accountId,
                amount = aMoney(cents),
                category = RecordableTransactionCategory.EXPENSE,
                subcategoryId = subcategoryId,
            ),
        )

    @Test
    fun `deleting a subcategory keeps its transactions listed, uncategorised`() = runTest()
    {
        // GIVEN two expenses in a subcategory, and one with none
        createSubcategory.create(aCreateSubcategoryCommand(name = SubcategoryName("Alimentation")))
        spend(1_000, first)
        spend(2_000, first)
        spend(500, null)

        // WHEN
        deleteSubcategory.delete(first)

        // THEN nothing is found under the deleted subcategory any more, yet no transaction is lost
        assertThat(listTransactions.list(subcategoryId = first)).isEmpty()
        assertThat(listTransactions.list()).hasSize(3)
        assertThat(listTransactions.list().map { it.subcategoryId }).containsOnlyNulls()
    }

    @Test
    fun `deleting a subcategory changes no balance`() = runTest()
    {
        // GIVEN
        createSubcategory.create(aCreateSubcategoryCommand(name = SubcategoryName("Alimentation")))
        spend(1_000, first)
        spend(2_000, null)
        val balanceBefore = getBalance.getBalance(accountId)

        // WHEN
        deleteSubcategory.delete(first)

        // THEN
        assertThat(getBalance.getBalance(accountId)).isEqualTo(balanceBefore)
    }

    @Test
    fun `renaming a subcategory keeps its transactions attached to it`() = runTest()
    {
        // GIVEN
        createSubcategory.create(aCreateSubcategoryCommand(name = SubcategoryName("Alimentation")))
        spend(1_000, first)
        spend(2_000, first)

        // WHEN
        updateSubcategory.update(anUpdateSubcategoryCommand(id = first, name = SubcategoryName("Courses")))

        // THEN they are found under the same subcategory, whatever it is called now
        assertThat(listTransactions.list(subcategoryId = first)).hasSize(2)
    }

    @Test
    fun `a name freed by a deletion can be used again, by a subcategory of its own`() = runTest()
    {
        // GIVEN a transaction recorded under "Alimentation", which is then deleted
        createSubcategory.create(aCreateSubcategoryCommand(name = SubcategoryName("Alimentation")))
        spend(1_000, first)
        deleteSubcategory.delete(first)

        // WHEN the same name is created again
        val again = createSubcategory.create(aCreateSubcategoryCommand(name = SubcategoryName("Alimentation")))

        // THEN it is a new subcategory: the old transaction does not come back under it
        assertThat(again.id).isEqualTo(second)
        assertThat(listTransactions.list(subcategoryId = second)).isEmpty()
        assertThat(listTransactions.list().single().subcategoryId).isNull()
    }

    @Test
    fun `a deleted subcategory can no longer be used to record a transaction`() = runTest()
    {
        // GIVEN
        createSubcategory.create(aCreateSubcategoryCommand(name = SubcategoryName("Alimentation")))
        deleteSubcategory.delete(first)

        // WHEN / THEN
        assertThatThrownBySuspending { spend(1_000, first) }
            .isInstanceOf(SubcategoryNotFoundException::class.java)
        assertThat(transactionRepository.saved).isEmpty()
    }
}
