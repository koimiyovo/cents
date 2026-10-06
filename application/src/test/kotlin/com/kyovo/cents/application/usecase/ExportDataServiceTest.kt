package com.kyovo.cents.application.usecase

import com.kyovo.cents.application.fakes.InMemoryAccountRepository
import com.kyovo.cents.application.fakes.InMemoryBudgetCalendarRepository
import com.kyovo.cents.application.fakes.InMemoryBudgetRepository
import com.kyovo.cents.application.fakes.InMemoryProjectRepository
import com.kyovo.cents.application.fakes.InMemoryRecurringTransactionRepository
import com.kyovo.cents.application.fakes.InMemorySubcategoryRepository
import com.kyovo.cents.application.fakes.InMemoryTransactionRepository
import com.kyovo.cents.application.fakes.InMemoryUnitOfWork
import com.kyovo.cents.application.fakes.RecordingBackupSerializer
import com.kyovo.cents.application.fakes.aBudget
import com.kyovo.cents.application.fakes.aProject
import com.kyovo.cents.application.fakes.aRecurringTransaction
import com.kyovo.cents.application.fakes.aSubcategory
import com.kyovo.cents.application.fakes.aSubcategoryId
import com.kyovo.cents.application.fakes.aTransaction
import com.kyovo.cents.application.fakes.aTransactionId
import com.kyovo.cents.application.fakes.anAccount
import com.kyovo.cents.application.fakes.anAccountId
import com.kyovo.cents.application.fakes.anInstant
import com.kyovo.cents.application.fakes.assertThatThrownBySuspending
import com.kyovo.cents.domain.model.AccountName
import com.kyovo.cents.domain.model.BackupSnapshot
import com.kyovo.cents.domain.model.BudgetCalendar
import com.kyovo.cents.domain.model.BudgetStartDay
import com.kyovo.cents.domain.model.TransactionCategory
import com.kyovo.cents.domain.port.output.UnitOfWork
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * Exporting reads everything the user owns and hands it to the serializer as one [BackupSnapshot]; the
 * text the serializer gives back is the answer. It writes nothing.
 */
class ExportDataServiceTest
{
    private val accounts = InMemoryAccountRepository()
    private val subcategories = InMemorySubcategoryRepository()
    private val transactions = InMemoryTransactionRepository()
    private val budgets = InMemoryBudgetRepository()
    private val budgetCalendar = InMemoryBudgetCalendarRepository()
    private val recurringTransactions = InMemoryRecurringTransactionRepository()
    private val projects = InMemoryProjectRepository()
    private val unitOfWork = InMemoryUnitOfWork()
    private val serializer = RecordingBackupSerializer("the file")

    private fun service(unitOfWork: UnitOfWork = this.unitOfWork) = ExportDataService(
        accounts,
        subcategories,
        transactions,
        budgets,
        budgetCalendar,
        recurringTransactions,
        projects,
        unitOfWork,
        serializer
    )

    private fun anAccountNumbered(n: Int, name: String) = anAccount(
        id = anAccountId("11111111-1111-1111-1111-11111111111$n"),
        name = AccountName(name)
    )

    @Test
    fun `an app with nothing in it exports an empty snapshot`() = runTest()
    {
        // WHEN
        service().export()

        // THEN
        assertThat(serializer.written.single()).isEqualTo(
            BackupSnapshot(
                accounts = emptyList(),
                subcategories = emptyList(),
                transactions = emptyList(),
                budgets = emptyList(),
                budgetCalendar = BudgetCalendar(),
                recurringTransactions = emptyList(),
                projects = emptyList()
            )
        )
    }

    @Test
    fun `answers with the text the serializer wrote`() = runTest()
    {
        assertThat(service().export()).isEqualTo("the file")
    }

    @Test
    fun `the snapshot holds every kind of data`() = runTest()
    {
        // GIVEN
        val account = anAccountNumbered(1, "Courant")
        val subcategory = aSubcategory()
        val transaction = aTransaction(
            category = TransactionCategory.EXPENSE,
            accountId = account.id,
            subcategoryId = subcategory.id
        )
        val budget = aBudget(subcategory.id)
        val rule = aRecurringTransaction(accountId = account.id)
        val project = aProject()
        accounts.save(account)
        subcategories.save(subcategory)
        transactions.save(transaction)
        budgets.save(budget)
        recurringTransactions.save(rule)
        projects.save(project)

        // WHEN
        service().export()

        // THEN
        val snapshot = serializer.written.single()
        assertThat(snapshot.accounts).containsExactly(account)
        assertThat(snapshot.subcategories).containsExactly(subcategory)
        assertThat(snapshot.transactions).containsExactly(transaction)
        assertThat(snapshot.budgets).containsExactly(budget)
        assertThat(snapshot.recurringTransactions).containsExactly(rule)
        assertThat(snapshot.projects).containsExactly(project)
    }

    @Test
    fun `the accounts come in the order the user gave them`() = runTest()
    {
        // GIVEN
        val first = anAccountNumbered(1, "Courant")
        val second = anAccountNumbered(2, "Livret")
        val third = anAccountNumbered(3, "Espèces")
        accounts.save(first)
        accounts.save(second)
        accounts.save(third)
        accounts.reorder(listOf(third.id, first.id, second.id))

        // WHEN
        service().export()

        // THEN
        assertThat(serializer.written.single().accounts).containsExactly(third, first, second)
    }

    @Test
    fun `archived accounts and their history are exported too`() = runTest()
    {
        // GIVEN
        val closed = anAccount(archivedAt = anInstant("2026-01-01T00:00:00Z"))
        val deposit = aTransaction(id = aTransactionId(), accountId = closed.id)
        accounts.save(closed)
        transactions.save(deposit)

        // WHEN
        service().export()

        // THEN
        val snapshot = serializer.written.single()
        assertThat(snapshot.accounts).containsExactly(closed)
        assertThat(snapshot.transactions).containsExactly(deposit)
    }

    @Test
    fun `the transactions of every account are exported, transfer legs included`() = runTest()
    {
        // GIVEN
        val outgoing = aTransaction(
            id = aTransactionId("33333333-3333-3333-3333-333333333331"),
            category = TransactionCategory.TRANSFER_OUT
        )
        val incoming = aTransaction(
            id = aTransactionId("33333333-3333-3333-3333-333333333332"),
            accountId = anAccountId("11111111-1111-1111-1111-111111111112"),
            category = TransactionCategory.TRANSFER_IN
        )
        transactions.save(outgoing)
        transactions.save(incoming)

        // WHEN
        service().export()

        // THEN
        assertThat(serializer.written.single().transactions).containsExactly(outgoing, incoming)
    }

    @Test
    fun `every month of every budget is exported`() = runTest()
    {
        // GIVEN
        val subcategoryId = aSubcategoryId()
        val september = aBudget(subcategoryId, YearMonth.of(2026, 9))
        val october = aBudget(subcategoryId, YearMonth.of(2026, 10))
        budgets.save(september)
        budgets.save(october)

        // WHEN
        service().export()

        // THEN
        assertThat(serializer.written.single().budgets).containsExactly(september, october)
    }

    @Test
    fun `the budget calendar is exported with its usual day and its declared starts`() = runTest()
    {
        // GIVEN
        budgetCalendar.saveDefaultStartDay(BudgetStartDay(25))
        budgetCalendar.saveCycleStart(LocalDate.of(2026, 9, 28))

        // WHEN
        service().export()

        // THEN
        assertThat(serializer.written.single().budgetCalendar).isEqualTo(
            BudgetCalendar(BudgetStartDay(25), setOf(LocalDate.of(2026, 9, 28)))
        )
    }

    @Test
    fun `everything is read in one unit of work, so the snapshot is one moment of the data`() = runTest()
    {
        // WHEN
        service().export()

        // THEN
        assertThat(unitOfWork.executionCount).isEqualTo(1)
    }

    @Test
    fun `exporting changes nothing`() = runTest()
    {
        // GIVEN
        val account = anAccountNumbered(1, "Courant")
        val transaction = aTransaction(accountId = account.id)
        accounts.save(account)
        transactions.save(transaction)

        // WHEN
        service().export()

        // THEN
        assertThat(accounts.saved).containsExactly(account)
        assertThat(transactions.findAll()).containsExactly(transaction)
    }

    @Test
    fun `exporting twice gives two identical snapshots`() = runTest()
    {
        // GIVEN
        accounts.save(anAccountNumbered(1, "Courant"))

        // WHEN
        service().export()
        service().export()

        // THEN
        assertThat(serializer.written).hasSize(2)
        assertThat(serializer.written[0]).isEqualTo(serializer.written[1])
    }

    @Test
    fun `a failure while reading is let through and nothing is written`() = runTest()
    {
        // GIVEN
        val failing = object : UnitOfWork
        {
            override suspend fun <T> execute(block: suspend () -> T): T
            {
                throw IllegalStateException("the database is gone")
            }
        }

        // WHEN / THEN
        assertThatThrownBySuspending { service(failing).export() }
            .isInstanceOf(IllegalStateException::class.java)
        assertThat(serializer.written).isEmpty()
    }
}
