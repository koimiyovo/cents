package com.kyovo.cents.infrastructure.persistence

import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.Subcategory
import com.kyovo.cents.domain.model.SubcategoryId
import com.kyovo.cents.domain.model.SubcategoryName
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

class ListSubcategoryRepositoryTest
{
    private val repository = ListSubcategoryRepository()

    private fun aSubcategory(suffix: Int, name: String) = Subcategory(
        SubcategoryId(Uuid.parse("55555555-5555-5555-5555-55555555555$suffix")),
        RecordableTransactionCategory.EXPENSE,
        SubcategoryName(name),
        null
    )

    @Test
    fun `finds a subcategory that has been saved`()
    {
        // GIVEN
        val groceries = aSubcategory(1, "Alimentation")
        repository.save(groceries)

        // WHEN / THEN
        assertThat(repository.findById(groceries.id)).isEqualTo(groceries)
    }

    @Test
    fun `finds nothing for an unknown id`()
    {
        assertThat(repository.findById(aSubcategory(1, "Alimentation").id)).isNull()
    }

    @Test
    fun `saving an existing subcategory replaces it where it stands`()
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
    fun `deletes a subcategory, and is silent about an unknown one`()
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
        assertThatCode { repository.deleteById(first.id) }.doesNotThrowAnyException()
    }
}
