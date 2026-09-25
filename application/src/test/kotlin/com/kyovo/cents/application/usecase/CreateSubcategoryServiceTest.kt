package com.kyovo.cents.application.usecase

import com.kyovo.cents.application.fakes.assertThatThrownBySuspending
import kotlinx.coroutines.test.runTest
import com.kyovo.cents.application.fakes.FixedSubcategoryIdGenerator
import com.kyovo.cents.application.fakes.InMemorySubcategoryRepository
import com.kyovo.cents.application.fakes.aCreateSubcategoryCommand
import com.kyovo.cents.application.fakes.aSubcategory
import com.kyovo.cents.application.fakes.aSubcategoryId
import com.kyovo.cents.domain.exception.DuplicateSubcategoryNameException
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.SubcategoryEmoji
import com.kyovo.cents.domain.model.SubcategoryName
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * Categories are fixed — they are the transaction types (income, expense), and a transfer has none —
 * so what a user manages is the *subcategories* under them. Each one belongs to a single kind, chosen
 * when it is created and never changed afterwards.
 */
class CreateSubcategoryServiceTest
{
    private val generatedId = aSubcategoryId()
    private val existingId = aSubcategoryId("11111111-1111-1111-1111-111111111111")
    private val repository = InMemorySubcategoryRepository()
    private val service = CreateSubcategoryService(repository, FixedSubcategoryIdGenerator(generatedId))

    @Test
    fun `creates a subcategory with the given kind and name and a generated id, and saves it`() = runTest()
    {
        // WHEN
        val result = service.create(aCreateSubcategoryCommand())

        // THEN
        val expected = aSubcategory(id = generatedId)
        assertThat(result).isEqualTo(expected)
        assertThat(repository.saved).containsExactly(expected)
    }

    @Test
    fun `keeps the emoji when one is given, and has none otherwise`() = runTest()
    {
        // GIVEN
        val cart = SubcategoryEmoji("🛒")

        // WHEN
        val withEmoji = service.create(aCreateSubcategoryCommand(emoji = cart))
        val without = service.create(aCreateSubcategoryCommand(name = SubcategoryName("Transport")))

        // THEN
        assertThat(withEmoji.emoji).isEqualTo(cart)
        assertThat(without.emoji).isNull()
    }

    @ParameterizedTest
    @ValueSource(strings = ["Alimentation", "alimentation", "ALIMENTATION", "  Alimentation  "])
    fun `throws when a subcategory of the same kind already has that name, whatever the case or spaces`(name: String) = runTest()
    {
        // GIVEN
        repository.save(aSubcategory(id = existingId))

        // WHEN / THEN
        assertThatThrownBySuspending { service.create(aCreateSubcategoryCommand(name = SubcategoryName(name))) }
            .isInstanceOf(DuplicateSubcategoryNameException::class.java)
    }

    @Test
    fun `saves nothing when the name is already taken`() = runTest()
    {
        // GIVEN
        val existing = aSubcategory(id = existingId)
        repository.save(existing)

        // WHEN
        assertThatThrownBySuspending { service.create(aCreateSubcategoryCommand()) }
            .isInstanceOf(DuplicateSubcategoryNameException::class.java)

        // THEN
        assertThat(repository.saved).containsExactly(existing)
    }

    // "Autre" can perfectly be both an expense and an income subcategory: names only have to be
    // unique among the subcategories of the same kind.
    @Test
    fun `accepts a name already used under the other kind`() = runTest()
    {
        // GIVEN
        repository.save(aSubcategory(id = existingId, kind = RecordableTransactionCategory.INCOME))

        // WHEN
        val created = service.create(aCreateSubcategoryCommand(kind = RecordableTransactionCategory.EXPENSE))

        // THEN
        assertThat(repository.saved).hasSize(2)
        assertThat(repository.findById(created.id)).isEqualTo(created)
    }

    // Uniqueness ignores accents as well as case: "Education" and "Éducation" are the same name.
    @ParameterizedTest
    @ValueSource(strings = ["Education", "éducation", "EDUCATION", "E\u0301ducation"])
    fun `throws when a subcategory of the same kind has the same name with other accents`(name: String) = runTest()
    {
        // GIVEN
        repository.save(aSubcategory(id = existingId, name = SubcategoryName("Éducation")))

        // WHEN / THEN
        assertThatThrownBySuspending { service.create(aCreateSubcategoryCommand(name = SubcategoryName(name))) }
            .isInstanceOf(DuplicateSubcategoryNameException::class.java)
    }

    @Test
    fun `throws when the existing name has no accent and the new one has`() = runTest()
    {
        // GIVEN
        repository.save(aSubcategory(id = existingId, name = SubcategoryName("Education")))

        // WHEN / THEN
        assertThatThrownBySuspending { service.create(aCreateSubcategoryCommand(name = SubcategoryName("Éducation"))) }
            .isInstanceOf(DuplicateSubcategoryNameException::class.java)
    }
}
