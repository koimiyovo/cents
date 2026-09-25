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
import com.kyovo.cents.domain.model.TransactionDescription
import com.kyovo.cents.domain.model.TransactionId
import com.kyovo.cents.domain.model.TransactionTitle
import com.kyovo.cents.domain.port.output.AccountRepository
import com.kyovo.cents.domain.port.output.SubcategoryRepository
import com.kyovo.cents.domain.port.output.TransactionRepository
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Currency
import java.util.UUID

/**
 * The three repositories of one storage, built together: a transaction points to an account and to a
 * subcategory, and a database (unlike a list) checks that what it points to exists. So the contract
 * saves the accounts and subcategories it needs through the same storage.
 */
class Stores(
    val accounts: AccountRepository,
    val subcategories: SubcategoryRepository,
    val transactions: TransactionRepository,
)

/**
 * What every [TransactionRepository] must do, whatever it stores the transactions in (the in-memory
 * list, the Room database). What only a database does — refusing a transaction of an unknown
 * account, for instance — is tested by the Room adapter's own class.
 */
abstract class TransactionRepositoryContract
{
    /** Fresh, empty storage. Called before each test. */
    protected abstract fun createStores(): Stores

    protected lateinit var stores: Stores

    private val accountA = anAccount(1)
    private val accountB = anAccount(2)
    private val groceries = Subcategory(SubcategoryId(UUID.fromString("55555555-5555-5555-5555-555555555551")), RecordableTransactionCategory.EXPENSE, SubcategoryName("Alimentation"), null)
    private val salaryCategory = Subcategory(SubcategoryId(UUID.fromString("55555555-5555-5555-5555-555555555552")), RecordableTransactionCategory.INCOME, SubcategoryName("Salaire"), null)

    @BeforeEach
    fun createTheStores()
    {
        stores = createStores()
        realTime()
        {
            stores.accounts.save(accountA)
            stores.accounts.save(accountB)
            stores.subcategories.save(groceries)
            stores.subcategories.save(salaryCategory)
        }
    }

    private val repository get() = stores.transactions

    private fun anAccount(suffix: Int) = Account(
        id = AccountId(UUID.fromString("11111111-1111-1111-1111-11111111111$suffix")),
        name = AccountName("Compte $suffix"),
        type = AccountType.CHECKING,
        currency = AccountCurrency(Currency.getInstance("EUR")),
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
    )

    private fun transactionId(suffix: Int) = TransactionId(UUID.fromString("33333333-3333-3333-3333-33333333333$suffix"))

    private val date = Instant.parse("2026-09-22T10:00:00Z")

    private fun anExpense(
        suffix: Int,
        cents: Long = 1_250,
        title: String = "Courses",
        account: Account = accountA,
        subcategory: Subcategory? = groceries,
        description: String? = null,
        at: Instant = date,
    ) = Transaction.recorded(
        transactionId(suffix), account.id, Money(cents), TransactionTitle(title),
        RecordableTransactionCategory.EXPENSE, subcategory, TransactionDescription.of(description), at,
    )

    // ------------------------------------------------------------------ reading what was saved

    @Test
    fun `finds a transaction that has been saved`() = realTime()
    {
        // GIVEN
        val expense = anExpense(1)
        repository.save(expense)

        // WHEN / THEN
        assertThat(repository.findById(expense.id)).isEqualTo(expense)
    }

    @Test
    fun `finds nothing for an unknown id`() = realTime()
    {
        assertThat(repository.findById(transactionId(1))).isNull()
    }

    @Test
    fun `lists nothing when nothing was saved`() = realTime()
    {
        assertThat(repository.findAll()).isEmpty()
    }

    @Test
    fun `gives back every kind of transaction with every field`() = realTime()
    {
        // GIVEN one of each kind
        val deposit = Transaction.openingDeposit(transactionId(1), accountA.id, Money(100_000), date)
        val income = Transaction.recorded(
            transactionId(2), accountA.id, Money(250_000), TransactionTitle("Salaire de septembre"),
            RecordableTransactionCategory.INCOME, salaryCategory, TransactionDescription.of("Virement\nde l'employeur 💶"), date,
        )
        val expense = anExpense(3, subcategory = null)
        val transferOut = Transaction.transferOut(transactionId(4), accountA.id, Money(5_000), TransactionTitle("Retrait espèces"), date)
        val transferIn = Transaction.transferIn(transactionId(5), accountB.id, Money(5_000), TransactionTitle("Retrait espèces"), date)
        listOf(deposit, income, expense, transferOut, transferIn).forEach { repository.save(it) }

        // WHEN / THEN
        assertThat(repository.findAll()).containsExactly(deposit, income, expense, transferOut, transferIn)
    }

    @Test
    fun `keeps the date to the nanosecond`() = realTime()
    {
        // GIVEN
        val expense = anExpense(1, at = Instant.parse("2026-09-22T10:00:00.123456789Z"))

        // WHEN
        repository.save(expense)

        // THEN
        assertThat(repository.findById(expense.id)).isEqualTo(expense)
    }

    @Test
    fun `keeps a very large amount`() = realTime()
    {
        // GIVEN 90 billion euros, in cents
        val expense = anExpense(1, cents = 9_000_000_000_000L)

        // WHEN
        repository.save(expense)

        // THEN
        assertThat(repository.findById(expense.id)?.amount).isEqualTo(Money(9_000_000_000_000L))
    }

    @Test
    fun `keeps titles and descriptions with quotes, accents and line breaks intact`() = realTime()
    {
        // GIVEN
        val expense = anExpense(1, title = "L'épicerie \"bio\"; DROP TABLE transactions;--", description = "ligne 1\nligne 2 éè 🛒")

        // WHEN
        repository.save(expense)

        // THEN
        assertThat(repository.findById(expense.id)).isEqualTo(expense)
    }

    // ------------------------------------------------------------------ saving again, order

    @Test
    fun `lists the transactions in the order they were first saved`() = realTime()
    {
        // GIVEN
        val third = anExpense(3)
        val first = anExpense(1)
        val second = anExpense(2)
        repository.save(third)
        repository.save(first)
        repository.save(second)

        // WHEN / THEN
        assertThat(repository.findAll()).containsExactly(third, first, second)
    }

    @Test
    fun `saving an existing transaction replaces it where it stands`() = realTime()
    {
        // GIVEN
        repository.save(anExpense(1))
        repository.save(anExpense(2))
        repository.save(anExpense(3))

        // WHEN the first is edited: amount, title, moved to another account, subcategory taken away
        val edited = anExpense(1, cents = 9_900, title = "Autre", account = accountB, subcategory = null)
        repository.save(edited)

        // THEN it did not move, and nothing was duplicated
        assertThat(repository.findAll().map { it.id }).containsExactly(transactionId(1), transactionId(2), transactionId(3))
        assertThat(repository.findById(transactionId(1))).isEqualTo(edited)
    }

    // ------------------------------------------------------------------ deleting

    @Test
    fun `deletes a transaction and leaves the others`() = realTime()
    {
        // GIVEN
        repository.save(anExpense(1))
        val kept = anExpense(2)
        repository.save(kept)

        // WHEN
        repository.deleteById(transactionId(1))

        // THEN
        assertThat(repository.findAll()).containsExactly(kept)
        assertThat(repository.findById(transactionId(1))).isNull()
    }

    @Test
    fun `is silent about deleting an unknown transaction`() = realTime()
    {
        // GIVEN
        val kept = anExpense(1)
        repository.save(kept)

        // WHEN
        repository.deleteById(transactionId(2))

        // THEN
        assertThat(repository.findAll()).containsExactly(kept)
    }

    // ------------------------------------------------------------------ observing

    @Test
    fun `an observer first gets what is stored, in order`() = realTime()
    {
        // GIVEN
        val second = anExpense(2)
        val first = anExpense(1)
        repository.save(second)
        repository.save(first)

        // WHEN / THEN
        assertThat(repository.observeAll().first()).containsExactly(second, first)
    }

    @Test
    fun `an observer of an empty repository gets an empty list`() = realTime()
    {
        assertThat(repository.observeAll().first()).isEmpty()
    }

    @Test
    fun `an observer collecting late still gets the current state`() = realTime()
    {
        // GIVEN changes made before anyone observes
        repository.save(anExpense(1))
        repository.save(anExpense(2))
        repository.deleteById(transactionId(1))

        // WHEN / THEN
        assertThat(repository.observeAll().first().map { it.id }).containsExactly(transactionId(2))
    }

    @Test
    fun `an observer follows the repository as transactions are added, edited and deleted`() = realTime()
    {
        // GIVEN a screen collecting the transactions' amounts
        val latest = repository.observeAll().map { list -> list.map { it.amount.value } }.stateIn(this, SharingStarted.Eagerly, null)
        latest.awaitMatching { it != null && it.isEmpty() }

        // WHEN / THEN each change shows up
        repository.save(anExpense(1, cents = 100))
        repository.save(anExpense(2, cents = 200))
        latest.awaitMatching { it == listOf(100L, 200L) }

        repository.save(anExpense(1, cents = 150))
        latest.awaitMatching { it == listOf(150L, 200L) }

        repository.deleteById(transactionId(1))
        latest.awaitMatching { it == listOf(200L) }

        coroutineContext.cancelChildren()
    }
}
