package com.kyovo.cents.ui.subcategory

import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.Subcategory
import com.kyovo.cents.domain.model.SubcategoryEmoji
import com.kyovo.cents.domain.model.SubcategoryId
import com.kyovo.cents.domain.model.SubcategoryName
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

private fun aRow(kind: RecordableTransactionCategory, emoji: String?) = SubcategoryRow(
    Subcategory(SubcategoryId(UUID.randomUUID()), kind, SubcategoryName("Nom"), emoji?.let { SubcategoryEmoji(it) }),
    transactionCount = 0,
)

/**
 * The icon of a row of the management screen: the subcategory's own emoji, or — for one created
 * without — a default that says which kind it is, so no row is left with a blank icon.
 */
class SubcategoryRowEmojiTest
{
    @Test
    fun `a subcategory shows its own emoji`()
    {
        assertThat(aRow(RecordableTransactionCategory.EXPENSE, "🛒").displayEmoji()).isEqualTo("🛒")
        assertThat(aRow(RecordableTransactionCategory.INCOME, "🎁").displayEmoji()).isEqualTo("🎁")
    }

    @Test
    fun `an expense without one shows the card`()
    {
        assertThat(aRow(RecordableTransactionCategory.EXPENSE, null).displayEmoji()).isEqualTo("💳")
    }

    @Test
    fun `an income without one shows the money bag`()
    {
        assertThat(aRow(RecordableTransactionCategory.INCOME, null).displayEmoji()).isEqualTo("💰")
    }

    @Test
    fun `the two defaults differ`()
    {
        assertThat(defaultSubcategoryEmoji(RecordableTransactionCategory.EXPENSE))
            .isNotEqualTo(defaultSubcategoryEmoji(RecordableTransactionCategory.INCOME))
    }

    @Test
    fun `whatever the row, the icon is never blank`()
    {
        val rows = RecordableTransactionCategory.entries.flatMap { kind ->
            listOf(aRow(kind, null), aRow(kind, "🛒"))
        }

        assertThat(rows.map { it.displayEmoji() }).noneMatch { it.isBlank() }
    }
}
