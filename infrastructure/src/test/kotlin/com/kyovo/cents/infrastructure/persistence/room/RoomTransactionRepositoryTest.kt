package com.kyovo.cents.infrastructure.persistence.room

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.AccountCurrency
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.AccountName
import com.kyovo.cents.domain.model.AccountType
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.Project
import com.kyovo.cents.domain.model.ProjectId
import com.kyovo.cents.domain.model.ProjectName
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.Subcategory
import com.kyovo.cents.domain.model.SubcategoryId
import com.kyovo.cents.domain.model.SubcategoryName
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.model.TransactionId
import com.kyovo.cents.domain.model.TransactionTitle
import com.kyovo.cents.infrastructure.persistence.assertThatThrownBySuspending
import com.kyovo.cents.infrastructure.persistence.realTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.Instant
import java.util.Currency
import java.util.UUID

/**
 * What a database does beyond storing rows: it keeps the tables consistent with each other. A
 * transaction cannot point to an account that does not exist, an account with transactions cannot
 * vanish under them, and deleting a subcategory leaves its transactions uncategorised — the same
 * rules the services apply, enforced a second time where the data lives.
 */
class RoomTransactionRepositoryTest
{
    private lateinit var database: CentsDatabase
    private lateinit var accounts: RoomAccountRepository
    private lateinit var subcategories: RoomSubcategoryRepository
    private lateinit var transactions: RoomTransactionRepository
    private lateinit var projects: RoomProjectRepository

    @TempDir
    lateinit var folder: File

    private val account = Account(
        AccountId(UUID.fromString("11111111-1111-1111-1111-111111111111")), AccountName("Compte"),
        AccountType.CHECKING, AccountCurrency(Currency.getInstance("EUR")), Instant.parse("2026-01-01T00:00:00Z"),
    )
    private val groceries = Subcategory(SubcategoryId(UUID.fromString("55555555-5555-5555-5555-555555555551")), RecordableTransactionCategory.EXPENSE, SubcategoryName("Alimentation"), null)
    private val date = Instant.parse("2026-09-22T10:00:00Z")

    private val japan = Project(ProjectId(UUID.fromString("66666666-6666-6666-6666-666666666661")), ProjectName("Voyage au Japon"), null, null)
    private val kitchen = Project(ProjectId(UUID.fromString("66666666-6666-6666-6666-666666666662")), ProjectName("Travaux cuisine"), null, null)

    private fun anExpense(
        suffix: Int,
        onAccount: AccountId = account.id,
        subcategory: Subcategory? = groceries,
        project: Project? = null,
    ) =
        Transaction.recorded(
            TransactionId(UUID.fromString("33333333-3333-3333-3333-33333333333$suffix")), onAccount, Money(1_250),
            TransactionTitle("Courses"), RecordableTransactionCategory.EXPENSE, subcategory, null, date, project?.id,
        )

    private fun open(file: File? = null): CentsDatabase =
        (if (file == null) Room.inMemoryDatabaseBuilder<CentsDatabase>() else Room.databaseBuilder<CentsDatabase>(file.absolutePath))
            .setDriver(BundledSQLiteDriver())
            .build()

    private fun useDatabase(db: CentsDatabase)
    {
        database = db
        accounts = RoomAccountRepository(db.accountDao())
        subcategories = RoomSubcategoryRepository(db.subcategoryDao())
        transactions = RoomTransactionRepository(db.transactionDao())
        projects = RoomProjectRepository(db.projectDao())
    }

    @BeforeEach
    fun openInMemory()
    {
        useDatabase(open())
    }

    @AfterEach
    fun closeTheDatabase()
    {
        database.close()
    }

    @Test
    fun `refuses a transaction of an account that does not exist`() = realTime()
    {
        // GIVEN no account saved

        // WHEN / THEN
        assertThatThrownBySuspending { transactions.save(anExpense(1)) }.isNotNull()
        assertThat(transactions.findAll()).isEmpty()
    }

    @Test
    fun `refuses a transaction of a subcategory that does not exist`() = realTime()
    {
        // GIVEN an account, but no subcategory
        accounts.save(account)

        // WHEN / THEN
        assertThatThrownBySuspending { transactions.save(anExpense(1)) }.isNotNull()
    }

    @Test
    fun `refuses to delete an account that still has transactions`() = realTime()
    {
        // GIVEN
        accounts.save(account)
        subcategories.save(groceries)
        transactions.save(anExpense(1))

        // WHEN / THEN nothing is lost
        assertThatThrownBySuspending { accounts.deleteById(account.id) }.isNotNull()
        assertThat(accounts.findById(account.id)).isNotNull()
        assertThat(transactions.findAll()).hasSize(1)
    }

    @Test
    fun `deletes an account once its transactions are gone`() = realTime()
    {
        // GIVEN
        accounts.save(account)
        subcategories.save(groceries)
        transactions.save(anExpense(1))

        // WHEN
        transactions.deleteById(anExpense(1).id)
        accounts.deleteById(account.id)

        // THEN
        assertThat(accounts.findAll()).isEmpty()
    }

    @Test
    fun `deleting a subcategory keeps its transactions, uncategorised`() = realTime()
    {
        // GIVEN
        accounts.save(account)
        subcategories.save(groceries)
        val categorised = anExpense(1)
        transactions.save(categorised)

        // WHEN
        subcategories.deleteById(groceries.id)

        // THEN the transaction stays, and nothing else about it changed
        assertThat(transactions.findAll()).containsExactly(categorised.withoutSubcategory())
    }

    @Test
    fun `refuses a transaction of a project that does not exist`() = realTime()
    {
        // GIVEN an account and a subcategory, but no project
        accounts.save(account)
        subcategories.save(groceries)

        // WHEN / THEN
        assertThatThrownBySuspending { transactions.save(anExpense(1, project = japan)) }.isNotNull()
        assertThat(transactions.findAll()).isEmpty()
    }

    // The same rule as for a subcategory, enforced a second time where the data lives.
    @Test
    fun `deleting a project keeps its transactions, without the project`() = realTime()
    {
        // GIVEN
        accounts.save(account)
        subcategories.save(groceries)
        projects.save(japan)
        val inTheProject = anExpense(1, project = japan)
        transactions.save(inTheProject)

        // WHEN
        projects.deleteById(japan.id)

        // THEN the transaction stays, and nothing else about it changed
        assertThat(transactions.findAll()).containsExactly(inTheProject.withoutProject())
        assertThat(transactions.findAll().single().subcategoryId).isEqualTo(groceries.id)
    }

    @Test
    fun `deleting a project does not touch the transactions of the other projects`() = realTime()
    {
        // GIVEN
        accounts.save(account)
        subcategories.save(groceries)
        projects.save(japan)
        projects.save(kitchen)
        transactions.save(anExpense(1, project = japan))
        val inKitchen = anExpense(2, project = kitchen)
        transactions.save(inKitchen)
        val withNone = anExpense(3)
        transactions.save(withNone)

        // WHEN
        projects.deleteById(japan.id)

        // THEN
        assertThat(transactions.findAll()).containsExactly(anExpense(1), inKitchen, withNone)
    }

    @Test
    fun `deleting a transaction leaves its project`() = realTime()
    {
        // GIVEN
        accounts.save(account)
        subcategories.save(groceries)
        projects.save(japan)
        transactions.save(anExpense(1, project = japan))

        // WHEN
        transactions.deleteById(anExpense(1).id)

        // THEN
        assertThat(projects.findAll()).containsExactly(japan)
    }

    @Test
    fun `the transactions are still there when the database is closed and opened again`() = realTime()
    {
        // GIVEN a file database with an account, a subcategory and two transactions, then closed
        val file = File(folder, "cents.db")
        database.close()
        useDatabase(open(file))
        accounts.save(account)
        subcategories.save(groceries)
        transactions.save(anExpense(1))
        transactions.save(anExpense(2, subcategory = null))
        database.close()

        // WHEN the file is opened by a new database
        useDatabase(open(file))

        // THEN
        assertThat(transactions.findAll()).containsExactly(anExpense(1), anExpense(2, subcategory = null))
    }
}
