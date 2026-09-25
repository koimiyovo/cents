package com.kyovo.cents.ui.home

import com.kyovo.cents.domain.model.Subcategory
import com.kyovo.cents.domain.model.SubcategoryId
import com.kyovo.cents.domain.model.Transaction

/**
 * The subcategories a filter offers: those among [subcategories] that at least one of
 * [transactions] points to, in the order of [subcategories]. So choosing one never leaves an empty
 * list, and a subcategory nothing uses (or an id the list doesn't know) doesn't show up.
 */
internal fun availableSubcategories(
    transactions: List<Transaction>,
    subcategories: List<Subcategory>,
): List<Subcategory>
{
    val usedIds = transactions.mapNotNull { it.subcategoryId }.toSet()
    return subcategories.filter { it.id in usedIds }
}

/**
 * The subcategory the filter really applies: [selected] if it still exists, otherwise none. A screen
 * remembers the subcategory it was filtering on, and that one can be deleted from the management
 * screen in the meantime — the filter must then fall back to "all", not keep showing an empty list
 * for a subcategory that is gone.
 */
internal fun validSubcategoryFilter(selected: SubcategoryId?, subcategories: List<Subcategory>): SubcategoryId?
{
    return selected?.takeIf { id -> subcategories.any { it.id == id } }
}
