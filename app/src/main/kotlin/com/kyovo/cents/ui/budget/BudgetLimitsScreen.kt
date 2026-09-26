package com.kyovo.cents.ui.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyovo.cents.R
import com.kyovo.cents.ui.common.ChevronDownIcon
import com.kyovo.cents.ui.common.formatEuroCents
import com.kyovo.cents.ui.home.AccountsPalette
import com.kyovo.cents.ui.home.BackButton
import com.kyovo.cents.ui.home.DarkAccountsPalette
import com.kyovo.cents.ui.home.HomeTopBar
import com.kyovo.cents.ui.home.LightAccountsPalette
import com.kyovo.cents.ui.subcategory.defaultSubcategoryEmoji

/**
 * Where the budget limits are set: the month, then every expense subcategory with its limit and alert
 * threshold in force — or "no limit" — and a touch on one opens its form. The budget tab only looks; this is
 * where a budget is defined. A limit set here holds from the month shown on, until it is changed.
 */
@Composable
fun BudgetLimitsScreen(
    state: BudgetsUiState,
    onBack: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onToday: () -> Unit,
    onEdit: (BudgetRow) -> Unit,
    modifier: Modifier = Modifier,
)
{
    val palette = if (isSystemInDarkTheme()) DarkAccountsPalette else LightAccountsPalette

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackButton(palette, onBack)
            Spacer(Modifier.width(12.dp))
            HomeTopBar(palette, stringResource(R.string.budget_limits_title))
        }
        MonthSelectorRow(
            palette = palette,
            label = state.selector.label,
            isCurrentMonth = state.isCurrentMonth,
            onPrevious = onPreviousMonth,
            onNext = onNextMonth,
            onToday = onToday,
        )
        Text(
            text = stringResource(R.string.budget_limits_intro),
            color = palette.textMuted,
            fontSize = 13.sp,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(palette.surface),
        ) {
            state.rows.forEach { row -> LimitRow(palette, row, onClick = { onEdit(row) }) }
        }
    }
}

@Composable
private fun LimitRow(palette: AccountsPalette, row: BudgetRow, onClick: () -> Unit)
{
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = row.subcategory.emoji?.value ?: defaultSubcategoryEmoji(row.subcategory.kind),
            fontSize = 22.sp,
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.subcategory.name.value,
                color = palette.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            val progress = row.progress
            Text(
                text = if (progress == null)
                    stringResource(R.string.budget_limits_row_none)
                else
                    stringResource(
                        R.string.budget_limits_row_set,
                        formatEuroCents(progress.limit.value),
                        progress.alertThreshold.percent,
                    ),
                color = if (progress == null) palette.textMuted else palette.textSecondary,
                fontSize = 13.sp,
            )
        }
        Spacer(Modifier.width(8.dp))
        // A chevron pointing right: "this opens something".
        ChevronDownIcon(tint = palette.textSecondary, modifier = Modifier.size(14.dp).rotate(-90f))
    }
}
