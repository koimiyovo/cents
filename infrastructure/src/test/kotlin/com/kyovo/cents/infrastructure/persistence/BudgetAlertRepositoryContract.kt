package com.kyovo.cents.infrastructure.persistence

import com.kyovo.cents.domain.model.BudgetAlert
import com.kyovo.cents.domain.model.BudgetAlertLevel
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.Subcategory
import com.kyovo.cents.domain.model.SubcategoryId
import com.kyovo.cents.domain.model.SubcategoryName
import com.kyovo.cents.domain.port.output.BudgetAlertRepository
import com.kyovo.cents.domain.port.output.SubcategoryRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.YearMonth
import java.util.UUID

/**
 * The two repositories of one storage, built together: an alert points to a subcategory, and a database
 * (unlike a list) checks that what it points to exists.
 */
class BudgetAlertStores(
    val subcategories: SubcategoryRepository,
    val budgetAlerts: BudgetAlertRepository,
)

/**
 * What every [BudgetAlertRepository] must do, whatever it stores the alerts in (today Room): the port's
 * specification, written as tests. What only a database does — refusing an alert of an unknown subcategory,
 * deleting alerts along with their subcategory — is tested by the Room adapter's own class.
 *
 * Unlike the other repositories, there is no `observeAll`: nothing shows this on screen, a periodic check
 * only ever asks "what was already reported this month", once, so every method here is a plain read or write.
 */
abstract class BudgetAlertRepositoryContract
{
    /** Fresh, empty storage. Called before each test. */
    protected abstract fun createStores(): BudgetAlertStores

    protected lateinit var stores: BudgetAlertStores

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

    private val repository get() = stores.budgetAlerts

    private fun aSubcategory(suffix: Int, name: String) =
        Subcategory(
            SubcategoryId(UUID.fromString("55555555-5555-5555-5555-55555555555$suffix")),
            RecordableTransactionCategory.EXPENSE,
            SubcategoryName(name),
            null,
        )

    private fun anAlert(subcategory: Subcategory, month: YearMonth, level: BudgetAlertLevel = BudgetAlertLevel.CLOSE_TO_LIMIT) =
        BudgetAlert(subcategory.id, month, level)

    // ------------------------------------------------------------------ recording and reading back

    @Test
    fun `a month with nothing recorded gives back an empty set`() = realTime()
    {
        assertThat(repository.findByMonth(september)).isEmpty()
    }

    @Test
    fun `gives back an alert that has been recorded`() = realTime()
    {
        // GIVEN
        val alert = anAlert(groceries, september)

        // WHEN
        repository.record(alert)

        // THEN
        assertThat(repository.findByMonth(september)).containsExactly(alert)
    }

    @Test
    fun `only gives back the alerts of the month asked for`() = realTime()
    {
        // GIVEN
        repository.record(anAlert(groceries, august))
        val inSeptember = anAlert(groceries, september)
        repository.record(inSeptember)

        // WHEN / THEN
        assertThat(repository.findByMonth(september)).containsExactly(inSeptember)
    }

    @Test
    fun `keeps the alerts of every subcategory in the same month`() = realTime()
    {
        // GIVEN
        val forGroceries = anAlert(groceries, september)
        val forTransport = anAlert(transport, september)

        // WHEN
        repository.record(forGroceries)
        repository.record(forTransport)

        // THEN
        assertThat(repository.findByMonth(september)).containsExactlyInAnyOrder(forGroceries, forTransport)
    }

    // Close and over are two distinct crossings of the same budget: reporting one does not stand in for
    // the other.
    @Test
    fun `keeps close and over as two separate alerts of the same subcategory and month`() = realTime()
    {
        // GIVEN
        val close = anAlert(groceries, september, BudgetAlertLevel.CLOSE_TO_LIMIT)
        val over = anAlert(groceries, september, BudgetAlertLevel.OVER)

        // WHEN
        repository.record(close)
        repository.record(over)

        // THEN
        assertThat(repository.findByMonth(september)).containsExactlyInAnyOrder(close, over)
    }

    @Test
    fun `recording the same crossing again does not duplicate it`() = realTime()
    {
        // GIVEN
        val alert = anAlert(groceries, september)
        repository.record(alert)

        // WHEN
        repository.record(alert)

        // THEN
        assertThat(repository.findByMonth(september)).containsExactly(alert)
    }

    // ------------------------------------------------------------------ deleting

    @Test
    fun `deletes the alerts of a subcategory, for every month, and leaves the others`() = realTime()
    {
        // GIVEN
        val transportAlert = anAlert(transport, september)
        repository.record(anAlert(groceries, august))
        repository.record(transportAlert)
        repository.record(anAlert(groceries, september))

        // WHEN
        repository.deleteBySubcategoryId(groceries.id)

        // THEN
        assertThat(repository.findByMonth(august)).isEmpty()
        assertThat(repository.findByMonth(september)).containsExactly(transportAlert)
    }

    @Test
    fun `is silent about deleting the alerts of a subcategory that has none`() = realTime()
    {
        // GIVEN
        val transportAlert = anAlert(transport, september)
        repository.record(transportAlert)

        // WHEN
        repository.deleteBySubcategoryId(groceries.id)

        // THEN
        assertThat(repository.findByMonth(september)).containsExactly(transportAlert)
    }

    // A crossing that no longer holds (the expense behind it was deleted or corrected) is forgotten one
    // alert at a time: the other level of the same budget, and other months, stay as they were.
    @Test
    fun `deletes one alert and leaves the other level, the other months and the other subcategories`() = realTime()
    {
        // GIVEN
        val over = anAlert(groceries, september, BudgetAlertLevel.OVER)
        val close = anAlert(groceries, september, BudgetAlertLevel.CLOSE_TO_LIMIT)
        val inAugust = anAlert(groceries, august, BudgetAlertLevel.OVER)
        val forTransport = anAlert(transport, september, BudgetAlertLevel.OVER)
        listOf(over, close, inAugust, forTransport).forEach { repository.record(it) }

        // WHEN
        repository.delete(over)

        // THEN
        assertThat(repository.findByMonth(september)).containsExactlyInAnyOrder(close, forTransport)
        assertThat(repository.findByMonth(august)).containsExactly(inAugust)
    }

    @Test
    fun `is silent about deleting an alert that was never recorded`() = realTime()
    {
        // GIVEN
        val recorded = anAlert(groceries, september, BudgetAlertLevel.CLOSE_TO_LIMIT)
        repository.record(recorded)

        // WHEN
        repository.delete(anAlert(groceries, september, BudgetAlertLevel.OVER))

        // THEN
        assertThat(repository.findByMonth(september)).containsExactly(recorded)
    }

    @Test
    fun `a deleted crossing can be recorded again`() = realTime()
    {
        // GIVEN
        val alert = anAlert(groceries, september)
        repository.record(alert)
        repository.delete(alert)
        assertThat(repository.findByMonth(september)).isEmpty()

        // WHEN
        repository.record(alert)

        // THEN
        assertThat(repository.findByMonth(september)).containsExactly(alert)
    }

    @Test
    fun `a crossing can be recorded again once the alerts of its subcategory were deleted`() = realTime()
    {
        // GIVEN
        val alert = anAlert(groceries, september)
        repository.record(alert)
        repository.deleteBySubcategoryId(groceries.id)

        // WHEN
        repository.record(alert)

        // THEN
        assertThat(repository.findByMonth(september)).containsExactly(alert)
    }
}
