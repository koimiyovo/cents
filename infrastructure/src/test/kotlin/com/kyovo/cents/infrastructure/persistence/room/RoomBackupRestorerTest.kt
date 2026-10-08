package com.kyovo.cents.infrastructure.persistence.room

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.AccountCurrency
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.AccountName
import com.kyovo.cents.domain.model.AccountType
import com.kyovo.cents.domain.model.AlertThreshold
import com.kyovo.cents.domain.model.BackupSnapshot
import com.kyovo.cents.domain.model.Budget
import com.kyovo.cents.domain.model.BudgetAlert
import com.kyovo.cents.domain.model.BudgetAlertLevel
import com.kyovo.cents.domain.model.BudgetCalendar
import com.kyovo.cents.domain.model.BudgetStartDay
import com.kyovo.cents.domain.model.DefaultSubcategories
import com.kyovo.cents.domain.model.Emoji
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.Project
import com.kyovo.cents.domain.model.ProjectId
import com.kyovo.cents.domain.model.ProjectName
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.RecurrenceFrequency
import com.kyovo.cents.domain.model.RecurringTransaction
import com.kyovo.cents.domain.model.RecurringTransactionId
import com.kyovo.cents.domain.model.Subcategory
import com.kyovo.cents.domain.model.SubcategoryId
import com.kyovo.cents.domain.model.SubcategoryName
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.model.TransactionId
import com.kyovo.cents.domain.model.TransactionTitle
import com.kyovo.cents.infrastructure.persistence.realTime
import kotlinx.coroutines.flow.first
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.Currency
import java.util.UUID

/**
 * Importing puts a [BackupSnapshot] in place of *everything* in the database, in one transaction: all of
 * it or none of it. What the database must see afterwards is exactly the snapshot, whatever was there
 * before (the common subcategories a new file starts with included), through the same repositories the
 * app reads with.
 */
class RoomBackupRestorerTest
{
    private lateinit var persistence: RoomPersistence

    @BeforeEach
    fun open()
    {
        persistence = RoomPersistence.inMemory(BundledSQLiteDriver())
    }

    @AfterEach
    fun close()
    {
        persistence.close()
    }

    private fun uuid(n: Int) = UUID.fromString("00000000-0000-0000-0000-%012d".format(n))

    private fun account(n: Int, name: String, archived: Boolean = false) = Account(
        AccountId(uuid(n)), AccountName(name), AccountType.CHECKING, AccountCurrency(Currency.getInstance("EUR")),
        Instant.parse("2026-01-01T00:00:00.123456789Z"),
        if (archived) Instant.parse("2026-02-01T00:00:00Z") else null
    )

    private val courant = account(1, "Courant")
    private val livret = account(2, "Livret")
    private val vieux = account(3, "Vieux compte", archived = true)

    private val groceries =
        Subcategory(SubcategoryId(uuid(10)), RecordableTransactionCategory.EXPENSE, SubcategoryName("Courses"), Emoji("🛒"))
    private val salary =
        Subcategory(SubcategoryId(uuid(11)), RecordableTransactionCategory.INCOME, SubcategoryName("Paie"), null)
    private val japan = Project(ProjectId(uuid(20)), ProjectName("Japon"), null, Money(300_000), AlertThreshold(60))

    private val expense = Transaction.recorded(
        TransactionId(uuid(30)), courant.id, Money(1_250), TransactionTitle("Marché"),
        RecordableTransactionCategory.EXPENSE, groceries, null, Instant.parse("2026-09-30T12:00:00.5Z"), japan.id
    )
    private val deposit = Transaction.openingDeposit(
        TransactionId(uuid(31)), livret.id, Money(50_000), Instant.parse("2026-01-01T00:00:00Z")
    )
    private val transferOut = Transaction.transferOut(
        TransactionId(uuid(32)), courant.id, Money(10_000), TransactionTitle("Vers Livret"),
        Instant.parse("2026-09-01T00:00:00Z")
    )
    private val transferIn = Transaction.transferIn(
        TransactionId(uuid(33)), livret.id, Money(10_000), TransactionTitle("Depuis Courant"),
        Instant.parse("2026-09-01T00:00:00Z")
    )

    private val rule = RecurringTransaction(
        RecurringTransactionId(uuid(40)), courant.id, RecordableTransactionCategory.INCOME, Money(250_000),
        TransactionTitle("Salaire"), salary.id, null, RecurrenceFrequency.MONTHLY, 1,
        LocalDate.of(2026, 1, 28), null, LocalDate.of(2026, 9, 28)
    )

    // The user's accounts are in this order on purpose: neither alphabetical nor the order of the ids.
    private val everything = BackupSnapshot(
        accounts = listOf(livret, vieux, courant),
        subcategories = listOf(salary, groceries),
        transactions = listOf(expense, deposit, transferOut, transferIn),
        budgets = listOf(
            Budget(groceries.id, YearMonth.of(2026, 9), Money(30_000), AlertThreshold(70)),
            Budget(groceries.id, YearMonth.of(2026, 10), Money(35_000))
        ),
        budgetCalendar = BudgetCalendar(BudgetStartDay(25), setOf(LocalDate.of(2026, 9, 28))),
        recurringTransactions = listOf(rule),
        projects = listOf(japan)
    )

    private val nothing = BackupSnapshot(
        emptyList(), emptyList(), emptyList(), emptyList(), BudgetCalendar(), emptyList(), emptyList()
    )

    private suspend fun readBack() = BackupSnapshot(
        accounts = persistence.accounts.findAll(),
        subcategories = persistence.subcategories.findAll(),
        transactions = persistence.transactions.findAll(),
        budgets = persistence.budgets.observeAll().first(),
        budgetCalendar = persistence.budgetCalendar.observe().first(),
        recurringTransactions = persistence.recurringTransactions.findAll(),
        projects = persistence.projects.findAll()
    )

    @Test
    fun `a snapshot put into a new database is what the database then holds`() = realTime()
    {
        // WHEN
        persistence.backupRestorer.replaceAll(everything)

        // THEN
        assertThat(readBack()).isEqualTo(everything)
    }

    @Test
    fun `the accounts keep the order of the snapshot`() = realTime()
    {
        // WHEN
        persistence.backupRestorer.replaceAll(everything)

        // THEN
        assertThat(persistence.accounts.findAll()).containsExactly(livret, vieux, courant)
    }

    @Test
    fun `the common subcategories a new database starts with are replaced too, not kept`() = realTime()
    {
        // WHEN
        persistence.backupRestorer.replaceAll(everything)

        // THEN
        assertThat(persistence.subcategories.findAll()).containsExactly(salary, groceries)
        assertThat(persistence.subcategories.findAll()).doesNotContainAnyElementsOf(DefaultSubcategories.ALL)
    }

    @Test
    fun `everything that was there before is gone`() = realTime()
    {
        // GIVEN
        val before = account(7, "Avant")
        persistence.accounts.save(before)
        persistence.transactions.save(Transaction.openingDeposit(TransactionId(uuid(70)), before.id, Money(5), Instant.EPOCH))
        persistence.projects.save(Project(ProjectId(uuid(71)), ProjectName("Ancien projet"), null, null))
        persistence.budgetCalendar.saveDefaultStartDay(BudgetStartDay(10))
        persistence.budgetCalendar.saveCycleStart(LocalDate.of(2025, 5, 3))

        // WHEN
        persistence.backupRestorer.replaceAll(everything)

        // THEN
        val now = readBack()
        assertThat(now.accounts).doesNotContain(before)
        assertThat(now.transactions).noneMatch { it.id == TransactionId(uuid(70)) }
        assertThat(now.projects).containsExactly(japan)
        assertThat(now.budgetCalendar).isEqualTo(everything.budgetCalendar)
    }

    @Test
    fun `an empty snapshot empties the database, the calendar back to its default`() = realTime()
    {
        // GIVEN
        persistence.backupRestorer.replaceAll(everything)

        // WHEN
        persistence.backupRestorer.replaceAll(nothing)

        // THEN
        assertThat(readBack()).isEqualTo(nothing)
    }

    @Test
    fun `the budget alerts already reported are forgotten`() = realTime()
    {
        // GIVEN: reported for a subcategory the file does not even hold
        val month = YearMonth.of(2026, 9)
        persistence.backupRestorer.replaceAll(everything)
        persistence.budgetAlerts.record(BudgetAlert(groceries.id, month, BudgetAlertLevel.OVER))

        // WHEN
        persistence.backupRestorer.replaceAll(everything)

        // THEN
        assertThat(persistence.budgetAlerts.findByMonth(month)).isEmpty()
    }

    @Test
    fun `restoring the same snapshot twice gives the same result`() = realTime()
    {
        // WHEN
        persistence.backupRestorer.replaceAll(everything)
        persistence.backupRestorer.replaceAll(everything)

        // THEN
        assertThat(readBack()).isEqualTo(everything)
    }

    @Test
    fun `a snapshot the database refuses leaves everything as it was`() = realTime()
    {
        // GIVEN: data in place, then a snapshot whose transaction points to an account it does not hold
        // (the import checks that first; the database's own keys are the second line of defence)
        persistence.backupRestorer.replaceAll(everything)
        val broken = everything.copy(accounts = listOf(courant), transactions = listOf(expense, deposit))

        // WHEN
        val result = runCatching { persistence.backupRestorer.replaceAll(broken) }

        // THEN
        assertThat(result.isFailure).isTrue()
        assertThat(readBack()).isEqualTo(everything)
    }

    @Test
    fun `a failed restore into a database with its common subcategories keeps them`() = realTime()
    {
        // GIVEN
        val broken = everything.copy(accounts = emptyList())

        // WHEN
        val result = runCatching { persistence.backupRestorer.replaceAll(broken) }

        // THEN
        assertThat(result.isFailure).isTrue()
        assertThat(persistence.subcategories.findAll()).containsExactlyInAnyOrderElementsOf(DefaultSubcategories.ALL)
        assertThat(persistence.accounts.findAll()).isEmpty()
    }

    @Test
    fun `what was restored can be changed afterwards like any other data`() = realTime()
    {
        // GIVEN
        persistence.backupRestorer.replaceAll(everything)

        // WHEN
        persistence.subcategories.deleteById(groceries.id)

        // THEN: the foreign keys are in place again: the transaction is kept, uncategorised
        val kept = persistence.transactions.findById(expense.id)
        assertThat(kept).isNotNull
        assertThat(kept!!.subcategoryId).isNull()
    }
}
