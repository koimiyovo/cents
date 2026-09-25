package com.kyovo.cents.infrastructure.persistence.room

import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.Subcategory
import com.kyovo.cents.domain.model.SubcategoryEmoji
import com.kyovo.cents.domain.model.SubcategoryId
import com.kyovo.cents.domain.model.SubcategoryName
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * The database row is its own type, apart from the domain's [Subcategory]: the domain does not know
 * Room, and the table can change without the domain noticing. Two functions convert between them.
 */
class SubcategoryEntityMapperTest
{
    private val uuid = UUID.fromString("55555555-5555-5555-5555-555555555551")

    private val groceries = Subcategory(
        SubcategoryId(uuid),
        RecordableTransactionCategory.EXPENSE,
        SubcategoryName("Alimentation"),
        SubcategoryEmoji("🛒"),
    )

    @Test
    fun `a subcategory becomes a row of plain values`()
    {
        // WHEN
        val entity = groceries.toEntity()

        // THEN
        assertThat(entity).isEqualTo(SubcategoryEntity(uuid, "EXPENSE", "Alimentation", "🛒"))
    }

    @Test
    fun `an income is stored under its own kind`()
    {
        assertThat(groceries.copy(kind = RecordableTransactionCategory.INCOME).toEntity().kind).isEqualTo("INCOME")
    }

    @Test
    fun `a subcategory without an emoji has no emoji in its row`()
    {
        assertThat(groceries.copy(emoji = null).toEntity().emoji).isNull()
    }

    @Test
    fun `a row becomes the subcategory it came from`()
    {
        assertThat(groceries.toEntity().toDomain()).isEqualTo(groceries)
    }

    @Test
    fun `a row without an emoji becomes a subcategory without an emoji`()
    {
        assertThat(SubcategoryEntity(uuid, "INCOME", "Salaire", null).toDomain())
            .isEqualTo(Subcategory(SubcategoryId(uuid), RecordableTransactionCategory.INCOME, SubcategoryName("Salaire"), null))
    }

    // The kind is stored as text, so a database can hold a kind this version of the app has never heard of.
    @Test
    fun `a row with an unknown kind is refused instead of being guessed`()
    {
        assertThatThrownBy { SubcategoryEntity(uuid, "SAVINGS", "Salaire", null).toDomain() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("SAVINGS")
    }
}
