package com.kyovo.cents.infrastructure.backup

import com.kyovo.cents.domain.exception.InvalidBackupException
import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.AccountCurrency
import com.kyovo.cents.domain.model.AccountDescription
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.AccountName
import com.kyovo.cents.domain.model.AccountType
import com.kyovo.cents.domain.model.AlertThreshold
import com.kyovo.cents.domain.model.BackupSnapshot
import com.kyovo.cents.domain.model.Budget
import com.kyovo.cents.domain.model.BudgetCalendar
import com.kyovo.cents.domain.model.BudgetStartDay
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
import com.kyovo.cents.domain.model.TransactionCategory
import com.kyovo.cents.domain.model.TransactionDescription
import com.kyovo.cents.domain.model.TransactionId
import com.kyovo.cents.domain.model.TransactionTitle
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.Currency
import java.util.UUID

/**
 * The backup file is a JSON text, and the adapter turns a [BackupSnapshot] into it and back. What matters
 * is that nothing is lost on the way (every field, every kind of value) and that a file that cannot be
 * trusted is refused with an [InvalidBackupException] rather than half read.
 *
 * Whether the pieces of a snapshot agree with each other (a transaction pointing to an account that is not
 * in the file...) is not this adapter's concern: it is the import's.
 */
class JsonBackupSerializerTest
{
    private val serializer = JsonBackupSerializer()

    private fun uuid(n: Int) = UUID.fromString("00000000-0000-0000-0000-%012d".format(n))

    private val checking = Account(
        id = AccountId(uuid(1)),
        name = AccountName("Compte courant"),
        type = AccountType.CHECKING,
        currency = AccountCurrency(Currency.getInstance("EUR")),
        createdAt = Instant.parse("2026-01-15T08:30:00Z"),
        description = AccountDescription.of("Celui de la banque")
    )

    // Nanoseconds on purpose: an instant that changed by being saved would no longer equal itself.
    private val closedYen = Account(
        id = AccountId(uuid(2)),
        name = AccountName("Voyage"),
        type = AccountType.CASH,
        currency = AccountCurrency(Currency.getInstance("JPY")),
        createdAt = Instant.parse("2025-03-01T10:00:00.123456789Z"),
        archivedAt = Instant.parse("2025-04-01T09:15:30.987654321Z")
    )

    private val groceries = Subcategory(
        SubcategoryId(uuid(10)),
        RecordableTransactionCategory.EXPENSE,
        SubcategoryName("Alimentation"),
        Emoji("🛒")
    )

    private val salary = Subcategory(
        SubcategoryId(uuid(11)),
        RecordableTransactionCategory.INCOME,
        SubcategoryName("Salaire"),
        emoji = null
    )

    private val japan = Project(ProjectId(uuid(20)), ProjectName("Voyage au Japon"), Emoji("✈️"), Money(300_000))

    private val expense = Transaction.recorded(
        id = TransactionId(uuid(30)),
        accountId = checking.id,
        amount = Money(1_250),
        title = TransactionTitle("Courses"),
        category = RecordableTransactionCategory.EXPENSE,
        subcategory = groceries,
        description = TransactionDescription.of("Marché"),
        date = Instant.parse("2026-09-30T12:00:00.5Z"),
        projectId = japan.id
    )

    private val income = Transaction.recorded(
        TransactionId(uuid(31)),
        checking.id,
        Money(250_000),
        TransactionTitle("Paie"),
        RecordableTransactionCategory.INCOME,
        salary,
        description = null,
        date = Instant.parse("2026-09-28T07:00:00Z")
    )

    private val deposit = Transaction.openingDeposit(
        TransactionId(uuid(32)),
        closedYen.id,
        Money(50_000),
        Instant.parse("2025-03-01T10:00:00.123456789Z")
    )

    private val transferOut = Transaction.transferOut(
        TransactionId(uuid(33)), checking.id, Money(10_000), TransactionTitle("Vers Voyage"),
        Instant.parse("2026-09-01T00:00:00Z")
    )

    private val transferIn = Transaction.transferIn(
        TransactionId(uuid(34)), closedYen.id, Money(10_000), TransactionTitle("Depuis Courant"),
        Instant.parse("2026-09-01T00:00:00Z")
    )

    private val groceriesBudget = Budget(groceries.id, YearMonth.of(2026, 10), Money(30_000), AlertThreshold(60))

    private val rent = RecurringTransaction(
        id = RecurringTransactionId(uuid(40)),
        accountId = checking.id,
        category = RecordableTransactionCategory.EXPENSE,
        amount = Money(75_000),
        title = TransactionTitle("Loyer"),
        subcategoryId = groceries.id,
        description = TransactionDescription.of("Chaque mois"),
        frequency = RecurrenceFrequency.MONTHLY,
        interval = 3,
        startDate = LocalDate.of(2026, 1, 31),
        endDate = LocalDate.of(2027, 12, 31),
        lastGeneratedDate = LocalDate.of(2026, 7, 31)
    )

    private val pay = RecurringTransaction(
        id = RecurringTransactionId(uuid(41)),
        accountId = checking.id,
        category = RecordableTransactionCategory.INCOME,
        amount = Money(250_000),
        title = TransactionTitle("Salaire"),
        subcategoryId = null,
        description = null,
        frequency = RecurrenceFrequency.WEEKLY,
        startDate = LocalDate.of(2026, 9, 1)
    )

    private val everything = BackupSnapshot(
        accounts = listOf(closedYen, checking),
        subcategories = listOf(salary, groceries),
        transactions = listOf(expense, income, deposit, transferOut, transferIn),
        budgets = listOf(groceriesBudget, Budget(groceries.id, YearMonth.of(2026, 11), Money(35_000))),
        budgetCalendar = BudgetCalendar(
            BudgetStartDay(25),
            setOf(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 11, 26))
        ),
        recurringTransactions = listOf(rent, pay),
        projects = listOf(japan, Project(ProjectId(uuid(21)), ProjectName("Cadeaux"), emoji = null, target = null))
    )

    private fun roundTrip(snapshot: BackupSnapshot): BackupSnapshot
    {
        return serializer.deserialize(serializer.serialize(snapshot))
    }

    @Test
    fun `a snapshot with every kind of data comes back identical`()
    {
        assertThat(roundTrip(everything)).isEqualTo(everything)
    }

    @Test
    fun `an empty snapshot comes back identical`()
    {
        val empty = BackupSnapshot(
            accounts = emptyList(),
            subcategories = emptyList(),
            transactions = emptyList(),
            budgets = emptyList(),
            budgetCalendar = BudgetCalendar(),
            recurringTransactions = emptyList(),
            projects = emptyList()
        )

        assertThat(roundTrip(empty)).isEqualTo(empty)
    }

    @Test
    fun `the accounts keep the order the user gave them`()
    {
        assertThat(roundTrip(everything).accounts).containsExactly(closedYen, checking)
    }

    @Test
    fun `the order of the other lists is kept too`()
    {
        val back = roundTrip(everything)

        assertThat(back.transactions).containsExactlyElementsOf(everything.transactions)
        assertThat(back.subcategories).containsExactlyElementsOf(everything.subcategories)
    }

    @Test
    fun `instants keep their nanoseconds`()
    {
        val back = roundTrip(everything)

        assertThat(back.accounts[0].createdAt).isEqualTo(Instant.parse("2025-03-01T10:00:00.123456789Z"))
        assertThat(back.accounts[0].archivedAt).isEqualTo(Instant.parse("2025-04-01T09:15:30.987654321Z"))
        assertThat(back.transactions[0].date).isEqualTo(Instant.parse("2026-09-30T12:00:00.5Z"))
    }

    @Test
    fun `every transaction category survives`()
    {
        assertThat(roundTrip(everything).transactions.map { it.category }).containsExactly(
            TransactionCategory.EXPENSE,
            TransactionCategory.INCOME,
            TransactionCategory.INITIAL_DEPOSIT,
            TransactionCategory.TRANSFER_OUT,
            TransactionCategory.TRANSFER_IN
        )
    }

    @Test
    fun `an amount that does not fit in a double survives`()
    {
        // JSON numbers are doubles in many readers: above 2^53 a careless parse rounds the cents.
        val big = Transaction.recorded(
            income.id, income.accountId, Money(Long.MAX_VALUE), income.title,
            RecordableTransactionCategory.INCOME, salary, null, income.date
        )
        val huge = everything.copy(transactions = listOf(big))

        assertThat(roundTrip(huge).transactions.single().amount).isEqualTo(Money(Long.MAX_VALUE))
    }

    @Test
    fun `a transaction of zero already stored is read back rather than refused`()
    {
        // The domain refuses to record one now, but a row stored earlier must stay readable: a backup
        // taken from such a database has to be restorable.
        val old = Transaction.restored(
            TransactionId(uuid(50)), checking.id, Money(0), TransactionTitle("Ancien"),
            TransactionCategory.EXPENSE, null, null, Instant.parse("2024-01-01T00:00:00Z"), null
        )
        val snapshot = everything.copy(transactions = listOf(old))

        assertThat(roundTrip(snapshot).transactions).containsExactly(old)
    }

    @Test
    fun `text that needs escaping survives`()
    {
        val tricky = income.let {
            Transaction.recorded(
                it.id, it.accountId, it.amount,
                TransactionTitle("Café \"Chez Léa\" \\ <b>&amp;</b> é́ 日本"),
                RecordableTransactionCategory.INCOME, salary,
                TransactionDescription.of("ligne 1\nligne 2\ttabulée, avec une virgule; et un point-virgule"),
                it.date
            )
        }
        val snapshot = everything.copy(transactions = listOf(tricky))

        assertThat(roundTrip(snapshot).transactions.single()).isEqualTo(tricky)
    }

    @Test
    fun `an emoji made of several code points survives`()
    {
        val family = groceries.copy(emoji = Emoji("👨‍👩‍👧‍👦"))

        assertThat(roundTrip(everything.copy(subcategories = listOf(family))).subcategories.single())
            .isEqualTo(family)
    }

    @Test
    fun `a budget keeps its own alert threshold`()
    {
        val back = roundTrip(everything).budgets

        assertThat(back[0].alertThreshold).isEqualTo(AlertThreshold(60))
        assertThat(back[1].alertThreshold).isEqualTo(AlertThreshold.DEFAULT)
    }

    @Test
    fun `the budget calendar keeps its usual day and its declared starts`()
    {
        val back = roundTrip(everything).budgetCalendar

        assertThat(back.defaultStartDay).isEqualTo(BudgetStartDay(25))
        assertThat(back.declaredStarts).containsExactlyInAnyOrder(
            LocalDate.of(2026, 9, 28),
            LocalDate.of(2026, 11, 26)
        )
    }

    @Test
    fun `a recurring transaction keeps its category, interval, end and bookkeeping`()
    {
        val back = roundTrip(everything).recurringTransactions

        assertThat(back).containsExactly(rent, pay)
        assertThat(back[0].lastGeneratedDate).isEqualTo(LocalDate.of(2026, 7, 31))
        assertThat(back[1].category).isEqualTo(RecordableTransactionCategory.INCOME)
        assertThat(back[1].endDate).isNull()
    }

    @Test
    fun `the file is a text that says which format version it is`()
    {
        val text = serializer.serialize(everything)

        assertThat(text).containsPattern("\"formatVersion\"\\s*:\\s*1")
    }

    @Test
    fun `serialising twice gives the same text`()
    {
        assertThat(serializer.serialize(everything)).isEqualTo(serializer.serialize(everything))
    }

    @Test
    fun `a file written by a newer version of the app is refused`()
    {
        val newer = serializer.serialize(everything)
            .replace(Regex("\"formatVersion\"\\s*:\\s*1"), "\"formatVersion\": 2")

        assertThatThrownBy { serializer.deserialize(newer) }.isInstanceOf(InvalidBackupException::class.java)
    }

    @Test
    fun `a file without a format version is refused`()
    {
        val unversioned = serializer.serialize(everything)
            .replace(Regex("\"formatVersion\"\\s*:\\s*1,?"), "")

        assertThatThrownBy { serializer.deserialize(unversioned) }
            .isInstanceOf(InvalidBackupException::class.java)
    }

    @Test
    fun `text that is not JSON is refused`()
    {
        assertThatThrownBy { serializer.deserialize("ceci n'est pas un fichier de sauvegarde") }
            .isInstanceOf(InvalidBackupException::class.java)
        assertThatThrownBy { serializer.deserialize("") }.isInstanceOf(InvalidBackupException::class.java)
    }

    @Test
    fun `a file cut short is refused`()
    {
        val text = serializer.serialize(everything)

        assertThatThrownBy { serializer.deserialize(text.take(text.length / 2)) }
            .isInstanceOf(InvalidBackupException::class.java)
    }

    @Test
    fun `JSON that is not a backup is refused`()
    {
        assertThatThrownBy { serializer.deserialize("[]") }.isInstanceOf(InvalidBackupException::class.java)
        assertThatThrownBy { serializer.deserialize("{\"formatVersion\": 1}") }
            .isInstanceOf(InvalidBackupException::class.java)
    }

    @Test
    fun `a file with a value the domain refuses is refused, not half imported`()
    {
        // A blank account name: the domain would never have let one be created.
        val tampered = serializer.serialize(everything).replace("Compte courant", "   ")

        assertThatThrownBy { serializer.deserialize(tampered) }
            .isInstanceOf(InvalidBackupException::class.java)
    }

    @Test
    fun `a file with an unknown enum value is refused`()
    {
        val tampered = serializer.serialize(everything).replace("CHECKING", "OFFSHORE")

        assertThatThrownBy { serializer.deserialize(tampered) }
            .isInstanceOf(InvalidBackupException::class.java)
    }
}
