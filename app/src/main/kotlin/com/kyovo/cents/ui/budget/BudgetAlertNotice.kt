package com.kyovo.cents.ui.budget

import com.kyovo.cents.domain.model.BudgetAlert
import com.kyovo.cents.domain.model.BudgetAlertLevel
import com.kyovo.cents.domain.model.Subcategory
import com.kyovo.cents.ui.subcategory.defaultSubcategoryEmoji

/** What a budget alert says on screen: the subcategory's emoji and name (an alert only carries its id) and the level. */
data class BudgetAlertNotice(val emoji: String, val subcategoryName: String, val level: BudgetAlertLevel)

/** Null when the subcategory is gone (deleted since the alert was raised): there is nothing left to warn about. */
fun budgetAlertNotice(alert: BudgetAlert, subcategories: List<Subcategory>): BudgetAlertNotice?
{
    val subcategory = subcategories.find { it.id == alert.subcategoryId } ?: return null
    return BudgetAlertNotice(
        subcategory.emoji?.value ?: defaultSubcategoryEmoji(subcategory.kind),
        subcategory.name.value,
        alert.level,
    )
}
