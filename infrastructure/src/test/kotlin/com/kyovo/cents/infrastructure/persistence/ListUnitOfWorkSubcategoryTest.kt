package com.kyovo.cents.infrastructure.persistence

import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.Subcategory
import com.kyovo.cents.domain.model.SubcategoryId
import com.kyovo.cents.domain.model.SubcategoryName
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.model.TransactionId
import com.kyovo.cents.domain.model.TransactionTitle
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/**
 * Deleting a subcategory writes to two repositories — the transactions that used it lose it, then
 * the subcategory goes — and the two must go together or not at all. `DeleteSubcategoryService`
 * relies on the unit of work for that, so the unit of work has to cover the subcategory repository
 * too: with the real list-based adapter, a failure halfway must leave everything as it was.
 *
 * Expected: `ListUnitOfWork(accountRepository, transactionRepository, subcategoryRepository)`, the
 * subcategory repository being a `ListSubcategoryRepository` that can be snapshotted and restored
 * like the other two (internally, as they are).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ListUnitOfWorkSubcategoryTest
{
    private val accountRepository = ListAccountRepository()
    private val transactionRepository = ListTransactionRepository()
    private val subcategoryRepository = ListSubcategoryRepository()
    private val unitOfWork = ListUnitOfWork(accountRepository, transactionRepository, subcategoryRepository)

    private val accountId = AccountId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
    private val moment = Instant.parse("2026-09-22T10:00:00Z")

    private val groceries = aSubcategory(1, "Alimentation")
    private val transport = aSubcategory(2, "Transport")

    private fun aSubcategory(suffix: Int, name: String) = Subcategory(
        SubcategoryId(UUID.fromString("55555555-5555-5555-5555-55555555555$suffix")),
        RecordableTransactionCategory.EXPENSE,
        SubcategoryName(name),
        null,
    )

    private fun anExpense(suffix: Int, subcategory: Subcategory?) = Transaction.recorded(
        id = TransactionId(UUID.fromString("33333333-3333-3333-3333-33333333333$suffix")),
        accountId = accountId,
        amount = Money(1_000),
        title = TransactionTitle("Courses"),
        category = RecordableTransactionCategory.EXPENSE,
        subcategory = subcategory,
        description = null,
        date = moment,
    )

    private suspend fun failing(block: suspend () -> Unit)
    {
        assertThatThrownBySuspending { unitOfWork.execute { block(); throw RuntimeException("boom") } }
            .isInstanceOf(RuntimeException::class.java)
    }

    @Test
    fun `keeps the subcategory writes of a block that completes`() = runTest()
    {
        // WHEN
        unitOfWork.execute {
            subcategoryRepository.save(groceries)
            subcategoryRepository.save(transport)
        }

        // THEN
        assertThat(subcategoryRepository.findAll()).containsExactly(groceries, transport)
    }

    @Test
    fun `rolls back a subcategory created inside a block that throws`() = runTest()
    {
        // WHEN
        failing { subcategoryRepository.save(groceries) }

        // THEN
        assertThat(subcategoryRepository.findAll()).isEmpty()
    }

    @Test
    fun `rolls back a subcategory deleted inside a block that throws, at the place it was`() = runTest()
    {
        // GIVEN
        subcategoryRepository.save(groceries)
        subcategoryRepository.save(transport)

        // WHEN
        failing { subcategoryRepository.deleteById(groceries.id) }

        // THEN it is back, and still first
        assertThat(subcategoryRepository.findAll()).containsExactly(groceries, transport)
    }

    @Test
    fun `rolls back a subcategory renamed inside a block that throws`() = runTest()
    {
        // GIVEN
        subcategoryRepository.save(groceries)

        // WHEN
        failing { subcategoryRepository.save(groceries.copy(name = SubcategoryName("Courses"))) }

        // THEN
        assertThat(subcategoryRepository.findById(groceries.id)).isEqualTo(groceries)
    }

    @Test
    fun `does not roll back subcategories saved before the failing block started`() = runTest()
    {
        // GIVEN
        subcategoryRepository.save(groceries)

        // WHEN
        failing { subcategoryRepository.save(transport) }

        // THEN
        assertThat(subcategoryRepository.findAll()).containsExactly(groceries)
    }

    @Test
    fun `rolls back a transaction changed inside a block that throws`() = runTest()
    {
        // GIVEN
        val original = anExpense(1, groceries)
        transactionRepository.save(original)

        // WHEN
        failing { transactionRepository.save(original.withoutSubcategory()) }

        // THEN
        assertThat(transactionRepository.findById(original.id)).isEqualTo(original)
    }

    // The very sequence of DeleteSubcategoryService: strip the transactions, then delete the
    // subcategory — and something fails before the end.
    @Test
    fun `a deletion that fails halfway leaves the subcategory and its transactions untouched`() = runTest()
    {
        // GIVEN a subcategory used by two transactions
        subcategoryRepository.save(groceries)
        val first = anExpense(1, groceries)
        val second = anExpense(2, groceries)
        transactionRepository.save(first)
        transactionRepository.save(second)

        // WHEN the first transaction is stripped, and it fails before the second one
        failing {
            transactionRepository.save(first.withoutSubcategory())
        }

        // THEN nothing changed: the subcategory is there, both transactions still use it
        assertThat(subcategoryRepository.findAll()).containsExactly(groceries)
        assertThat(transactionRepository.findById(first.id)).isEqualTo(first)
        assertThat(transactionRepository.findById(second.id)).isEqualTo(second)
    }

    @Test
    fun `a deletion that completes is kept, transactions and subcategory together`() = runTest()
    {
        // GIVEN
        subcategoryRepository.save(groceries)
        val first = anExpense(1, groceries)
        transactionRepository.save(first)

        // WHEN
        unitOfWork.execute {
            transactionRepository.save(first.withoutSubcategory())
            subcategoryRepository.deleteById(groceries.id)
        }

        // THEN
        assertThat(subcategoryRepository.findAll()).isEmpty()
        assertThat(transactionRepository.findById(first.id)).isEqualTo(first.withoutSubcategory())
    }

    @Test
    fun `an exception is rethrown as it was, whatever the repository that was written`() = runTest()
    {
        // WHEN / THEN
        assertThatThrownBySuspending {
            unitOfWork.execute {
                subcategoryRepository.save(groceries)
                throw IllegalStateException("specific")
            }
        }.isInstanceOf(IllegalStateException::class.java).hasMessage("specific")
    }

    // A screen collecting the subcategories must not be left showing what a failed deletion had
    // removed: the rollback restores the repository *and* tells its observers.
    @Test
    fun `a rollback is seen by the observers of the subcategories`() = runTest()
    {
        // GIVEN a screen collecting the subcategories
        subcategoryRepository.save(groceries)
        subcategoryRepository.save(transport)
        val seen = mutableListOf<List<String>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler))
        {
            subcategoryRepository.observeAll().collect { list -> seen += list.map { it.name.value } }
        }

        // WHEN a deletion fails halfway
        failing { subcategoryRepository.deleteById(groceries.id) }

        // THEN the observer saw the deletion, then the restored list
        assertThat(seen.last()).containsExactly("Alimentation", "Transport")
        assertThat(seen).contains(listOf("Transport"))
    }

    @Test
    fun `observers see nothing when the block completes and changes nothing`() = runTest()
    {
        // GIVEN
        subcategoryRepository.save(groceries)
        val seen = mutableListOf<List<String>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler))
        {
            subcategoryRepository.observeAll().collect { list -> seen += list.map { it.name.value } }
        }

        // WHEN
        unitOfWork.execute { }

        // THEN
        assertThat(seen).containsExactly(listOf("Alimentation"))
    }
}
