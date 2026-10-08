package com.kyovo.cents.domain.model

import com.kyovo.cents.domain.exception.InvalidBackupException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.Currency
import java.util.UUID

/**
 * Reading a file only proves each piece is valid on its own. Before anything replaces the user's data, the
 * pieces must also agree with each other: the same rules the app enforces one write at a time (a transaction
 * lives on an account that exists, a name is not taken twice...) and the database enforces with its keys.
 * A snapshot that breaks one is refused whole with an [InvalidBackupException], by
 * [BackupSnapshot.requireConsistent].
 */
class BackupSnapshotConsistencyTest
{
    private fun uuid(n: Int) = UUID.fromString("00000000-0000-0000-0000-%012d".format(n))

    private fun account(n: Int, name: String, archived: Boolean = false) = Account(
        id = AccountId(uuid(n)),
        name = AccountName(name),
        type = AccountType.CHECKING,
        currency = AccountCurrency(Currency.getInstance("EUR")),
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        archivedAt = if (archived) Instant.parse("2026-02-01T00:00:00Z") else null
    )

    private fun subcategory(
        n: Int,
        name: String,
        kind: RecordableTransactionCategory = RecordableTransactionCategory.EXPENSE
    ) = Subcategory(SubcategoryId(uuid(100 + n)), kind, SubcategoryName(name), emoji = null)

    private fun project(n: Int, name: String) =
        Project(ProjectId(uuid(200 + n)), ProjectName(name), null, null)

    private fun transaction(
        n: Int,
        account: Account,
        category: RecordableTransactionCategory = RecordableTransactionCategory.EXPENSE,
        subcategory: Subcategory? = null,
        project: Project? = null
    ) = Transaction.recorded(
        TransactionId(uuid(300 + n)), account.id, Money(1_000), TransactionTitle("Achat"), category,
        subcategory, null, Instant.parse("2026-03-01T00:00:00Z"), project?.id
    )

    private fun rule(
        n: Int,
        account: Account,
        category: RecordableTransactionCategory = RecordableTransactionCategory.EXPENSE,
        subcategory: Subcategory? = null
    ) = RecurringTransaction(
        id = RecurringTransactionId(uuid(400 + n)),
        accountId = account.id,
        category = category,
        amount = Money(75_000),
        title = TransactionTitle("Loyer"),
        subcategoryId = subcategory?.id,
        description = null,
        frequency = RecurrenceFrequency.MONTHLY,
        startDate = LocalDate.of(2026, 1, 5)
    )

    private fun snapshot(
        accounts: List<Account> = emptyList(),
        subcategories: List<Subcategory> = emptyList(),
        transactions: List<Transaction> = emptyList(),
        budgets: List<Budget> = emptyList(),
        recurringTransactions: List<RecurringTransaction> = emptyList(),
        projects: List<Project> = emptyList()
    ) = BackupSnapshot(
        accounts,
        subcategories,
        transactions,
        budgets,
        BudgetCalendar(),
        recurringTransactions,
        projects
    )

    private fun assertRefused(snapshot: BackupSnapshot)
    {
        assertThatThrownBy { snapshot.requireConsistent() }.isInstanceOf(InvalidBackupException::class.java)
    }

    private val current = account(1, "Courant")
    private val groceries = subcategory(1, "Alimentation")
    private val salary = subcategory(2, "Salaire", RecordableTransactionCategory.INCOME)
    private val japan = project(1, "Voyage au Japon")

    // ---- what is accepted

    @Test
    fun `an empty snapshot is consistent`()
    {
        assertThatCode { snapshot().requireConsistent() }.doesNotThrowAnyException()
    }

    @Test
    fun `a snapshot whose pieces agree is consistent`()
    {
        val whole = snapshot(
            accounts = listOf(current, account(2, "Livret", archived = true)),
            subcategories = listOf(groceries, salary),
            transactions = listOf(
                transaction(1, current, subcategory = groceries, project = japan),
                transaction(2, current, RecordableTransactionCategory.INCOME, salary),
                transaction(3, current)
            ),
            budgets = listOf(Budget(groceries.id, YearMonth.of(2026, 10), Money(30_000))),
            recurringTransactions = listOf(rule(1, current, subcategory = groceries)),
            projects = listOf(japan)
        )

        assertThatCode { whole.requireConsistent() }.doesNotThrowAnyException()
    }

    @Test
    fun `the same name is fine for an active and an archived account, and for two archived ones`()
    {
        assertThatCode {
            snapshot(
                accounts = listOf(
                    account(1, "Livret"),
                    account(2, "Livret", archived = true),
                    account(3, "Livret", archived = true)
                )
            ).requireConsistent()
        }.doesNotThrowAnyException()
    }

    @Test
    fun `the same subcategory name is fine under the two kinds`()
    {
        assertThatCode {
            snapshot(
                subcategories = listOf(
                    subcategory(1, "Autre"),
                    subcategory(2, "Autre", RecordableTransactionCategory.INCOME)
                )
            ).requireConsistent()
        }.doesNotThrowAnyException()
    }

    @Test
    fun `a transaction of zero already stored does not make the backup refused`()
    {
        // The domain refuses to record one now, but a database that holds one must be exportable and back.
        val old = Transaction.restored(
            TransactionId(uuid(350)), current.id, Money(0), TransactionTitle("Ancien"),
            TransactionCategory.EXPENSE, null, null, Instant.parse("2024-01-01T00:00:00Z"), null
        )

        assertThatCode {
            snapshot(
                accounts = listOf(current),
                transactions = listOf(old)
            ).requireConsistent()
        }
            .doesNotThrowAnyException()
    }

    // ---- ids that appear twice

    @Test
    fun `two accounts with the same id are refused`()
    {
        assertRefused(snapshot(accounts = listOf(current, account(1, "Autre nom"))))
    }

    @Test
    fun `two subcategories with the same id are refused`()
    {
        assertRefused(snapshot(subcategories = listOf(groceries, subcategory(1, "Transport"))))
    }

    @Test
    fun `two transactions with the same id are refused`()
    {
        assertRefused(
            snapshot(
                accounts = listOf(current),
                transactions = listOf(transaction(1, current), transaction(1, current))
            )
        )
    }

    @Test
    fun `two recurring transactions with the same id are refused`()
    {
        assertRefused(
            snapshot(
                accounts = listOf(current),
                recurringTransactions = listOf(rule(1, current), rule(1, current))
            )
        )
    }

    @Test
    fun `two projects with the same id are refused`()
    {
        assertRefused(snapshot(projects = listOf(japan, project(1, "Autre projet"))))
    }

    @Test
    fun `two budgets for the same subcategory and month are refused`()
    {
        val september = YearMonth.of(2026, 9)

        assertRefused(
            snapshot(
                subcategories = listOf(groceries),
                budgets = listOf(
                    Budget(groceries.id, september, Money(30_000)),
                    Budget(groceries.id, september, Money(40_000))
                )
            )
        )
    }

    @Test
    fun `one subcategory may have a budget for several months`()
    {
        assertThatCode {
            snapshot(
                subcategories = listOf(groceries),
                budgets = listOf(
                    Budget(groceries.id, YearMonth.of(2026, 9), Money(30_000)),
                    Budget(groceries.id, YearMonth.of(2026, 10), Money(40_000))
                )
            ).requireConsistent()
        }.doesNotThrowAnyException()
    }

    // ---- names that are taken

    @Test
    fun `two active accounts with the same name are refused, whatever the case`()
    {
        assertRefused(snapshot(accounts = listOf(account(1, "Livret"), account(2, "LIVRET"))))
    }

    @Test
    fun `two subcategories of the same kind with the same name are refused, accents and case ignored`()
    {
        assertRefused(
            snapshot(
                subcategories = listOf(
                    subcategory(1, "Education"),
                    subcategory(2, "Éducation")
                )
            )
        )
    }

    @Test
    fun `two projects with the same name are refused, accents and case ignored`()
    {
        assertRefused(snapshot(projects = listOf(project(1, "Été 2027"), project(2, "ete 2027"))))
    }

    // ---- one opening deposit per account

    private fun deposit(n: Int, account: Account) = Transaction.openingDeposit(
        TransactionId(uuid(500 + n)),
        account.id,
        Money(5_000),
        Instant.parse("2026-01-01T00:00:00Z")
    )

    @Test
    fun `an account with two opening deposits is refused`()
    {
        assertRefused(
            snapshot(
                accounts = listOf(current),
                transactions = listOf(deposit(1, current), deposit(2, current))
            )
        )
    }

    @Test
    fun `each account may have its own opening deposit`()
    {
        val savings = account(2, "Livret")

        assertThatCode {
            snapshot(
                accounts = listOf(current, savings),
                transactions = listOf(deposit(1, current), deposit(2, savings))
            ).requireConsistent()
        }.doesNotThrowAnyException()
    }

    // ---- references that lead nowhere

    @Test
    fun `a transaction on an account that is not in the file is refused`()
    {
        assertRefused(
            snapshot(
                accounts = listOf(account(2, "Livret")),
                transactions = listOf(transaction(1, current))
            )
        )
    }

    @Test
    fun `a transaction with a subcategory that is not in the file is refused`()
    {
        assertRefused(
            snapshot(
                accounts = listOf(current),
                transactions = listOf(transaction(1, current, subcategory = groceries))
            )
        )
    }

    @Test
    fun `a transaction with a project that is not in the file is refused`()
    {
        assertRefused(
            snapshot(
                accounts = listOf(current),
                transactions = listOf(transaction(1, current, project = japan))
            )
        )
    }

    @Test
    fun `a transaction whose subcategory is of the other kind is refused`()
    {
        // An expense filed under an income subcategory: recording one is refused, so a file may not hold one.
        val misfiled = Transaction.restored(
            TransactionId(uuid(360)),
            current.id,
            Money(1_000),
            TransactionTitle("Achat"),
            TransactionCategory.EXPENSE,
            salary.id,
            null,
            Instant.parse("2026-03-01T00:00:00Z"),
            null
        )

        assertRefused(
            snapshot(
                accounts = listOf(current),
                subcategories = listOf(salary),
                transactions = listOf(misfiled)
            )
        )
    }

    @Test
    fun `a budget on a subcategory that is not in the file is refused`()
    {
        assertRefused(
            snapshot(
                budgets = listOf(
                    Budget(
                        groceries.id,
                        YearMonth.of(2026, 9),
                        Money(30_000)
                    )
                )
            )
        )
    }

    @Test
    fun `a budget on an income subcategory is refused`()
    {
        assertRefused(
            snapshot(
                subcategories = listOf(salary),
                budgets = listOf(Budget(salary.id, YearMonth.of(2026, 9), Money(30_000)))
            )
        )
    }

    @Test
    fun `a recurring transaction on an account that is not in the file is refused`()
    {
        assertRefused(snapshot(recurringTransactions = listOf(rule(1, current))))
    }

    @Test
    fun `a recurring transaction with a subcategory that is not in the file is refused`()
    {
        assertRefused(
            snapshot(
                accounts = listOf(current),
                recurringTransactions = listOf(rule(1, current, subcategory = groceries))
            )
        )
    }

    @Test
    fun `a recurring transaction whose subcategory is of the other kind is refused`()
    {
        assertRefused(
            snapshot(
                accounts = listOf(current),
                subcategories = listOf(salary),
                recurringTransactions = listOf(
                    rule(
                        1,
                        current,
                        RecordableTransactionCategory.EXPENSE,
                        salary
                    )
                )
            )
        )
    }

    @Test
    fun `checking does not change the snapshot`()
    {
        val whole =
            snapshot(accounts = listOf(current), transactions = listOf(transaction(1, current)))

        whole.requireConsistent()

        assertThat(whole).isEqualTo(
            snapshot(
                accounts = listOf(current),
                transactions = listOf(transaction(1, current))
            )
        )
    }
}
