package com.kyovo.cents.ui.home

import com.kyovo.cents.domain.model.Subcategory
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
