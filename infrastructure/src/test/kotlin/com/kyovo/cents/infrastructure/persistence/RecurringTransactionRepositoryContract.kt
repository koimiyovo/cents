package com.kyovo.cents.infrastructure.persistence

import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.AccountCurrency
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.AccountName
import com.kyovo.cents.domain.model.AccountType
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.RecurrenceFrequency
import com.kyovo.cents.domain.model.RecurringTransaction
import com.kyovo.cents.domain.model.RecurringTransactionId
import com.kyovo.cents.domain.model.Subcategory
import com.kyovo.cents.domain.model.SubcategoryId
import com.kyovo.cents.domain.model.SubcategoryName
import com.kyovo.cents.domain.model.TransactionDescription
import com.kyovo.cents.domain.model.TransactionTitle
import com.kyovo.cents.domain.port.output.AccountRepository
import com.kyovo.cents.domain.port.output.RecurringTransactionRepository
import com.kyovo.cents.domain.port.output.SubcategoryRepository
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.Currency
import java.util.UUID

/**
 * The three repositories of one storage, built together: a recurring expense points to an account and,
 * maybe, a subcategory, and a database (unlike a list) checks that what it points to exists (the
 * subcategory only — see [com.kyovo.cents.infrastructure.persistence.room.RecurringTransactionEntity] for why
 * there is no such check on the account). So the contract saves what it needs through the same storage.
 */
class RecurringTransactionStores(
    val accounts: AccountRepository,
    val subcategories: SubcategoryRepository,
    val recurringTransactions: RecurringTransactionRepository,
)

/**
 * What every [RecurringTransactionRepository] must do, whatever it stores the rules in (today Room). What
 * only a database does — a deleted subcategory taking a rule's link with it — is tested by the Room
 * adapter's own class.
 */
abstract class RecurringTransactionRepositoryContract
{
    /** Fresh, empty storage. Called before each test. */
    protected abstract fun createStores(): RecurringTransactionStores

    protected lateinit var stores: RecurringTransactionStores

    private val account = Account(
        id = AccountId(UUID.fromString("11111111-1111-1111-1111-111111111111")),
        name = AccountName("Compte courant"),
        type = AccountType.CHECKING,
        currency = AccountCurrency(Currency.getInstance("EUR")),
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
    )
    private val rent = Subcategory(
        SubcategoryId(UUID.fromString("55555555-5555-5555-5555-555555555551")),
        RecordableTransactionCategory.EXPENSE,
        SubcategoryName("Logement"),
        null,
    )

    @BeforeEach
    fun createTheStores()
    {
        stores = createStores()
        realTime()
        {
            stores.accounts.save(account)
            stores.subcategories.save(rent)
        }
    }

    private val repository get() = stores.recurringTransactions

    private fun anId(suffix: Int) =
        RecurringTransactionId(UUID.fromString("66666666-6666-6666-6666-66666666666$suffix"))

    private fun aRule(
        suffix: Int,
        category: RecordableTransactionCategory = RecordableTransactionCategory.EXPENSE,
        amount: Long = 80_000,
        title: String = "Loyer",
        subcategory: Subcategory? = rent,
        description: String? = null,
        frequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
        interval: Int = 1,
        startDate: LocalDate = LocalDate.of(2026, 9, 5),
        endDate: LocalDate? = null,
        lastGeneratedDate: LocalDate? = null,
    ) = RecurringTransaction(
        anId(suffix), account.id, category, Money(amount), TransactionTitle(title), subcategory?.id,
        TransactionDescription.of(description), frequency, interval, startDate, endDate, lastGeneratedDate,
    )

    // ------------------------------------------------------------------ reading what was saved

    @Test
    fun `finds a rule that has been saved`() = realTime()
    {
        // GIVEN
        val rule = aRule(1)
        repository.save(rule)

        // WHEN / THEN
        assertThat(repository.findById(rule.id)).isEqualTo(rule)
    }

    @Test
    fun `finds nothing for an unknown id`() = realTime()
    {
        assertThat(repository.findById(anId(1))).isNull()
    }

    @Test
    fun `lists nothing when nothing was saved`() = realTime()
    {
        assertThat(repository.findAll()).isEmpty()
    }

    @Test
    fun `gives back every field intact, an optional one included`() = realTime()
    {
        // GIVEN one with everything set, one with nothing optional
        val full = aRule(
            1,
            description = "Appartement",
            frequency = RecurrenceFrequency.YEARLY,
            interval = 2,
            endDate = LocalDate.of(2030, 9, 5),
            lastGeneratedDate = LocalDate.of(2026, 9, 5),
        )
        val bare = aRule(2, subcategory = null)
        listOf(full, bare).forEach { repository.save(it) }

        // WHEN / THEN
        assertThat(repository.findAll()).containsExactly(full, bare)
    }

    // An income and an expense are two different rules: what is read back is what was saved, never the default.
    @Test
    fun `gives back an income as an income and an expense as an expense`() = realTime()
    {
        // GIVEN
        val salary = aRule(1, category = RecordableTransactionCategory.INCOME, title = "Salaire", subcategory = null)
        val rent = aRule(2, category = RecordableTransactionCategory.EXPENSE)
        listOf(salary, rent).forEach { repository.save(it) }

        // WHEN / THEN
        assertThat(repository.findAll().map { it.category })
            .containsExactly(RecordableTransactionCategory.INCOME, RecordableTransactionCategory.EXPENSE)
        assertThat(repository.findById(salary.id)).isEqualTo(salary)
    }

    @Test
    fun `saving an existing rule again keeps its category`() = realTime()
    {
        // GIVEN
        val salary = aRule(1, category = RecordableTransactionCategory.INCOME, subcategory = null)
        repository.save(salary)

        // WHEN
        repository.save(salary.copy(amount = Money(210_000)))

        // THEN
        assertThat(repository.findById(salary.id)?.category).isEqualTo(RecordableTransactionCategory.INCOME)
    }

    @Test
    fun `keeps a very large amount`() = realTime()
    {
        // GIVEN 90 billion euros, in cents
        val rule = aRule(1, amount = 9_000_000_000_000L)

        // WHEN
        repository.save(rule)

        // THEN
        assertThat(repository.findById(rule.id)?.amount).isEqualTo(Money(9_000_000_000_000L))
    }

    @Test
    fun `keeps titles and descriptions with quotes, accents and line breaks intact`() = realTime()
    {
        // GIVEN
        val rule = aRule(1, title = "L'appartement \"principal\"; DROP TABLE recurring_transactions;--", description = "ligne 1\nligne 2 éè 🏠")

        // WHEN
        repository.save(rule)

        // THEN
        assertThat(repository.findById(rule.id)).isEqualTo(rule)
    }

    @Test
    fun `keeps a date far in the past or the future intact`() = realTime()
    {
        // GIVEN
        val rule = aRule(1, startDate = LocalDate.of(1999, 1, 1), endDate = LocalDate.of(2100, 12, 31))

        // WHEN
        repository.save(rule)

        // THEN
        assertThat(repository.findById(rule.id)).isEqualTo(rule)
    }

    // ------------------------------------------------------------------ saving again, order

    @Test
    fun `lists the rules in the order they were first saved`() = realTime()
    {
        // GIVEN
        val third = aRule(3)
        val first = aRule(1)
        val second = aRule(2)
        repository.save(third)
        repository.save(first)
        repository.save(second)

        // WHEN / THEN
        assertThat(repository.findAll()).containsExactly(third, first, second)
    }

    @Test
    fun `saving an existing rule replaces it where it stands`() = realTime()
    {
        // GIVEN
        repository.save(aRule(1))
        repository.save(aRule(2))
        repository.save(aRule(3))

        // WHEN the first is edited: amount, frequency, subcategory taken away
        val edited = aRule(1, amount = 90_000, frequency = RecurrenceFrequency.WEEKLY, subcategory = null)
        repository.save(edited)

        // THEN it did not move, and nothing was duplicated
        assertThat(repository.findAll().map { it.id }).containsExactly(anId(1), anId(2), anId(3))
        assertThat(repository.findById(anId(1))).isEqualTo(edited)
    }

    // ------------------------------------------------------------------ deleting

    @Test
    fun `deletes a rule and leaves the others`() = realTime()
    {
        // GIVEN
        repository.save(aRule(1))
        val kept = aRule(2)
        repository.save(kept)

        // WHEN
        repository.deleteById(anId(1))

        // THEN
        assertThat(repository.findAll()).containsExactly(kept)
        assertThat(repository.findById(anId(1))).isNull()
    }

    @Test
    fun `is silent about deleting an unknown rule`() = realTime()
    {
        // GIVEN
        val kept = aRule(1)
        repository.save(kept)

        // WHEN
        repository.deleteById(anId(2))

        // THEN
        assertThat(repository.findAll()).containsExactly(kept)
    }

    // ------------------------------------------------------------------ observing

    @Test
    fun `an observer first gets what is stored, in order`() = realTime()
    {
        // GIVEN
        val second = aRule(2)
        val first = aRule(1)
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
        repository.save(aRule(1))
        repository.save(aRule(2))
        repository.deleteById(anId(1))

        // WHEN / THEN
        assertThat(repository.observeAll().first().map { it.id }).containsExactly(anId(2))
    }

    @Test
    fun `an observer follows the repository as rules are added, edited and deleted`() = realTime()
    {
        // GIVEN a screen collecting the rules' amounts
        val latest = repository.observeAll().map { list -> list.map { it.amount.value } }.stateIn(this, SharingStarted.Eagerly, null)
        latest.awaitMatching { it != null && it.isEmpty() }

        // WHEN / THEN each change shows up
        repository.save(aRule(1, amount = 100))
        repository.save(aRule(2, amount = 200))
        latest.awaitMatching { it == listOf(100L, 200L) }

        repository.save(aRule(1, amount = 150))
        latest.awaitMatching { it == listOf(150L, 200L) }

        repository.deleteById(anId(1))
        latest.awaitMatching { it == listOf(200L) }

        coroutineContext.cancelChildren()
    }
}
