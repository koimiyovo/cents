package com.kyovo.cents.infrastructure.persistence.room

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.AccountCurrency
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.AccountName
import com.kyovo.cents.domain.model.AccountType
import com.kyovo.cents.infrastructure.persistence.realTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.Instant
import java.util.Currency
import java.util.UUID

/** What only a database on disk can do: keep the accounts, and the order the user gave them, once closed. */
class RoomAccountRepositoryTest
{
    @TempDir
    lateinit var folder: File

    private fun open(): CentsDatabase =
        Room.databaseBuilder<CentsDatabase>(File(folder, "cents.db").absolutePath)
            .setDriver(BundledSQLiteDriver())
            .build()

    private fun anAccount(suffix: Int, name: String) = Account(
        id = AccountId(UUID.fromString("11111111-1111-1111-1111-11111111111$suffix")),
        name = AccountName(name),
        type = AccountType.SAVINGS,
        currency = AccountCurrency(Currency.getInstance("EUR")),
        createdAt = Instant.parse("2026-09-22T10:00:00.123456789Z"),
    )

    @Test
    fun `the accounts and their order are still there when the database is closed and opened again`() = realTime()
    {
        // GIVEN three accounts, reordered by hand, then the database closed
        val a = anAccount(1, "A")
        val b = anAccount(2, "B")
        val c = anAccount(3, "C")
        val first = open()
        RoomAccountRepository(first.accountDao()).also {
            it.save(a); it.save(b); it.save(c)
            it.reorder(listOf(c.id, a.id, b.id))
        }
        first.close()

        // WHEN the file is opened by a new database
        val second = open()
        val found = RoomAccountRepository(second.accountDao()).findAll()
        second.close()

        // THEN
        assertThat(found).containsExactly(c, a, b)
    }
}
