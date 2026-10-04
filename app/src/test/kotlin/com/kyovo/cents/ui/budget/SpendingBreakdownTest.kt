package com.kyovo.cents.ui.budget

import com.kyovo.cents.domain.model.Emoji
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.Subcategory
import com.kyovo.cents.domain.model.SubcategoryId
import com.kyovo.cents.domain.model.SubcategoryName
import com.kyovo.cents.ui.subcategory.defaultSubcategoryEmoji
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * Turns the raw sums of [com.kyovo.cents.domain.port.input.GetSpendingBreakdownUseCase] into a pie: most
 * spent first, nothing shown for an empty subcategory, and only so many slices named before the rest fold
 * into one "other" share, so the chart stays readable whatever the number of subcategories in use.
 */
class SpendingBreakdownTest
{
    private fun aSubcategory(id: Int, name: String, emoji: String? = null) = Subcategory(
        SubcategoryId(UUID.fromString("aaaaaaaa-0000-0000-0000-%012d".format(id))),
        RecordableTransactionCategory.EXPENSE,
        SubcategoryName(name),
        emoji?.let { Emoji(it) },
    )

    private val groceries = aSubcategory(1, "Alimentation", "🛒")
    private val fuel = aSubcategory(2, "Transport")

    @Test
    fun `a subcategory with nothing spent does not appear`()
    {
        // WHEN
        val breakdown = spendingBreakdown(listOf(groceries, fuel), mapOf(groceries.id to Money(0)))

        // THEN
        assertThat(breakdown.slices).isEmpty()
    }

    @Test
    fun `the total is the sum spent, regardless of how many slices are shown`()
    {
        // WHEN
        val breakdown = spendingBreakdown(
            listOf(groceries, fuel),
            mapOf(groceries.id to Money(4_500), fuel.id to Money(1_500)),
        )

        // THEN
        assertThat(breakdown.total).isEqualTo(Money(6_000))
    }

    @Test
    fun `slices are ordered most spent first`()
    {
        // WHEN
        val breakdown = spendingBreakdown(
            listOf(groceries, fuel),
            mapOf(groceries.id to Money(1_500), fuel.id to Money(4_500)),
        )

        // THEN
        assertThat(breakdown.slices.map { it.amount }).containsExactly(Money(4_500), Money(1_500))
    }

    @Test
    fun `a slice's fraction is its share of the total`()
    {
        // WHEN
        val breakdown = spendingBreakdown(
            listOf(groceries, fuel),
            mapOf(groceries.id to Money(3_000), fuel.id to Money(1_000)),
        )

        // THEN
        assertThat(breakdown.slices[0].fraction).isCloseTo(0.75f, within(0.0001f))
        assertThat(breakdown.slices[1].fraction).isCloseTo(0.25f, within(0.0001f))
    }

    @Test
    fun `a named slice carries the subcategory's own emoji, or the default of its kind`()
    {
        // WHEN
        val breakdown = spendingBreakdown(
            listOf(groceries, fuel),
            mapOf(groceries.id to Money(1_000), fuel.id to Money(500)),
        )

        // THEN
        assertThat(breakdown.slices).containsExactly(
            SpendingSlice(SpendingSliceLabel.Named("Alimentation", "🛒"), Money(1_000), 2f / 3),
            SpendingSlice(
                SpendingSliceLabel.Named("Transport", defaultSubcategoryEmojiFor(fuel)),
                Money(500),
                1f / 3
            ),
        )
    }

    @Test
    fun `uncategorised spending is its own slice, ranked like any subcategory`()
    {
        // WHEN
        val breakdown = spendingBreakdown(
            listOf(groceries),
            mapOf(groceries.id to Money(1_000), null to Money(9_000)),
        )

        // THEN the biggest share, first, though it names no subcategory
        assertThat(breakdown.slices.first().label).isEqualTo(SpendingSliceLabel.Uncategorized)
        assertThat(breakdown.slices.first().amount).isEqualTo(Money(9_000))
    }

    @Test
    fun `up to six subcategories each keep their own slice`()
    {
        // GIVEN exactly six spending subcategories
        val subcategories = (1..6).map { aSubcategory(it, "Sub $it") }
        val spent: Map<SubcategoryId?, Money> =
            subcategories.mapIndexed { index, subcategory -> subcategory.id to Money((600 - index * 100).toLong()) }
                .toMap()

        // WHEN
        val breakdown = spendingBreakdown(subcategories, spent)

        // THEN none of them folds into "other"
        assertThat(breakdown.slices).hasSize(6)
        assertThat(breakdown.slices.map { it.label }).noneMatch { it == SpendingSliceLabel.Other }
    }

    @Test
    fun `past six subcategories, the smaller ones fold into one other slice`()
    {
        // GIVEN eight subcategories, spending strictly decreasing so the ranking is unambiguous
        val subcategories = (1..8).map { aSubcategory(it, "Sub $it") }
        val spent: Map<SubcategoryId?, Money> =
            subcategories.mapIndexed { index, subcategory -> subcategory.id to Money((800 - index * 100).toLong()) }
                .toMap()

        // WHEN
        val breakdown = spendingBreakdown(subcategories, spent)

        // THEN the six biggest keep their name, the two smallest (100 and 0 -- wait, none is zero) fold together
        assertThat(breakdown.slices).hasSize(7)
        assertThat(breakdown.slices.take(6).map { (it.label as SpendingSliceLabel.Named).name })
            .containsExactly("Sub 1", "Sub 2", "Sub 3", "Sub 4", "Sub 5", "Sub 6")
        val other = breakdown.slices.last()
        assertThat(other.label).isEqualTo(SpendingSliceLabel.Other)
        assertThat(other.amount).isEqualTo(Money(200 + 100)) // Sub 7 (200) + Sub 8 (100)
    }

    @Test
    fun `nothing spent anywhere gives an empty pie`()
    {
        // WHEN
        val breakdown = spendingBreakdown(listOf(groceries, fuel), emptyMap())

        // THEN
        assertThat(breakdown.total).isEqualTo(Money(0))
        assertThat(breakdown.slices).isEmpty()
    }

    private fun defaultSubcategoryEmojiFor(subcategory: Subcategory) =
        defaultSubcategoryEmoji(subcategory.kind)
}
