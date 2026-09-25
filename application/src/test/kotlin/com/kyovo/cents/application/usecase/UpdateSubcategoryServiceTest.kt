package com.kyovo.cents.application.usecase

import com.kyovo.cents.application.fakes.assertThatThrownBySuspending
import kotlinx.coroutines.test.runTest
import com.kyovo.cents.application.fakes.InMemorySubcategoryRepository
import com.kyovo.cents.application.fakes.aSubcategory
import com.kyovo.cents.application.fakes.aSubcategoryId
import com.kyovo.cents.application.fakes.anUpdateSubcategoryCommand
import com.kyovo.cents.domain.exception.DuplicateSubcategoryNameException
import com.kyovo.cents.domain.exception.SubcategoryNotFoundException
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.SubcategoryEmoji
import com.kyovo.cents.domain.model.SubcategoryName
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * A subcategory can be renamed and given another emoji (or none). Its kind is *not* editable: moving
 * an expense subcategory under income would leave every expense that uses it contradicting itself.
 */
class UpdateSubcategoryServiceTest
{
    private val id = aSubcategoryId()
    private val otherId = aSubcategoryId("66666666-6666-6666-6666-666666666666")
    private val repository = InMemorySubcategoryRepository()
    private val service = UpdateSubcategoryService(repository)

    @Test
    fun `renames the subcategory and changes its emoji`() = runTest()
    {
        // GIVEN
        repository.save(aSubcategory(id = id, name = SubcategoryName("Alimentation")))
        val cart = SubcategoryEmoji("🛒")

        // WHEN
        val result = service.update(
            anUpdateSubcategoryCommand(id = id, name = SubcategoryName("Courses"), emoji = cart),
        )

        // THEN
        val expected = aSubcategory(id = id, name = SubcategoryName("Courses"), emoji = cart)
        assertThat(result).isEqualTo(expected)
        assertThat(repository.saved).containsExactly(expected)
    }

    @Test
    fun `a null emoji removes the one it had`() = runTest()
    {
        // GIVEN
        repository.save(aSubcategory(id = id, emoji = SubcategoryEmoji("🛒")))

        // WHEN
        val result = service.update(anUpdateSubcategoryCommand(id = id, emoji = null))

        // THEN
        assertThat(result.emoji).isNull()
    }

    @Test
    fun `never changes the kind`() = runTest()
    {
        // GIVEN
        repository.save(aSubcategory(id = id, kind = RecordableTransactionCategory.INCOME))

        // WHEN
        val result = service.update(anUpdateSubcategoryCommand(id = id, name = SubcategoryName("Primes")))

        // THEN
        assertThat(result.kind).isEqualTo(RecordableTransactionCategory.INCOME)
    }

    @Test
    fun `throws when the subcategory does not exist`() = runTest()
    {
        // WHEN / THEN
        assertThatThrownBySuspending { service.update(anUpdateSubcategoryCommand(id = id)) }
            .isInstanceOf(SubcategoryNotFoundException::class.java)
    }

    @Test
    fun `throws when another subcategory of the same kind has that name, and changes nothing`() = runTest()
    {
        // GIVEN
        val renamed = aSubcategory(id = id, name = SubcategoryName("Alimentation"))
        val sibling = aSubcategory(id = otherId, name = SubcategoryName("Transport"))
        repository.save(renamed)
        repository.save(sibling)

        // WHEN
        assertThatThrownBySuspending {
            service.update(anUpdateSubcategoryCommand(id = id, name = SubcategoryName(" transport ")))
        }.isInstanceOf(DuplicateSubcategoryNameException::class.java)

        // THEN
        assertThat(repository.saved).containsExactly(renamed, sibling)
    }

    @Test
    fun `accepts a name used under the other kind`() = runTest()
    {
        // GIVEN
        repository.save(
            aSubcategory(id = id, kind = RecordableTransactionCategory.EXPENSE, name = SubcategoryName("Alimentation")),
        )
        repository.save(
            aSubcategory(id = otherId, kind = RecordableTransactionCategory.INCOME, name = SubcategoryName("Autre")),
        )

        // WHEN
        val result = service.update(anUpdateSubcategoryCommand(id = id, name = SubcategoryName("Autre")))

        // THEN
        assertThat(result.name).isEqualTo(SubcategoryName("Autre"))
    }

    // Changing only the case of its own name ("transport" → "Transport") must not be mistaken for
    // a clash with itself.
    @Test
    fun `accepts its own name, even with another case`() = runTest()
    {
        // GIVEN
        repository.save(aSubcategory(id = id, name = SubcategoryName("transport")))

        // WHEN
        val result = service.update(anUpdateSubcategoryCommand(id = id, name = SubcategoryName("Transport")))

        // THEN
        assertThat(result.name).isEqualTo(SubcategoryName("Transport"))
    }

    @Test
    fun `throws when another subcategory of the same kind has that name with other accents`() = runTest()
    {
        // GIVEN
        repository.save(aSubcategory(id = id, name = SubcategoryName("Alimentation")))
        repository.save(aSubcategory(id = otherId, name = SubcategoryName("Éducation")))

        // WHEN / THEN
        assertThatThrownBySuspending { service.update(anUpdateSubcategoryCommand(id = id, name = SubcategoryName("education"))) }
            .isInstanceOf(DuplicateSubcategoryNameException::class.java)
    }

    // Adding the accent it was missing is a change of its own name, not a clash with itself.
    @Test
    fun `accepts adding an accent to its own name`() = runTest()
    {
        // GIVEN
        repository.save(aSubcategory(id = id, name = SubcategoryName("Education")))

        // WHEN
        val result = service.update(anUpdateSubcategoryCommand(id = id, name = SubcategoryName("Éducation")))

        // THEN
        assertThat(result.name.value).isEqualTo("Éducation")
    }
}
