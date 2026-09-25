package com.kyovo.cents.infrastructure.persistence

import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.Subcategory
import com.kyovo.cents.domain.model.SubcategoryEmoji
import com.kyovo.cents.domain.model.SubcategoryId
import com.kyovo.cents.domain.model.SubcategoryName
import com.kyovo.cents.domain.port.output.SubcategoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

/**
 * What every [SubcategoryRepository] must do, whatever it stores the subcategories in: the port's
 * specification, written as tests. An adapter has a small test class extending this one and saying how
 * to build itself (today Room's, `RoomSubcategoryRepositoryContractTest`); another adapter that passes
 * the same tests could replace it without the services above noticing.
 *
 * The tests run in real time (`runBlocking`, not `runTest`): a database answers on its own threads,
 * and a virtual clock would run out while it does. For the same reason the observation tests wait
 * until the wanted list shows up, instead of counting the emissions: a database may fold several
 * quick changes into one emission.
 */
abstract class SubcategoryRepositoryContract
{
    /** A fresh, empty repository. Called before each test. */
    protected abstract fun createRepository(): SubcategoryRepository

    protected lateinit var repository: SubcategoryRepository

    @BeforeEach
    fun createTheRepository()
    {
        repository = createRepository()
    }

    private val groceries = aSubcategory(1, RecordableTransactionCategory.EXPENSE, "Alimentation", "🛒")
    private val transport = aSubcategory(2, RecordableTransactionCategory.EXPENSE, "Transport", null)
    private val salary = aSubcategory(3, RecordableTransactionCategory.INCOME, "Salaire", "💰")

    private fun aSubcategory(suffix: Int, kind: RecordableTransactionCategory, name: String, emoji: String?) =
        Subcategory(
            SubcategoryId(UUID.fromString("55555555-5555-5555-5555-55555555555$suffix")),
            kind,
            SubcategoryName(name),
            emoji?.let { SubcategoryEmoji(it) },
        )

    /** Runs the test in real time. */
    protected fun realTime(block: suspend CoroutineScope.() -> Unit): Unit = runBlocking(block = block)

    // ------------------------------------------------------------------ reading what was saved

    @Test
    fun `finds a subcategory that has been saved`() = realTime()
    {
        // GIVEN
        repository.save(groceries)

        // WHEN / THEN
        assertThat(repository.findById(groceries.id)).isEqualTo(groceries)
    }

    @Test
    fun `finds nothing for an unknown id`() = realTime()
    {
        assertThat(repository.findById(groceries.id)).isNull()
    }

    @Test
    fun `lists nothing when nothing was saved`() = realTime()
    {
        assertThat(repository.findAll()).isEmpty()
    }

    @Test
    fun `lists the subcategories in the order they were first saved`() = realTime()
    {
        // GIVEN
        repository.save(transport)
        repository.save(groceries)
        repository.save(salary)

        // WHEN / THEN
        assertThat(repository.findAll()).containsExactly(transport, groceries, salary)
    }

    @Test
    fun `saving an existing subcategory replaces it where it stands`() = realTime()
    {
        // GIVEN
        repository.save(groceries)
        repository.save(transport)
        repository.save(salary)

        // WHEN it is renamed and given another emoji
        val renamed = groceries.copy(name = SubcategoryName("Courses"), emoji = SubcategoryEmoji("🥖"))
        repository.save(renamed)

        // THEN it did not move, and nothing was duplicated
        assertThat(repository.findAll()).containsExactly(renamed, transport, salary)
        assertThat(repository.findById(groceries.id)).isEqualTo(renamed)
    }

    @Test
    fun `an emoji can be taken away`() = realTime()
    {
        // GIVEN
        repository.save(groceries)

        // WHEN
        repository.save(groceries.copy(emoji = null))

        // THEN
        assertThat(repository.findById(groceries.id)?.emoji).isNull()
    }

    @Test
    fun `gives back both kinds of subcategory`() = realTime()
    {
        // GIVEN
        repository.save(groceries)
        repository.save(salary)

        // WHEN / THEN
        assertThat(repository.findById(groceries.id)?.kind).isEqualTo(RecordableTransactionCategory.EXPENSE)
        assertThat(repository.findById(salary.id)?.kind).isEqualTo(RecordableTransactionCategory.INCOME)
    }

    // The store must not judge the names: whether two names clash is a rule of the services.
    @Test
    fun `stores two subcategories with the same name`() = realTime()
    {
        // GIVEN "Autre" under both kinds
        val expenseOther = aSubcategory(4, RecordableTransactionCategory.EXPENSE, "Autre", null)
        val incomeOther = aSubcategory(5, RecordableTransactionCategory.INCOME, "Autre", null)

        // WHEN
        repository.save(expenseOther)
        repository.save(incomeOther)

        // THEN
        assertThat(repository.findAll()).containsExactly(expenseOther, incomeOther)
    }

    @Test
    fun `keeps names and emojis with quotes, accents and multi-part emojis intact`() = realTime()
    {
        // GIVEN
        val tricky = aSubcategory(
            4,
            RecordableTransactionCategory.EXPENSE,
            "L'école d'Élise \"bio\"; DROP TABLE x;--",
            "👨‍👩‍👧‍👦",
        )

        // WHEN
        repository.save(tricky)

        // THEN
        assertThat(repository.findById(tricky.id)).isEqualTo(tricky)
    }

    @Test
    fun `keeps a name of the longest length`() = realTime()
    {
        // GIVEN
        val longest = aSubcategory(4, RecordableTransactionCategory.EXPENSE, "x".repeat(SubcategoryName.MAX_LENGTH), null)

        // WHEN
        repository.save(longest)

        // THEN
        assertThat(repository.findById(longest.id)).isEqualTo(longest)
    }

    // ------------------------------------------------------------------ deleting

    @Test
    fun `deletes a subcategory and leaves the others`() = realTime()
    {
        // GIVEN
        repository.save(groceries)
        repository.save(transport)

        // WHEN
        repository.deleteById(groceries.id)

        // THEN
        assertThat(repository.findAll()).containsExactly(transport)
        assertThat(repository.findById(groceries.id)).isNull()
    }

    @Test
    fun `is silent about deleting an unknown subcategory`() = realTime()
    {
        // GIVEN
        repository.save(transport)

        // WHEN
        repository.deleteById(groceries.id)

        // THEN
        assertThat(repository.findAll()).containsExactly(transport)
    }

    // ------------------------------------------------------------------ observing

    @Test
    fun `an observer first gets what is stored, in order`() = realTime()
    {
        // GIVEN
        repository.save(transport)
        repository.save(groceries)

        // WHEN / THEN
        assertThat(repository.observeAll().first()).containsExactly(transport, groceries)
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
        repository.save(groceries)
        repository.save(transport)
        repository.deleteById(groceries.id)

        // WHEN / THEN
        assertThat(repository.observeAll().first()).containsExactly(transport)
    }

    @Test
    fun `an observer follows the repository as subcategories are added, changed and deleted`() = realTime()
    {
        // GIVEN a screen collecting the subcategories
        val latest = repository.observeAll().stateIn(this, SharingStarted.Eagerly, null)
        latest.filterNotNullFirst { it.isEmpty() }

        // WHEN / THEN each change shows up, in stored order
        repository.save(groceries)
        latest.filterNotNullFirst { it == listOf(groceries) }

        repository.save(transport)
        latest.filterNotNullFirst { it == listOf(groceries, transport) }

        val renamed = groceries.copy(name = SubcategoryName("Courses"))
        repository.save(renamed)
        latest.filterNotNullFirst { it == listOf(renamed, transport) }

        repository.deleteById(renamed.id)
        latest.filterNotNullFirst { it == listOf(transport) }

        coroutineContext.cancelChildren()
    }

    private suspend fun StateFlow<List<Subcategory>?>.filterNotNullFirst(
        matches: (List<Subcategory>) -> Boolean,
    ): List<Subcategory> = withTimeout(5.seconds) { first { it != null && matches(it) }!! }
}
