package com.kyovo.cents.application.usecase

import com.kyovo.cents.application.fakes.InMemorySubcategoryRepository
import com.kyovo.cents.application.fakes.aSubcategory
import com.kyovo.cents.application.fakes.aSubcategoryId
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.SubcategoryName
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * The subcategories are *observed*: the screens collect a Flow that emits the list now and again
 * whenever it changes, instead of reading it once and being told to read again. Whatever the order
 * they were created or stored in, the list is by name, ascending — so a renamed or newly created one
 * lands where it belongs. The order ignores case and accents' weight ("Éducation" sits between
 * "Divers" and "Loisirs", not after "Transport").
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ListSubcategoriesServiceTest
{
    private val repository = InMemorySubcategoryRepository()
    private val service = ListSubcategoriesService(repository)

    private fun aNamed(suffix: Int, name: String, kind: RecordableTransactionCategory = RecordableTransactionCategory.EXPENSE) =
        aSubcategory(
            id = aSubcategoryId("11111111-1111-1111-1111-11111111111$suffix"),
            kind = kind,
            name = SubcategoryName(name),
        )

    @Test
    fun `emits an empty list when there is no subcategory`() = runTest()
    {
        assertThat(service.observe().first()).isEmpty()
    }

    @Test
    fun `emits every subcategory ordered by name, whatever the stored order`() = runTest()
    {
        // GIVEN saved out of order
        val transport = aNamed(1, "Transport")
        val groceries = aNamed(2, "Alimentation", RecordableTransactionCategory.EXPENSE)
        val salary = aNamed(3, "Salaire", RecordableTransactionCategory.INCOME)
        repository.save(transport)
        repository.save(salary)
        repository.save(groceries)

        // WHEN / THEN
        assertThat(service.observe().first()).containsExactly(groceries, salary, transport)
    }

    @Test
    fun `emits only the subcategories of the given kind, ordered by name`() = runTest()
    {
        // GIVEN
        val transport = aNamed(1, "Transport")
        val groceries = aNamed(2, "Alimentation")
        val salary = aNamed(3, "Salaire", RecordableTransactionCategory.INCOME)
        repository.save(transport)
        repository.save(salary)
        repository.save(groceries)

        // WHEN / THEN
        assertThat(service.observe(RecordableTransactionCategory.EXPENSE).first()).containsExactly(groceries, transport)
        assertThat(service.observe(RecordableTransactionCategory.INCOME).first()).containsExactly(salary)
    }

    @Test
    fun `the order ignores case`() = runTest()
    {
        // GIVEN
        val bills = aNamed(1, "factures")
        val leisure = aNamed(2, "Loisirs")
        val groceries = aNamed(3, "ALIMENTATION")
        repository.save(leisure)
        repository.save(bills)
        repository.save(groceries)

        // WHEN / THEN
        assertThat(service.observe().first()).containsExactly(groceries, bills, leisure)
    }

    @Test
    fun `an accented letter sorts with its plain letter`() = runTest()
    {
        // GIVEN
        val education = aNamed(1, "Éducation")
        val leisure = aNamed(2, "Loisirs")
        val misc = aNamed(3, "Divers")
        repository.save(leisure)
        repository.save(education)
        repository.save(misc)

        // WHEN / THEN
        assertThat(service.observe().first()).containsExactly(misc, education, leisure)
    }

    // The same name under both kinds is legitimate ("Autre"); neither goes first by rule, so the
    // stored order decides — and stays, listing after listing.
    @Test
    fun `equal names keep the stored order`() = runTest()
    {
        // GIVEN
        val incomeOther = aNamed(1, "Autre", RecordableTransactionCategory.INCOME)
        val expenseOther = aNamed(2, "Autre", RecordableTransactionCategory.EXPENSE)
        repository.save(incomeOther)
        repository.save(expenseOther)

        // WHEN / THEN
        assertThat(service.observe().first()).containsExactly(incomeOther, expenseOther)
    }

    @Test
    fun `names that only differ by case or accents count as equal, and keep the stored order`() = runTest()
    {
        // GIVEN
        val lower = aNamed(1, "éducation", RecordableTransactionCategory.INCOME)
        val plain = aNamed(2, "Education", RecordableTransactionCategory.EXPENSE)
        val other = aNamed(3, "Divers")
        repository.save(lower)
        repository.save(plain)
        repository.save(other)

        // WHEN / THEN
        assertThat(service.observe().first()).containsExactly(other, lower, plain)
    }

    // ------------------------------------------------------------------ it keeps emitting

    @Test
    fun `emits again, in order, each time a subcategory is added, renamed or deleted`() = runTest()
    {
        // GIVEN a screen collecting the list
        val emissions = mutableListOf<List<String>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler))
        {
            service.observe().collect { list -> emissions += list.map { it.name.value } }
        }

        // WHEN
        repository.save(aNamed(1, "Transport"))
        repository.save(aNamed(2, "Alimentation"))
        repository.save(aNamed(1, "Voyages"))
        repository.deleteById(aNamed(2, "Alimentation").id)

        // THEN every state was seen, each sorted by name
        assertThat(emissions).containsExactly(
            emptyList(),
            listOf("Transport"),
            listOf("Alimentation", "Transport"),
            listOf("Alimentation", "Voyages"),
            listOf("Voyages"),
        )
    }

    @Test
    fun `a renamed subcategory moves to where its new name belongs`() = runTest()
    {
        // GIVEN
        repository.save(aNamed(1, "Alimentation"))
        repository.save(aNamed(2, "Transport"))

        // WHEN it is renamed after the other one
        repository.save(aNamed(1, "Zoo"))

        // THEN
        assertThat(service.observe().first().map { it.name.value }).containsExactly("Transport", "Zoo")
    }

    // A screen showing the expenses has nothing to redraw when an income is added.
    @Test
    fun `a change under the other kind does not make a kind's list emit again`() = runTest()
    {
        // GIVEN a screen collecting the expenses
        val emissions = mutableListOf<List<String>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler))
        {
            service.observe(RecordableTransactionCategory.EXPENSE).collect { list -> emissions += list.map { it.name.value } }
        }

        // WHEN an income is added, then an expense
        repository.save(aNamed(1, "Salaire", RecordableTransactionCategory.INCOME))
        repository.save(aNamed(2, "Transport"))

        // THEN only the expense made it emit
        assertThat(emissions).containsExactly(emptyList(), listOf("Transport"))
    }
}
