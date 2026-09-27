package com.kyovo.cents.ui.budget

import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.Subcategory
import com.kyovo.cents.domain.model.SubcategoryId
import com.kyovo.cents.ui.subcategory.defaultSubcategoryEmoji

/** How a slice of the spending pie should be labelled — carries no string: the screen picks the wording. */
sealed interface SpendingSliceLabel
{
    data class Named(val name: String, val emoji: String) : SpendingSliceLabel
    data object Uncategorized : SpendingSliceLabel
    data object Other : SpendingSliceLabel
}

/** One slice of the pie: a share of the month's expenses, already sized as a fraction of the [total]. */
data class SpendingSlice(val label: SpendingSliceLabel, val amount: Money, val fraction: Float)

/** The whole pie: [total] spent this month, split into [slices] — most spent first. */
data class SpendingBreakdown(val total: Money, val slices: List<SpendingSlice>)

/** Past this many named slices, the rest are folded into one [SpendingSliceLabel.Other] slice: a pie reads
 * at a glance only up to a handful of segments. */
private const val MAX_NAMED_SLICES = 6

/**
 * Turns the raw sums of [GetSpendingBreakdownUseCase][com.kyovo.cents.domain.port.input.GetSpendingBreakdownUseCase]
 * into a pie: subcategories with nothing spent don't appear, the rest are ordered most spent first, and
 * only the [MAX_NAMED_SLICES] biggest keep their own slice — smaller ones are folded into one "other" slice
 * so the chart stays readable. Uncategorised spending ([spentBySubcategory]'s null key) competes for a slice
 * like any subcategory, since it can be the biggest share.
 */
fun spendingBreakdown(subcategories: List<Subcategory>, spentBySubcategory: Map<SubcategoryId?, Money>): SpendingBreakdown
{
    val byId = subcategories.associateBy { it.id }
    val total = Money(spentBySubcategory.values.sumOf { it.value })

    val ranked = spentBySubcategory.entries
        .filter { it.value.isNotZero() }
        .map { (id, amount) -> labelOf(id, byId) to amount.value }
        .sortedByDescending { (_, cents) -> cents }

    val named = ranked.take(MAX_NAMED_SLICES).map { (label, cents) -> sliceOf(label, cents, total.value) }
    val foldedCents = ranked.drop(MAX_NAMED_SLICES).sumOf { (_, cents) -> cents }
    val folded = if (foldedCents > 0) listOf(sliceOf(SpendingSliceLabel.Other, foldedCents, total.value)) else emptyList()

    return SpendingBreakdown(total, named + folded)
}

private fun labelOf(subcategoryId: SubcategoryId?, byId: Map<SubcategoryId, Subcategory>): SpendingSliceLabel
{
    val subcategory = subcategoryId?.let { byId[it] } ?: return SpendingSliceLabel.Uncategorized
    return SpendingSliceLabel.Named(subcategory.name.value, subcategory.emoji?.value ?: defaultSubcategoryEmoji(subcategory.kind))
}

private fun sliceOf(label: SpendingSliceLabel, cents: Long, totalCents: Long): SpendingSlice
{
    val fraction = if (totalCents == 0L) 0f else (cents.toDouble() / totalCents).toFloat()
    return SpendingSlice(label, Money(cents), fraction)
}
