package com.kyovo.cents.infrastructure.persistence

import com.kyovo.cents.domain.model.Budget
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.Subcategory
import com.kyovo.cents.domain.model.SubcategoryId
import com.kyovo.cents.domain.model.SubcategoryName
import com.kyovo.cents.domain.port.output.BudgetRepository
import com.kyovo.cents.domain.port.output.SubcategoryRepository
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.YearMonth
import java.util.UUID

/**
 * The two repositories of one storage, built together: a budget points to a subcategory, and a database
 * (unlike a list) checks that what it points to exists. So the contract saves the subcategories it needs
 * through the same storage.
 */
class BudgetStores(
    val subcategories: SubcategoryRepository,
    val budgets: BudgetRepository,
)

/**
 * What every [BudgetRepository] must do, whatever it stores the budgets in (today Room): the port's
 * specification, written as tests. What only a database does — refusing a budget of an unknown
 * subcategory, deleting budgets along with their subcategory — is tested by the Room adapter's own class.
 *
 * A budget is identified by its subcategory *and* its month: one subcategory has a budget per month, and
 * saving again for the same month replaces the limit. The port has no "find": the budgets are read by
 * observing them, so that is how these tests read too. Like the other contracts they run in real time
 * and wait for the state they expect instead of counting emissions.
 */
abstract class BudgetRepositoryContract
{
    /** Fresh, empty storage. Called before each test. */
    protected abstract fun createStores(): BudgetStores

    protected lateinit var stores: BudgetStores

    private val groceries = aSubcategory(1, "Alimentation")
    private val transport = aSubcategory(2, "Transport")

    private val august = YearMonth.of(2026, 8)
    private val september = YearMonth.of(2026, 9)

    @BeforeEach
    fun createTheStores()
    {
        stores = createStores()
        realTime()
        {
            stores.subcategories.save(groceries)
            stores.subcategories.save(transport)
        }
    }

    private val repository get() = stores.budgets

    private fun aSubcategory(suffix: Int, name: String) =
        Subcategory(
            SubcategoryId(UUID.fromString("55555555-5555-5555-5555-55555555555$suffix")),
            RecordableTransactionCategory.EXPENSE,
            SubcategoryName(name),
            null,
        )

    private fun aBudget(subcategory: Subcategory, month: YearMonth, cents: Long) =
        Budget(subcategory.id, month, Money(cents))

    private suspend fun stored() = repository.observeAll().first()

    // ------------------------------------------------------------------ reading what was saved

    @Test
    fun `an observer of an empty repository gets an empty list`() = realTime()
    {
        assertThat(stored()).isEmpty()
    }

    @Test
    fun `gives back a budget that has been saved`() = realTime()
    {
        // GIVEN
        val budget = aBudget(groceries, september, 30_000)

        // WHEN
        repository.save(budget)

        // THEN
        assertThat(stored()).containsExactly(budget)
    }

    @Test
    fun `lists the budgets in the order they were first saved`() = realTime()
    {
        // GIVEN
        val first = aBudget(transport, september, 10_000)
        val second = aBudget(groceries, august, 30_000)
        val third = aBudget(groceries, september, 30_000)

        // WHEN
        repository.save(first)
        repository.save(second)
        repository.save(third)

        // THEN
        assertThat(stored()).containsExactly(first, second, third)
    }

    // What makes a budget "the same" one is its subcategory and its month, together.
    @Test
    fun `saving again for the same subcategory and month replaces the limit where it stands`() = realTime()
    {
        // GIVEN
        repository.save(aBudget(groceries, september, 30_000))
        repository.save(aBudget(transport, september, 10_000))

        // WHEN
        repository.save(aBudget(groceries, september, 45_000))

        // THEN it did not move, and nothing was duplicated
        assertThat(stored()).containsExactly(
            aBudget(groceries, september, 45_000),
            aBudget(transport, september, 10_000),
        )
    }

    @Test
    fun `keeps a budget for each month of the same subcategory`() = realTime()
    {
        // GIVEN
        val inAugust = aBudget(groceries, august, 30_000)
        val inSeptember = aBudget(groceries, september, 45_000)

        // WHEN
        repository.save(inAugust)
        repository.save(inSeptember)

        // THEN changing one month never rewrote the other
        assertThat(stored()).containsExactly(inAugust, inSeptember)
    }

    @Test
    fun `keeps a budget for each subcategory of the same month`() = realTime()
    {
        // GIVEN
        val forGroceries = aBudget(groceries, september, 30_000)
        val forTransport = aBudget(transport, september, 10_000)

        // WHEN
        repository.save(forGroceries)
        repository.save(forTransport)

        // THEN
        assertThat(stored()).containsExactly(forGroceries, forTransport)
    }

    @Test
    fun `gives back the month of a budget intact, at the turn of a year and far from today`() = realTime()
    {
        // GIVEN
        val months = listOf(YearMonth.of(1999, 1), YearMonth.of(2026, 12), YearMonth.of(2027, 1), YearMonth.of(2100, 6))
        val budgets = months.map { aBudget(groceries, it, 30_000) }

        // WHEN
        budgets.forEach { repository.save(it) }

        // THEN
        assertThat(stored()).containsExactlyElementsOf(budgets)
    }

    @Test
    fun `gives back a limit of the smallest and of a very large size intact`() = realTime()
    {
        // GIVEN one cent, and a million euros
        val smallest = aBudget(groceries, august, 1)
        val largest = aBudget(groceries, september, 100_000_000)

        // WHEN
        repository.save(smallest)
        repository.save(largest)

        // THEN
        assertThat(stored()).containsExactly(smallest, largest)
    }

    // ------------------------------------------------------------------ deleting

    @Test
    fun `deletes the budgets of a subcategory, for every month, and leaves the others`() = realTime()
    {
        // GIVEN
        val transportBudget = aBudget(transport, september, 10_000)
        repository.save(aBudget(groceries, august, 30_000))
        repository.save(transportBudget)
        repository.save(aBudget(groceries, september, 45_000))

        // WHEN
        repository.deleteBySubcategoryId(groceries.id)

        // THEN
        assertThat(stored()).containsExactly(transportBudget)
    }

    @Test
    fun `is silent about deleting the budgets of a subcategory that has none`() = realTime()
    {
        // GIVEN
        val transportBudget = aBudget(transport, september, 10_000)
        repository.save(transportBudget)

        // WHEN
        repository.deleteBySubcategoryId(groceries.id)

        // THEN
        assertThat(stored()).containsExactly(transportBudget)
    }

    @Test
    fun `a budget can be set again once the budgets of its subcategory were deleted`() = realTime()
    {
        // GIVEN
        repository.save(aBudget(groceries, september, 30_000))
        repository.deleteBySubcategoryId(groceries.id)

        // WHEN
        repository.save(aBudget(groceries, september, 20_000))

        // THEN
        assertThat(stored()).containsExactly(aBudget(groceries, september, 20_000))
    }

    // ------------------------------------------------------------------ observing

    @Test
    fun `an observer collecting late still gets the current state`() = realTime()
    {
        // GIVEN changes made before anyone observes
        repository.save(aBudget(groceries, september, 30_000))
        repository.save(aBudget(transport, september, 10_000))
        repository.deleteBySubcategoryId(groceries.id)

        // WHEN / THEN
        assertThat(stored()).containsExactly(aBudget(transport, september, 10_000))
    }

    @Test
    fun `an observer follows the repository as budgets are set, changed and deleted`() = realTime()
    {
        // GIVEN a screen collecting the budgets
        val latest = repository.observeAll().stateIn(this, SharingStarted.Eagerly, null).filterNotNull()
        latest.awaitMatching { it.isEmpty() }

        // WHEN / THEN each change shows up, in stored order
        val forGroceries = aBudget(groceries, september, 30_000)
        repository.save(forGroceries)
        latest.awaitMatching { it == listOf(forGroceries) }

        val forTransport = aBudget(transport, september, 10_000)
        repository.save(forTransport)
        latest.awaitMatching { it == listOf(forGroceries, forTransport) }

        val raised = aBudget(groceries, september, 45_000)
        repository.save(raised)
        latest.awaitMatching { it == listOf(raised, forTransport) }

        repository.deleteBySubcategoryId(groceries.id)
        latest.awaitMatching { it == listOf(forTransport) }

        coroutineContext.cancelChildren()
    }
}
