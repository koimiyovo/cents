package com.kyovo.cents.infrastructure.persistence

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
import com.kyovo.cents.domain.port.output.UnitOfWork
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Currency
import java.util.UUID

/** One storage: its three repositories, and the unit of work that makes writes across them all-or-nothing. */
class Storage(val stores: Stores, val unitOfWork: UnitOfWork)

/**
 * What every [UnitOfWork] must guarantee, whatever it is built on (today a database transaction): the writes of a block are all kept if it completes, and all undone if it
 * throws — the exception itself still reaching the caller — across the three repositories.
 */
abstract class UnitOfWorkContract
{
    /** Fresh, empty storage. Called before each test. */
    protected abstract fun createStorage(): Storage

    private lateinit var storage: Storage
    private val accounts get() = storage.stores.accounts
    private val subcategories get() = storage.stores.subcategories
    private val transactions get() = storage.stores.transactions
    private val unitOfWork get() = storage.unitOfWork

    @BeforeEach
    fun createTheStorage()
    {
        storage = createStorage()
    }

    private class Boom : RuntimeException("boom")

    private fun anAccount(suffix: Int, name: String = "Compte $suffix") = Account(
        AccountId(UUID.fromString("11111111-1111-1111-1111-11111111111$suffix")), AccountName(name),
        AccountType.CHECKING, AccountCurrency(Currency.getInstance("EUR")), Instant.parse("2026-01-01T00:00:00Z"),
    )

    private val groceries = Subcategory(SubcategoryId(UUID.fromString("55555555-5555-5555-5555-555555555551")), RecordableTransactionCategory.EXPENSE, SubcategoryName("Alimentation"), null)

    private fun anExpense(suffix: Int, on: Account, subcategory: Subcategory? = null) = Transaction.recorded(
        TransactionId(UUID.fromString("33333333-3333-3333-3333-33333333333$suffix")), on.id, Money(1_250),
        TransactionTitle("Courses"), RecordableTransactionCategory.EXPENSE, subcategory, null, Instant.parse("2026-09-22T10:00:00Z"),
    )

    /** Runs the block in a unit of work that is made to fail at its end; gives back what was thrown. */
    private suspend fun failing(block: suspend () -> Unit): Throwable? = runCatching {
        unitOfWork.execute { block(); throw Boom() }
    }.exceptionOrNull()

    // ------------------------------------------------------------------ when it completes

    @Test
    fun `keeps the writes of every repository when the block completes, and gives back its result`() = realTime()
    {
        // GIVEN
        val account = anAccount(1)

        // WHEN
        val result = unitOfWork.execute {
            accounts.save(account)
            subcategories.save(groceries)
            transactions.save(anExpense(1, account, groceries))
            "done"
        }

        // THEN
        assertThat(result).isEqualTo("done")
        assertThat(accounts.findAll()).containsExactly(account)
        assertThat(subcategories.findAll()).containsExactly(groceries)
        assertThat(transactions.findAll()).containsExactly(anExpense(1, account, groceries))
    }

    @Test
    fun `a block can read what it has just written`() = realTime()
    {
        // GIVEN / WHEN
        val seen = unitOfWork.execute {
            accounts.save(anAccount(1))
            accounts.findAll().size
        }

        // THEN
        assertThat(seen).isEqualTo(1)
    }

    @Test
    fun `two units of work in a row each keep their writes`() = realTime()
    {
        // WHEN
        unitOfWork.execute { accounts.save(anAccount(1)) }
        unitOfWork.execute { accounts.save(anAccount(2)) }

        // THEN
        assertThat(accounts.findAll().map { it.id }).containsExactly(anAccount(1).id, anAccount(2).id)
    }

    // ------------------------------------------------------------------ when it fails

    @Test
    fun `undoes the writes of every repository when the block throws, and lets the exception through`() = realTime()
    {
        // GIVEN
        val account = anAccount(1)

        // WHEN
        val thrown = failing {
            accounts.save(account)
            subcategories.save(groceries)
            transactions.save(anExpense(1, account, groceries))
        }

        // THEN
        assertThat(thrown).isInstanceOf(Boom::class.java)
        assertThat(accounts.findAll()).isEmpty()
        assertThat(subcategories.findAll()).isEmpty()
        assertThat(transactions.findAll()).isEmpty()
    }

    @Test
    fun `does not undo what was written before the failing unit of work started`() = realTime()
    {
        // GIVEN an account saved earlier
        val existing = anAccount(1)
        accounts.save(existing)

        // WHEN a later unit of work adds another one and fails
        failing { accounts.save(anAccount(2)) }

        // THEN
        assertThat(accounts.findAll()).containsExactly(existing)
    }

    @Test
    fun `undoes a change made to what already existed`() = realTime()
    {
        // GIVEN
        val original = anAccount(1, "Livret A")
        accounts.save(original)

        // WHEN it is renamed and archived in a unit of work that fails
        failing { accounts.save(original.copy(name = AccountName("Renommé"), archivedAt = Instant.parse("2026-09-23T09:00:00Z"))) }

        // THEN
        assertThat(accounts.findById(original.id)).isEqualTo(original)
    }

    @Test
    fun `undoes a deletion`() = realTime()
    {
        // GIVEN
        subcategories.save(groceries)

        // WHEN
        failing { subcategories.deleteById(groceries.id) }

        // THEN it is back
        assertThat(subcategories.findAll()).containsExactly(groceries)
    }

    @Test
    fun `undoes a reordering`() = realTime()
    {
        // GIVEN
        accounts.save(anAccount(1))
        accounts.save(anAccount(2))
        accounts.save(anAccount(3))

        // WHEN
        failing { accounts.reorder(listOf(anAccount(3).id, anAccount(2).id, anAccount(1).id)) }

        // THEN
        assertThat(accounts.findAll().map { it.id }).containsExactly(anAccount(1).id, anAccount(2).id, anAccount(3).id)
    }

    @Test
    fun `a failed deletion of an account with its transactions leaves both`() = realTime()
    {
        // GIVEN an account and its transaction
        val account = anAccount(1)
        accounts.save(account)
        transactions.save(anExpense(1, account))

        // WHEN the transactions go, then the account, and something fails afterwards
        failing {
            transactions.deleteById(anExpense(1, account).id)
            accounts.deleteById(account.id)
        }

        // THEN
        assertThat(accounts.findAll()).containsExactly(account)
        assertThat(transactions.findAll()).containsExactly(anExpense(1, account))
    }

    @Test
    fun `a completed deletion of an account with its transactions removes both`() = realTime()
    {
        // GIVEN
        val account = anAccount(1)
        accounts.save(account)
        transactions.save(anExpense(1, account))

        // WHEN
        unitOfWork.execute {
            transactions.deleteById(anExpense(1, account).id)
            accounts.deleteById(account.id)
        }

        // THEN
        assertThat(accounts.findAll()).isEmpty()
        assertThat(transactions.findAll()).isEmpty()
    }

    @Test
    fun `an observer ends up seeing the state from before the failed unit of work`() = realTime()
    {
        // GIVEN a screen collecting the accounts
        val existing = anAccount(1)
        accounts.save(existing)

        // WHEN a unit of work adds an account and fails
        failing { accounts.save(anAccount(2)) }

        // THEN what an observer gets now is the original list
        assertThat(accounts.observeAll().awaitMatching { it == listOf(existing) }).containsExactly(existing)
    }
}
