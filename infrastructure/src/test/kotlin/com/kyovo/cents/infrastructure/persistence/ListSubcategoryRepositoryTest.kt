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

    // What the contract (SubcategoryRepositoryContract) does not say, because a database may fold quick
    // changes into one emission: this list emits once per change, exactly.
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
}
