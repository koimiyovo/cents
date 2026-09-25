package com.kyovo.cents.data

import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.AccountCurrency
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.AccountName
import com.kyovo.cents.domain.model.AccountType
import com.kyovo.cents.infrastructure.persistence.ListAccountRepository
import com.kyovo.cents.infrastructure.persistence.ListSubcategoryRepository
import com.kyovo.cents.infrastructure.persistence.ListTransactionRepository
import com.kyovo.cents.infrastructure.persistence.ListUnitOfWork
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Currency
import java.util.UUID

/**
 * The demo data is for a debug build to start with something to look at. It goes in once, into an
 * empty database, and never over what the user has: with a real database the accounts are still
 * there at the next launch, and seeding again would duplicate them or bury the user's own.
 */
class SeedDemoDataTest
{
    private val accounts = ListAccountRepository()
    private val transactions = ListTransactionRepository()
    private val subcategories = ListSubcategoryRepository()
    private val unitOfWork = ListUnitOfWork(accounts, transactions, subcategories)

    private suspend fun seed() = seedDemoDataIfEmpty(accounts, transactions, subcategories, unitOfWork)

    private val usersOwnAccount = Account(
        AccountId(UUID.fromString("99999999-9999-9999-9999-999999999999")), AccountName("Mon compte"),
        AccountType.CHECKING, AccountCurrency(Currency.getInstance("EUR")), Instant.parse("2026-01-01T00:00:00Z"),
    )

    @Test
    fun `seeds the demo data into an empty database, and says so`() = runTest()
    {
        // WHEN
        val seeded = seed()

        // THEN
        assertThat(seeded).isTrue()
        assertThat(accounts.findAll()).hasSize(3)
        assertThat(subcategories.findAll()).isNotEmpty()
        assertThat(transactions.findAll()).isNotEmpty()
    }

    @Test
    fun `seeds nothing a second time`() = runTest()
    {
        // GIVEN
        seed()
        val transactionCount = transactions.findAll().size

        // WHEN
        val seeded = seed()

        // THEN
        assertThat(seeded).isFalse()
        assertThat(accounts.findAll()).hasSize(3)
        assertThat(transactions.findAll()).hasSize(transactionCount)
    }

    @Test
    fun `leaves alone a database that already holds an account of the user`() = runTest()
    {
        // GIVEN
        accounts.save(usersOwnAccount)

        // WHEN
        val seeded = seed()

        // THEN
        assertThat(seeded).isFalse()
        assertThat(accounts.findAll()).containsExactly(usersOwnAccount)
        assertThat(transactions.findAll()).isEmpty()
        assertThat(subcategories.findAll()).isEmpty()
    }

    @Test
    fun `leaves alone a database that only holds subcategories`() = runTest()
    {
        // GIVEN the user has created their own subcategories but no account yet
        seed()
        accounts.findAll().forEach { account ->
            transactions.findAll().filter { it.accountId == account.id }.forEach { transactions.deleteById(it.id) }
            accounts.deleteById(account.id)
        }
        val subcategoryCount = subcategories.findAll().size

        // WHEN
        val seeded = seed()

        // THEN nothing came back: the database is not empty
        assertThat(seeded).isFalse()
        assertThat(accounts.findAll()).isEmpty()
        assertThat(subcategories.findAll()).hasSize(subcategoryCount)
    }
}
