package com.kyovo.cents.ui.subcategory

import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.Subcategory
import com.kyovo.cents.domain.model.Transaction

/** A subcategory and how many transactions use it: what a row of the management screen shows. */
data class SubcategoryRow(val subcategory: Subcategory, val transactionCount: Int)

/** The icon a subcategory without an emoji of its own shows: it says which kind it is. */
fun defaultSubcategoryEmoji(kind: RecordableTransactionCategory): String = when (kind)
{
    RecordableTransactionCategory.EXPENSE -> "\uD83D\uDCB3"
    RecordableTransactionCategory.INCOME  -> "\uD83D\uDCB0"
}

/** The icon of a row: the subcategory's own emoji, or the default of its kind — never blank. */
fun SubcategoryRow.displayEmoji(): String = subcategory.emoji?.value ?: defaultSubcategoryEmoji(subcategory.kind)

/** The subcategories of one kind, in the order given. */
data class SubcategorySection(val kind: RecordableTransactionCategory, val rows: List<SubcategoryRow>)

/**
 * What the management screen lists: one section per kind — expenses first, then incomes — each
 * holding its subcategories in the order of [subcategories] (already by name) with the number of
 * [transactions] that use them. Both sections are always there, even empty: each ends with its own
 * "new subcategory" action, so an empty kind still has to be shown.
 */
fun subcategorySections(subcategories: List<Subcategory>, transactions: List<Transaction>): List<SubcategorySection>
{
    val countById = transactions.mapNotNull { it.subcategoryId }.groupingBy { it }.eachCount()
    return listOf(RecordableTransactionCategory.EXPENSE, RecordableTransactionCategory.INCOME).map { kind ->
        SubcategorySection(
            kind = kind,
            rows = subcategories
                .filter { it.kind == kind }
                .map { SubcategoryRow(it, countById[it.id] ?: 0) },
        )
    }
}
