package com.kyovo.cents.ui.budget

import androidx.compose.runtime.Composable
import com.kyovo.cents.R
import com.kyovo.cents.ui.common.SectionSwitch
import com.kyovo.cents.ui.home.AccountsPalette

/**
 * The two halves of the Budget screen: the month's budgets (with the month selector, the account filter and
 * their own three tabs) and the projects, which are not monthly and so have none of those.
 */
enum class BudgetSection
{
    MONTH,
    PROJECTS,
}

/**
 * The switch between the two [BudgetSection]s, apart from the small chips below it (which switch the tab of
 * the month) so the two levels are not mixed up.
 */
@Composable
internal fun BudgetSectionSwitch(palette: AccountsPalette, selected: BudgetSection, onSelect: (BudgetSection) -> Unit)
{
    SectionSwitch(palette, BudgetSection.entries, selected, onSelect = onSelect, labelOf = {
        when (it)
        {
            BudgetSection.MONTH    -> R.string.budget_section_month
            BudgetSection.PROJECTS -> R.string.budget_section_projects
        }
    })
}
