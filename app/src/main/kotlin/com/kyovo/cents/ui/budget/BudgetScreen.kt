package com.kyovo.cents.ui.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyovo.cents.R
import com.kyovo.cents.ui.common.formatEuroCents
import com.kyovo.cents.ui.home.AccountsPalette
import com.kyovo.cents.ui.home.DarkAccountsPalette
import com.kyovo.cents.ui.home.HomeTopBar
import com.kyovo.cents.ui.home.LightAccountsPalette
import com.kyovo.cents.ui.subcategory.defaultSubcategoryEmoji

/**
 * The budget tab: where the month's budgets stand, at a glance. A month selector on top, a summary of the
 * whole month, then a card per budget that is set — the most urgent first — and, apart, the subcategories
 * that have none. It only looks: the limits and their thresholds are set from the settings.
 */
@Composable
fun BudgetScreen(
    state: BudgetsUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onToday: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
)
{
    val palette = if (isSystemInDarkTheme()) DarkAccountsPalette else LightAccountsPalette
    val overview = remember(state.rows) { budgetOverview(state.rows) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HomeTopBar(palette, stringResource(R.string.budget_title), onOpenSettings)
        MonthSelectorRow(
            palette = palette,
            label = state.selector.label,
            isCurrentMonth = state.isCurrentMonth,
            onPrevious = onPreviousMonth,
            onNext = onNextMonth,
            onToday = onToday,
        )

        val summary = overview.summary
        if (summary == null)
        {
            Text(
                text = stringResource(R.string.budget_empty),
                color = palette.textMuted,
                fontSize = 14.sp,
            )
        } else
        {
            SummaryCard(palette, summary)
            overview.budgeted.forEach { row -> BudgetCard(palette, row) }
        }

        if (overview.unbudgeted.isNotEmpty())
        {
            Text(
                text = stringResource(R.string.budget_unbudgeted_title),
                color = palette.textSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            overview.unbudgeted.forEach { row -> UnbudgetedRow(palette, row) }
        }
    }
}

/** The colour that says how a budget stands: on track, close to its limit, or over it. */
private fun statusColor(palette: AccountsPalette, status: BudgetStatus?): Color = when (status)
{
    BudgetStatus.OVER           -> palette.error
    BudgetStatus.CLOSE_TO_LIMIT -> palette.iconToneGold
    BudgetStatus.ON_TRACK, null -> palette.statusActiveColor
}

/** `‹ Septembre 2026 ›`, with a way back to today when the screen is elsewhere. Shared by the tab and the limits. */
@Composable
internal fun MonthSelectorRow(
    palette: AccountsPalette,
    label: String,
    isCurrentMonth: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
)
{
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically)
    {
        RoundArrow(palette, "‹", stringResource(R.string.budget_month_previous), onPrevious)
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally)
        {
            Text(text = label, color = palette.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            // The way back to now, only when the screen is elsewhere.
            if (!isCurrentMonth)
            {
                Text(
                    text = stringResource(R.string.budget_month_today),
                    color = palette.kicker,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onToday)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
        RoundArrow(palette, "›", stringResource(R.string.budget_month_next), onNext)
    }
}

@Composable
private fun RoundArrow(palette: AccountsPalette, glyph: String, description: String, onClick: () -> Unit)
{
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(palette.surface)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = glyph, color = palette.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SummaryCard(palette: AccountsPalette, summary: BudgetSummary)
{
    val color = if (summary.isOverspent) palette.error else palette.heroOnCardPrimary
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(palette.heroCardBackground)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(
                R.string.budget_summary_spent_of,
                formatEuroCents(summary.totalSpent.value),
                formatEuroCents(summary.totalLimit.value),
            ),
            color = palette.heroOnCardPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        ProgressBar(
            fraction = summary.barFraction,
            color = if (summary.isOverspent) palette.error else palette.heroIncomeAccent,
            track = palette.heroPillBackground,
        )
        Text(
            text = if (summary.isOverspent)
                stringResource(R.string.budget_summary_over, formatEuroCents(-summary.remaining))
            else
                stringResource(R.string.budget_summary_left, formatEuroCents(summary.remaining)),
            color = color,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
        // How many budgets need attention: the whole point of a glance.
        val attention = buildList {
            if (summary.overCount > 0)
                add(pluralStringResource(R.plurals.budget_summary_over_count, summary.overCount, summary.overCount))
            if (summary.closeCount > 0)
                add(pluralStringResource(R.plurals.budget_summary_close_count, summary.closeCount, summary.closeCount))
        }
        Text(
            text = if (attention.isEmpty()) stringResource(R.string.budget_summary_all_good) else attention.joinToString(" · "),
            color = palette.heroOnCardSecondary,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun BudgetCard(palette: AccountsPalette, row: BudgetRow)
{
    val progress = row.progress ?: return
    val color = statusColor(palette, row.status)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(palette.surface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically)
        {
            Text(text = subcategoryEmoji(row), fontSize = 22.sp)
            Spacer(Modifier.width(10.dp))
            Text(
                text = row.subcategory.name.value,
                color = palette.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (row.status == BudgetStatus.CLOSE_TO_LIMIT)
            {
                Text(
                    text = stringResource(R.string.budget_row_status_close),
                    color = color,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        ProgressBar(fraction = row.barFraction ?: 0f, color = color, track = palette.divider)
        Row(verticalAlignment = Alignment.CenterVertically)
        {
            Text(
                text = stringResource(
                    R.string.budget_row_spent_of,
                    formatEuroCents(progress.spent.value),
                    formatEuroCents(progress.limit.value),
                ),
                color = palette.textMuted,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
            )
            val remaining = row.remaining
            Text(
                text = when (remaining)
                {
                    is BudgetRemaining.Left -> stringResource(R.string.budget_row_left, formatEuroCents(remaining.cents))
                    is BudgetRemaining.Over -> stringResource(R.string.budget_row_over, formatEuroCents(remaining.cents))
                    null                    -> ""
                },
                color = if (remaining is BudgetRemaining.Over) palette.error else palette.textSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun UnbudgetedRow(palette: AccountsPalette, row: BudgetRow)
{
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(palette.surface)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = subcategoryEmoji(row), fontSize = 18.sp)
        Spacer(Modifier.width(10.dp))
        Text(text = row.subcategory.name.value, color = palette.textMuted, fontSize = 14.sp)
    }
}

/** A thin bar filled to [fraction] (0..1). */
@Composable
private fun ProgressBar(fraction: Float, color: Color, track: Color)
{
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(track),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(4.dp))
                .background(color),
        )
    }
}

/** The icon of a row: the subcategory's own emoji, or the default of its kind — never blank. */
private fun subcategoryEmoji(row: BudgetRow): String =
    row.subcategory.emoji?.value ?: defaultSubcategoryEmoji(row.subcategory.kind)
