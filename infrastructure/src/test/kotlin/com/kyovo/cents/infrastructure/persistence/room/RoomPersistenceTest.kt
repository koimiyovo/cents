package com.kyovo.cents.infrastructure.persistence.room

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.TransactionTitle
import com.kyovo.cents.domain.model.TransactionId
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.SubcategoryName
import com.kyovo.cents.domain.model.DefaultSubcategories
import com.kyovo.cents.domain.model.AccountCurrency
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.AccountName
import com.kyovo.cents.domain.model.AccountType
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
 * [RoomPersistence] is what the app builds once: one database, and the three repositories and the unit
 * of work that all work on it. The app only sees the domain's ports, never a Room type.
 */
class RoomPersistenceTest
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

    private fun anAccount(suffix: Int) = Account(
        AccountId(UUID.fromString("11111111-1111-1111-1111-11111111111$suffix")), AccountName("Compte $suffix"),
        AccountType.CHECKING, AccountCurrency(Currency.getInstance("EUR")), Instant.parse("2026-01-01T00:00:00Z"),
    )

    @Test
    fun `starts with the common subcategories, and nothing else`() = realTime()
    {
        assertThat(persistence.subcategories.findAll()).containsExactlyInAnyOrderElementsOf(DefaultSubcategories.ALL)
        assertThat(persistence.accounts.findAll()).isEmpty()
        assertThat(persistence.transactions.findAll()).isEmpty()
    }

    @Test
    fun `what one repository saves is seen by the others' unit of work and by itself`() = realTime()
    {
        // GIVEN / WHEN
        persistence.accounts.save(anAccount(1))

        // THEN
        assertThat(persistence.accounts.findAll()).containsExactly(anAccount(1))
    }

    @Test
    fun `the unit of work and the repositories share one database`() = realTime()
    {
        // GIVEN an account saved through the repository
        persistence.accounts.save(anAccount(1))

        // WHEN a unit of work adds another one through the same repository, and fails
        runCatching {
            persistence.unitOfWork.execute {
                persistence.accounts.save(anAccount(2))
                error("boom")
            }
        }

        // THEN the rollback undid the write made through the repository
        assertThat(persistence.accounts.findAll()).containsExactly(anAccount(1))
    }

    // ------------------------------------------------------------------ a file, and its version

    @TempDir
    lateinit var folder: File

    private fun setUserVersion(path: String, version: Int)
    {
        BundledSQLiteDriver().open(path).use { it.execSQL("PRAGMA user_version = $version") }
    }

    @Test
    fun `what was saved in a file is still there when it is opened again`() = realTime()
    {
        // GIVEN
        val path = File(folder, "cents.db").absolutePath
        RoomPersistence.openFile(path, BundledSQLiteDriver()).also { it.accounts.save(anAccount(1)); it.close() }

        // WHEN
        val reopened = RoomPersistence.openFile(path, BundledSQLiteDriver())
        val found = reopened.accounts.findAll()
        reopened.close()

        // THEN
        assertThat(found).containsExactly(anAccount(1))
    }

    // A phone that got a newer version of the app, then an older one: the older app must not "fix" the
    // mismatch by erasing the data. Room recreating the tables silently is what a destructive migration
    // fallback would do, and it is deliberately not configured.
    @Test
    fun `refuses a database written by a newer version instead of wiping it`() = realTime()
    {
        // GIVEN a file with an account, marked as written by a much newer version
        val path = File(folder, "cents.db").absolutePath
        RoomPersistence.openFile(path, BundledSQLiteDriver()).also { it.accounts.save(anAccount(1)); it.close() }
        setUserVersion(path, 99)

        // WHEN / THEN opening it fails
        val refusing = RoomPersistence.openFile(path, BundledSQLiteDriver())
        val failure = runCatching { refusing.accounts.findAll() }.exceptionOrNull()
        refusing.close()
        assertThat(failure).isInstanceOf(IllegalStateException::class.java)

        // AND nothing was lost: with the right version again, the account is there
        setUserVersion(path, 1)
        val restored = RoomPersistence.openFile(path, BundledSQLiteDriver())
        val found = restored.accounts.findAll()
        restored.close()
        assertThat(found).containsExactly(anAccount(1))
    }

    // ------------------------------------------------------------------ the subcategories the app is delivered with

    @Test
    fun `a new database file starts with the common subcategories`() = realTime()
    {
        // GIVEN / WHEN
        val created = RoomPersistence.openFile(File(folder, "cents.db").absolutePath, BundledSQLiteDriver())
        val found = created.subcategories.findAll()
        created.close()

        // THEN
        assertThat(found).containsExactlyInAnyOrderElementsOf(DefaultSubcategories.ALL)
    }

    // They are put in when the database is created, once: after that they are the user's, who may rename
    // or delete them, and a deleted one must not come back at the next launch.
    @Test
    fun `a subcategory deleted by the user does not come back when the database is opened again`() = realTime()
    {
        // GIVEN a file where the user deleted "Alimentation" and renamed another one
        val path = File(folder, "cents.db").absolutePath
        val first = RoomPersistence.openFile(path, BundledSQLiteDriver())
        val groceries = DefaultSubcategories.ALL.single { it.name.value == "Alimentation" }
        val transport = DefaultSubcategories.ALL.single { it.name.value == "Transport" }
        first.subcategories.deleteById(groceries.id)
        first.subcategories.save(transport.copy(name = SubcategoryName("Déplacements")))
        first.close()

        // WHEN the file is opened by a new database
        val reopened = RoomPersistence.openFile(path, BundledSQLiteDriver())
        val found = reopened.subcategories.findAll()
        reopened.close()

        // THEN nothing was put back, and nothing added twice
        assertThat(found).hasSize(DefaultSubcategories.ALL.size - 1)
        assertThat(found.map { it.name.value }).doesNotContain("Alimentation", "Transport").contains("Déplacements")
    }

    @Test
    fun `a transaction can be recorded under a common subcategory`() = realTime()
    {
        // GIVEN an account, and a subcategory that came with the database
        persistence.accounts.save(anAccount(1))
        val groceries = DefaultSubcategories.ALL.single { it.name.value == "Alimentation" }

        // WHEN
        val expense = Transaction.recorded(
            TransactionId(UUID.fromString("33333333-3333-3333-3333-333333333333")), anAccount(1).id, Money(1_250),
            TransactionTitle("Courses"), RecordableTransactionCategory.EXPENSE, groceries, null, Instant.parse("2026-09-22T10:00:00Z"),
        )
        persistence.transactions.save(expense)

        // THEN the link to the subcategory holds (the database checks it exists)
        assertThat(persistence.transactions.findAll()).containsExactly(expense)
    }
}
