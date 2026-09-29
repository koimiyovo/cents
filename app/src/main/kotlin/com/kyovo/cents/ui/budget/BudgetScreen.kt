package com.kyovo.cents.ui.budget

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyovo.cents.R
import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.ui.common.SelectDropdown
import com.kyovo.cents.ui.common.SelectOption
import com.kyovo.cents.ui.common.formatEuroCents
import com.kyovo.cents.ui.home.AccountsPalette
import com.kyovo.cents.ui.home.DarkAccountsPalette
import com.kyovo.cents.ui.home.HomeTopBar
import com.kyovo.cents.ui.home.LightAccountsPalette
import com.kyovo.cents.ui.home.SubcategoryChip
import com.kyovo.cents.ui.subcategory.defaultSubcategoryEmoji
import kotlin.math.roundToInt

/**
 * The budget tab: where the month's budgets stand, at a glance, in three tabs sharing the same month and
 * account filter — [BudgetTab.OVERVIEW] (a pie of where the month's money actually went, budget or no
 * budget), [BudgetTab.BUDGETS] (a summary of the whole month, then a card per budget that is set — the most
 * urgent first — and, apart, the subcategories that have none), and [BudgetTab.TRENDS] (a bar per recent
 * month, so a drift shows up before it becomes a habit). The account filter (same "no filter" convention as
 * Historique's own) narrows every one of them to a single account's spending. It only looks: the limits and
 * their thresholds are set from the settings.
 */
@Composable
fun BudgetScreen(
    state: BudgetsUiState,
    accounts: List<Account>,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onToday: () -> Unit,
    onSelectTab: (BudgetTab) -> Unit,
    onSelectAccount: (AccountId?) -> Unit,
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
        BudgetAccountFilter(palette, accounts, state.selectedAccountId, onSelectAccount)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp))
        {
            SubcategoryChip(stringResource(R.string.budget_tab_overview), state.tab == BudgetTab.OVERVIEW, palette) {
                onSelectTab(BudgetTab.OVERVIEW)
            }
            SubcategoryChip(stringResource(R.string.budget_tab_budgets), state.tab == BudgetTab.BUDGETS, palette) {
                onSelectTab(BudgetTab.BUDGETS)
            }
            SubcategoryChip(stringResource(R.string.budget_tab_trends), state.tab == BudgetTab.TRENDS, palette) {
                onSelectTab(BudgetTab.TRENDS)
            }
        }

        when (state.tab)
        {
            BudgetTab.BUDGETS ->
            {
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

            BudgetTab.OVERVIEW -> BudgetOverviewTab(palette, state.breakdown)
            BudgetTab.TRENDS -> BudgetTrendTab(palette, state.trend)
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

/** Narrows every tab to one account's spending, same "no filter" convention as Historique's own filter. */
@Composable
private fun BudgetAccountFilter(
    palette: AccountsPalette,
    accounts: List<Account>,
    selected: AccountId?,
    onSelect: (AccountId?) -> Unit,
)
{
    val allLabel = stringResource(R.string.transactions_all_accounts)
    val options = listOf(SelectOption<AccountId?>(null, allLabel)) +
            accounts.map { SelectOption<AccountId?>(it.id, it.name.value) }
    SelectDropdown(
        palette = palette,
        options = options,
        selected = selected,
        onSelect = onSelect,
        labelPrefix = "💳 ",
    )
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
        // A warning ahead of the limit itself: at the current pace, the month would end over it — worth
        // saying only while it is not already over, which the figures above already say.
        val projection = row.projection
        if (projection != null && projection.isPacingToExceed && row.status != BudgetStatus.OVER)
        {
            Text(
                text = stringResource(R.string.budget_row_pacing_to_exceed, formatEuroCents(projection.projectedSpend.value - projection.limit.value)),
                color = palette.iconToneGold,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
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

/**
 * The overview tab: a pie of where the month's money went — every expense counts, whether or not its
 * subcategory has a budget — followed by a legend row per slice. Empty when nothing was spent.
 */
@Composable
private fun BudgetOverviewTab(palette: AccountsPalette, breakdown: SpendingBreakdown)
{
    if (breakdown.slices.isEmpty())
    {
        Text(
            text = stringResource(R.string.budget_overview_empty),
            color = palette.textMuted,
            fontSize = 14.sp,
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(20.dp))
    {
        SpendingPie(palette, breakdown)
        Column(verticalArrangement = Arrangement.spacedBy(14.dp))
        {
            breakdown.slices.forEachIndexed { index, slice -> SpendingLegendRow(palette, index, slice) }
        }
    }
}

/** A donut, most spent slice first from the top, clockwise, with a small gap between slices — and the
 * month's total in its centre. */
@Composable
private fun SpendingPie(palette: AccountsPalette, breakdown: SpendingBreakdown)
{
    val gapDegrees = if (breakdown.slices.size > 1) 3f else 0f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(180.dp))
        {
            val strokeWidth = size.minDimension * 0.16f
            var startAngle = -90f
            breakdown.slices.forEachIndexed { index, slice ->
                val sweep = slice.fraction * 360f
                drawArc(
                    color = sliceColor(palette, index, slice.label),
                    startAngle = startAngle + gapDegrees / 2,
                    sweepAngle = (sweep - gapDegrees).coerceAtLeast(0f),
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                )
                startAngle += sweep
            }
        }
        Text(
            text = stringResource(R.string.budget_overview_total_spent, formatEuroCents(breakdown.total.value)),
            color = palette.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            // The hole of the ring is about 151 dp wide (the 180 dp canvas minus the two 16 % strokes): the
            // text is held to a width that leaves a margin all round, so it wraps ("1 160,00 €" then
            // "dépensés") instead of running up to the arcs. The Box is as wide as the screen, so without
            // this the text was only limited by the screen.
            modifier = Modifier.widthIn(max = 104.dp),
        )
    }
}

@Composable
private fun SpendingLegendRow(palette: AccountsPalette, index: Int, slice: SpendingSlice)
{
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth())
    {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(sliceColor(palette, index, slice.label)),
        )
        Spacer(Modifier.width(10.dp))
        val label = slice.label
        if (label is SpendingSliceLabel.Named)
        {
            Text(text = label.emoji, fontSize = 16.sp)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = sliceLabelText(label),
            color = palette.textPrimary,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
        Column(horizontalAlignment = Alignment.End)
        {
            Text(
                text = formatEuroCents(slice.amount.value),
                color = palette.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.budget_overview_slice_percent, (slice.fraction * 100).roundToInt()),
                color = palette.textMuted,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun sliceLabelText(label: SpendingSliceLabel): String = when (label)
{
    is SpendingSliceLabel.Named -> label.name
    SpendingSliceLabel.Uncategorized -> stringResource(R.string.budget_overview_uncategorized)
    SpendingSliceLabel.Other -> stringResource(R.string.budget_overview_other)
}

/** A slice's colour: a fixed categorical hue by its rank among the named slices, kept distinct from — and
 * never reused as — a status colour; the folded "other" slice is neutral, since it names no single thing. */
private fun sliceColor(palette: AccountsPalette, index: Int, label: SpendingSliceLabel): Color =
    if (label == SpendingSliceLabel.Other) palette.textMuted else palette.chartColors[index]

/**
 * The trends tab: one bar per recent month, each labelled with its amount, most recent (the month shown) in
 * the accent colour and bold — the story is "is this month higher or lower than usual" — the rest muted.
 * Empty when nothing was spent anywhere in the window.
 */
@Composable
private fun BudgetTrendTab(palette: AccountsPalette, trend: SpendingTrend)
{
    if (trend.average == null)
    {
        Text(
            text = stringResource(R.string.budget_trend_empty),
            color = palette.textMuted,
            fontSize = 14.sp,
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp))
    {
        Text(
            text = stringResource(R.string.budget_trend_average, formatEuroCents(trend.average.value)),
            color = palette.textSecondary,
            fontSize = 14.sp,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            trend.bars.forEach { bar ->
                TrendBar(palette, bar, trend.averageFraction, Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun TrendBar(palette: AccountsPalette, bar: SpendingTrendBar, averageFraction: Float?, modifier: Modifier)
{
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally)
    {
        Text(
            text = formatEuroCents(bar.total.value),
            color = if (bar.isSelected) palette.textPrimary else palette.textMuted,
            fontSize = 11.sp,
            fontWeight = if (bar.isSelected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
        )
        Spacer(Modifier.height(4.dp))
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter)
        {
            // The average, as a dashed line level with the bars: every column draws its own segment at the
            // same fraction of the same fixed-height box, so the segments line up into one continuous line.
            if (averageFraction != null)
            {
                Canvas(modifier = Modifier.matchParentSize())
                {
                    val y = size.height * (1f - averageFraction.coerceIn(0f, 1f))
                    drawLine(
                        color = palette.textMuted,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
                    )
                }
            }
            val fraction = bar.barFraction.coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    // A month with nothing spent gets a fixed, visible tick rather than a proportional
                    // sliver: next to a much busier month, a couple of percent tall would vanish.
                    .then(if (fraction <= 0f) Modifier.height(4.dp) else Modifier.fillMaxHeight(fraction))
                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                    .background(if (bar.isSelected) palette.iconToneGreen else palette.divider),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(text = bar.label, color = palette.textMuted, fontSize = 11.sp)
    }
}
