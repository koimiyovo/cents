package com.kyovo.cents.ui.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyovo.cents.R
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
 * The switch between the two [BudgetSection]s: one segmented bar across the whole width, apart from the small
 * chips below it (which switch the tab of the month) so the two levels are not mixed up. Touch only - the screen
 * lives in a pager that swipes between the app's tabs, and a swipe here would fight it.
 */
@Composable
internal fun BudgetSectionSwitch(palette: AccountsPalette, selected: BudgetSection, onSelect: (BudgetSection) -> Unit)
{
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(palette.surface)
            .padding(4.dp),
    ) {
        BudgetSection.entries.forEach { section ->
            val isSelected = section == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(if (isSelected) palette.iconToneGreen else palette.surface)
                    .clickable { onSelect(section) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(
                        when (section)
                        {
                            BudgetSection.MONTH    -> R.string.budget_section_month
                            BudgetSection.PROJECTS -> R.string.budget_section_projects
                        },
                    ),
                    color = if (isSelected) palette.heroOnCardPrimary else palette.textSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
