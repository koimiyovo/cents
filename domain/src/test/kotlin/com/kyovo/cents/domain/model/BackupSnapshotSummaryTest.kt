package com.kyovo.cents.domain.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.Currency
import java.util.UUID

/**
 * What a snapshot holds, counted by kind: what an import tells the user it has just restored. The
 * summary only counts the things the user created (accounts, subcategories, transactions, budgets,
 * recurring transactions, projects); the budget calendar's settings are a setting, not data to count.
 */
class BackupSnapshotSummaryTest
{
    private fun uuid(n: Int) = UUID.fromString("00000000-0000-0000-0000-%012d".format(n))

    private val account = Account(
        AccountId(uuid(1)), AccountName("Courant"), AccountType.CHECKING,
        AccountCurrency(Currency.getInstance("EUR")), Instant.parse("2026-01-01T00:00:00Z")
    )
    private val subcategory =
        Subcategory(SubcategoryId(uuid(2)), RecordableTransactionCategory.EXPENSE, SubcategoryName("Courses"), null)
    private val transaction = Transaction.openingDeposit(
        TransactionId(uuid(3)), account.id, Money(1_000), Instant.parse("2026-01-01T00:00:00Z")
    )
    private val budget = Budget(subcategory.id, YearMonth.of(2026, 9), Money(30_000))
    private val rule = RecurringTransaction(
        RecurringTransactionId(uuid(4)), account.id, RecordableTransactionCategory.EXPENSE, Money(75_000),
        TransactionTitle("Loyer"), null, null, RecurrenceFrequency.MONTHLY, 1, LocalDate.of(2026, 1, 5)
    )
    private val project = Project(ProjectId(uuid(5)), ProjectName("Japon"), null, null)

    private val nothing = BackupSnapshot(
        emptyList(), emptyList(), emptyList(), emptyList(), BudgetCalendar(), emptyList(), emptyList()
    )

    @Test
    fun `an empty snapshot is summarised by zeros, and is empty`()
    {
        val summary = nothing.summary()

        assertThat(summary).isEqualTo(BackupSummary(0, 0, 0, 0, 0, 0))
        assertThat(summary.isEmpty).isTrue()
    }

    @Test
    fun `each kind is counted on its own`()
    {
        val snapshot = nothing.copy(
            accounts = listOf(account),
            subcategories = listOf(subcategory, subcategory.copy(id = SubcategoryId(uuid(6)))),
            transactions = listOf(transaction, transaction, transaction),
            budgets = listOf(budget),
            recurringTransactions = listOf(rule, rule, rule, rule),
            projects = listOf(project, project, project, project, project)
        )

        assertThat(snapshot.summary()).isEqualTo(
            BackupSummary(
                accounts = 1,
                subcategories = 2,
                transactions = 3,
                budgets = 1,
                recurringTransactions = 4,
                projects = 5
            )
        )
    }

    @Test
    fun `a summary with anything in it is not empty, whichever kind it is`()
    {
        val others = BackupSummary(0, 0, 0, 0, 0, 0)

        assertThat(others.copy(accounts = 1).isEmpty).isFalse()
        assertThat(others.copy(subcategories = 1).isEmpty).isFalse()
        assertThat(others.copy(transactions = 1).isEmpty).isFalse()
        assertThat(others.copy(budgets = 1).isEmpty).isFalse()
        assertThat(others.copy(recurringTransactions = 1).isEmpty).isFalse()
        assertThat(others.copy(projects = 1).isEmpty).isFalse()
    }

    @Test
    fun `the budget calendar settings are not counted as data`()
    {
        val customised = nothing.copy(
            budgetCalendar = BudgetCalendar(BudgetStartDay(25), setOf(LocalDate.of(2026, 9, 28)))
        )

        assertThat(customised.summary().isEmpty).isTrue()
    }

    @Test
    fun `summarising does not change the snapshot`()
    {
        val snapshot = nothing.copy(accounts = listOf(account), transactions = listOf(transaction))

        snapshot.summary()

        assertThat(snapshot).isEqualTo(nothing.copy(accounts = listOf(account), transactions = listOf(transaction)))
    }
}
