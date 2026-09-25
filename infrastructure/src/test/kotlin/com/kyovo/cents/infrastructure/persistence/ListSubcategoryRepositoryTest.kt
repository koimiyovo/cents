package com.kyovo.cents.infrastructure.persistence

import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.Subcategory
import com.kyovo.cents.domain.model.SubcategoryId
import com.kyovo.cents.domain.model.SubcategoryName
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class ListSubcategoryRepositoryTest
{
    private val repository = ListSubcategoryRepository()

    private fun aSubcategory(suffix: Int, name: String) = Subcategory(
        SubcategoryId(UUID.fromString("55555555-5555-5555-5555-55555555555$suffix")),
        RecordableTransactionCategory.EXPENSE,
        SubcategoryName(name),
        null
    )

    @Test
    fun `finds a subcategory that has been saved`() = runTest()
    {
        // GIVEN
        val groceries = aSubcategory(1, "Alimentation")
        repository.save(groceries)

        // WHEN / THEN
        assertThat(repository.findById(groceries.id)).isEqualTo(groceries)
    }

    @Test
    fun `finds nothing for an unknown id`() = runTest()
    {
        assertThat(repository.findById(aSubcategory(1, "Alimentation").id)).isNull()
    }

    @Test
    fun `saving an existing subcategory replaces it where it stands`() = runTest()
    {
        // GIVEN
        val first = aSubcategory(1, "Alimentation")
        val second = aSubcategory(2, "Transport")
        repository.save(first)
        repository.save(second)

        // WHEN
        val renamed = first.copy(name = SubcategoryName("Courses"))
        repository.save(renamed)

        // THEN
        assertThat(repository.findAll()).containsExactly(renamed, second)
    }

    @Test
    fun `deletes a subcategory, and is silent about an unknown one`() = runTest()
    {
        // GIVEN
        val first = aSubcategory(1, "Alimentation")
        val second = aSubcategory(2, "Transport")
        repository.save(first)
        repository.save(second)

        // WHEN
        repository.deleteById(first.id)

        // THEN
        assertThat(repository.findAll()).containsExactly(second)
        repository.deleteById(first.id)
    }

    // ------------------------------------------------------------------ observed

    @Test
    fun `an observer first gets what is stored`() = runTest()
    {
        // GIVEN
        val groceries = aSubcategory(1, "Alimentation")
        repository.save(groceries)

        // WHEN / THEN
        assertThat(repository.observeAll().first()).containsExactly(groceries)
    }

    @Test
    fun `an observer of an empty repository gets an empty list`() = runTest()
    {
        assertThat(repository.observeAll().first()).isEmpty()
    }

    @Test
    fun `an observer sees every change as it happens, in the stored order`() = runTest()
    {
        // GIVEN an observer
        val seen = mutableListOf<List<String>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler))
        {
            repository.observeAll().collect { list -> seen += list.map { it.name.value } }
        }

        // WHEN
        val first = aSubcategory(1, "Alimentation")
        val second = aSubcategory(2, "Transport")
        repository.save(first)
        repository.save(second)
        repository.save(first.copy(name = SubcategoryName("Courses")))
        repository.deleteById(first.id)

        // THEN
        assertThat(seen).containsExactly(
            emptyList(),
            listOf("Alimentation"),
            listOf("Alimentation", "Transport"),
            listOf("Courses", "Transport"),
            listOf("Transport"),
        )
    }

    @Test
    fun `an observer collecting late still gets the current state`() = runTest()
    {
        // GIVEN changes made before anyone observes
        repository.save(aSubcategory(1, "Alimentation"))
        repository.save(aSubcategory(2, "Transport"))
        repository.deleteById(aSubcategory(1, "Alimentation").id)

        // WHEN / THEN
        assertThat(repository.observeAll().first().map { it.name.value }).containsExactly("Transport")
    }
}
