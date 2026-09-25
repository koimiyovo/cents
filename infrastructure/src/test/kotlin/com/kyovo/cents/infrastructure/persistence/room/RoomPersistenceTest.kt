package com.kyovo.cents.infrastructure.persistence.room

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.kyovo.cents.domain.model.Account
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
    fun `starts empty`() = realTime()
    {
        assertThat(persistence.accounts.findAll()).isEmpty()
        assertThat(persistence.subcategories.findAll()).isEmpty()
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
}
