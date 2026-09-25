package com.kyovo.cents.infrastructure.persistence.room

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.AccountCurrency
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.AccountName
import com.kyovo.cents.domain.model.AccountType
import com.kyovo.cents.domain.model.Money
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
 * What a database does that a list does not: it keeps the tables consistent with each other. A
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

    @TempDir
    lateinit var folder: File

    private val account = Account(
        AccountId(UUID.fromString("11111111-1111-1111-1111-111111111111")), AccountName("Compte"),
        AccountType.CHECKING, AccountCurrency(Currency.getInstance("EUR")), Instant.parse("2026-01-01T00:00:00Z"),
    )
    private val groceries = Subcategory(SubcategoryId(UUID.fromString("55555555-5555-5555-5555-555555555551")), RecordableTransactionCategory.EXPENSE, SubcategoryName("Alimentation"), null)
    private val date = Instant.parse("2026-09-22T10:00:00Z")

    private fun anExpense(suffix: Int, onAccount: AccountId = account.id, subcategory: Subcategory? = groceries) =
        Transaction.recorded(
            TransactionId(UUID.fromString("33333333-3333-3333-3333-33333333333$suffix")), onAccount, Money(1_250),
            TransactionTitle("Courses"), RecordableTransactionCategory.EXPENSE, subcategory, null, date,
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
