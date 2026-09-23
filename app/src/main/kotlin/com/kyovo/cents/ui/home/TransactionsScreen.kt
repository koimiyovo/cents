package com.kyovo.cents.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kyovo.cents.R
import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.ExpenseSubcategory
import com.kyovo.cents.domain.model.IncomeSubcategory
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.model.TransactionCategory
import com.kyovo.cents.domain.model.TransactionSubcategory
import com.kyovo.cents.domain.port.input.ListAccountsUseCase
import com.kyovo.cents.domain.port.input.ListTransactionsUseCase
import com.kyovo.cents.ui.common.IconTone
import com.kyovo.cents.ui.common.formatSignedEuroCents
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

@Composable
fun TransactionsScreen(
    listAccounts: ListAccountsUseCase,
    listTransactions: ListTransactionsUseCase,
    modifier: Modifier = Modifier,
)
{
    val palette = if (isSystemInDarkTheme()) DarkAccountsPalette else LightAccountsPalette

    val accounts = remember { listAccounts.list() }
    val accountsById = remember(accounts) { accounts.associateBy { it.id } }
    val allTransactions = remember { listTransactions.list() }

    // Filter selection isn't saved across configuration changes: AccountId/TransactionSubcategory
    // aren't trivially Saveable, and losing a filter on rotation is a minor, acceptable trade-off.
    var selectedPeriod by remember { mutableStateOf(TransactionsPeriod.LAST_30_DAYS) }
    var customFrom by remember { mutableStateOf<LocalDate?>(null) }
    var customTo by remember { mutableStateOf<LocalDate?>(null) }
    var selectedAccountId by remember { mutableStateOf<AccountId?>(null) }
    var selectedSubcategory by remember { mutableStateOf<TransactionSubcategory?>(null) }
    var periodMenuExpanded by remember { mutableStateOf(false) }
    var accountMenuExpanded by remember { mutableStateOf(false) }
    var showCustomRangePicker by remember { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    // The whole point of the use case's from/to range is to scope the screen to a period instead
    // of always loading every transaction ever recorded; 30 days is the default window.
    val (periodFrom, periodTo) = remember(selectedPeriod, customFrom, customTo) {
        periodRange(selectedPeriod, customFrom, customTo, Instant.now())
    }
    val transactionsInPeriod = remember(allTransactions, periodFrom, periodTo) {
        transactionsWithinRange(allTransactions, periodFrom, periodTo)
    }
    val totalIncomeCents = remember(transactionsInPeriod) {
        transactionsInPeriod.filter { it.category == TransactionCategory.INCOME }
            .sumOf { it.amount.value }
    }
    val totalExpenseCents = remember(transactionsInPeriod) {
        transactionsInPeriod.filter { it.category == TransactionCategory.EXPENSE }
            .sumOf { it.amount.value }
    }
    val netCents = totalIncomeCents - totalExpenseCents
    val availableSubcategories = remember(transactionsInPeriod) {
        transactionsInPeriod.mapNotNull { it.subcategory }.distinct()
    }

    val filteredTransactions =
        remember(selectedAccountId, selectedSubcategory, searchQuery, periodFrom, periodTo) {
            listTransactions.list(
                accountId = selectedAccountId,
                subcategory = selectedSubcategory,
                titleFilter = searchQuery,
                from = periodFrom,
                to = periodTo,
            )
        }
    val groupedByDay = remember(filteredTransactions) { groupByDay(filteredTransactions) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HomeTopBar(palette, stringResource(R.string.transactions_title))
        Text(
            text = stringResource(R.string.transactions_subtitle),
            color = palette.textMuted,
            fontSize = 13.sp,
        )
        PeriodFilterRow(
            palette = palette,
            selected = selectedPeriod,
            label = periodLabel(selectedPeriod, customFrom, customTo),
            expanded = periodMenuExpanded,
            onToggleExpanded = { periodMenuExpanded = !periodMenuExpanded },
            onSelectPreset = { periodMenuExpanded = false; selectedPeriod = it },
            onSelectCustom = { periodMenuExpanded = false; showCustomRangePicker = true },
        )
        StatsRow(
            palette,
            expenseCents = totalExpenseCents,
            incomeCents = totalIncomeCents,
            netCents = netCents
        )
        SearchField(palette, searchQuery) { searchQuery = it }
        AccountFilterRow(
            palette = palette,
            accounts = accounts,
            selectedAccountId = selectedAccountId,
            expanded = accountMenuExpanded,
            onToggleExpanded = { accountMenuExpanded = !accountMenuExpanded },
            onSelect = { accountMenuExpanded = false; selectedAccountId = it },
            movementsCount = filteredTransactions.size,
        )
        SubcategoryChipsRow(
            palette = palette,
            subcategories = availableSubcategories,
            selected = selectedSubcategory,
            onSelect = { selectedSubcategory = it },
        )
        if (groupedByDay.isEmpty())
        {
            Text(
                text = stringResource(R.string.transactions_empty),
                color = palette.textMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 24.dp),
            )
        } else
        {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                groupedByDay.forEach { (date, dayTransactions) ->
                    DayGroup(palette, date, dayTransactions, accountsById)
                }
            }
        }
        Text(
            text = stringResource(R.string.transactions_privacy_footer),
            color = palette.textMuted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
    }

    if (showCustomRangePicker)
    {
        CustomDateRangePickerDialog(
            palette = palette,
            initialFrom = customFrom,
            initialTo = customTo,
            onDismiss = { showCustomRangePicker = false },
            onConfirm = { from, to ->
                customFrom = from
                customTo = to
                selectedPeriod = TransactionsPeriod.CUSTOM
                showCustomRangePicker = false
            },
        )
    }
}

internal enum class TransactionsPeriod(val labelRes: Int, val days: Long?)
{
    LAST_7_DAYS(R.string.transactions_period_7_days, 7),
    LAST_30_DAYS(R.string.transactions_period_30_days, 30),
    ALL_TIME(R.string.transactions_period_all_time, null),
    CUSTOM(R.string.transactions_period_custom, null),
}

/**
 * The from/to bounds for a period: preset periods count back from [now], CUSTOM uses the picked
 * dates (start of day to end of day, in the local zone), and ALL_TIME has no bounds at all.
 */
internal fun periodRange(
    period: TransactionsPeriod,
    customFrom: LocalDate?,
    customTo: LocalDate?,
    now: Instant,
): Pair<Instant?, Instant?>
{
    if (period == TransactionsPeriod.CUSTOM)
    {
        val zone = ZoneId.systemDefault()
        val from = customFrom?.atStartOfDay(zone)?.toInstant()
        val to = customTo?.plusDays(1)?.atStartOfDay(zone)?.toInstant()?.minusNanos(1)
        return from to to
    }
    return (period.days?.let { now.minus(it, ChronoUnit.DAYS) }) to null
}

/** Same boundary semantics as [com.kyovo.cents.application.usecase.ListTransactionsService]: inclusive on both ends. */
internal fun transactionsWithinRange(
    transactions: List<Transaction>,
    from: Instant?,
    to: Instant?
): List<Transaction> =
    transactions.filter {
        (from == null || !it.date.isBefore(from)) && (to == null || !it.date.isAfter(
            to
        ))
    }

@Composable
internal fun periodLabel(
    period: TransactionsPeriod,
    customFrom: LocalDate?,
    customTo: LocalDate?
): String
{
    if (period == TransactionsPeriod.CUSTOM && customFrom != null && customTo != null)
    {
        val formatter = DateTimeFormatter.ofPattern("d MMM", Locale.FRENCH)
        return "${customFrom.format(formatter)} – ${customTo.format(formatter)}"
    }
    return stringResource(period.labelRes)
}

/**
 * A single range picker rather than two chained native date dialogs: besides being the more
 * standard "pick a period" gesture, [androidx.compose.material3.DateRangePickerState] enforces
 * end >= start on its own (tapping a date before the current start just moves the start there).
 * MaterialTheme is scoped to this dialog only — the app has no global MaterialTheme wrapper
 * elsewhere, so this doesn't affect anything outside it — with colors pulled from [palette]
 * instead of M3's default (yellow-accented) baseline theme.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CustomDateRangePickerDialog(
    palette: AccountsPalette,
    initialFrom: LocalDate?,
    initialTo: LocalDate?,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, LocalDate) -> Unit,
)
{
    val today = LocalDate.now()
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = (initialFrom ?: today.minusDays(30)).toEpochMillisUtc(),
        initialSelectedEndDateMillis = (initialTo ?: today).toEpochMillisUtc(),
        selectableDates = object : SelectableDates
        {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                utcTimeMillis <= System.currentTimeMillis()
        },
    )
    // The "day in range" fill uses secondaryContainer, not primaryContainer (M3's DatePickerColors
    // default), so both need overriding — otherwise the range highlight stays M3's baseline purple
    // while only the start/end anchor circles pick up the palette color.
    val colorScheme = lightColorScheme(
        primary = palette.iconToneGreen,
        onPrimary = palette.heroOnCardPrimary,
        primaryContainer = palette.iconToneGreen,
        onPrimaryContainer = palette.heroOnCardPrimary,
        secondary = palette.iconToneGreen,
        onSecondary = palette.heroOnCardPrimary,
        secondaryContainer = palette.iconToneGreen,
        onSecondaryContainer = palette.heroOnCardPrimary,
        surface = palette.surface,
        onSurface = palette.textPrimary,
        onSurfaceVariant = palette.textSecondary,
        surfaceContainerHigh = palette.surface,
        outline = palette.divider,
        background = palette.background,
        onBackground = palette.textPrimary,
    )
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        MaterialTheme(colorScheme = colorScheme) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(28.dp),
                color = palette.background,
            ) {
                Column {
                    DateRangePicker(
                        state = state,
                        modifier = Modifier.weight(1f, fill = false),
                        title = {
                            Text(
                                text = "Sélectionnez une période",
                                modifier = Modifier.padding(
                                    start = 24.dp,
                                    end = 12.dp,
                                    top = 16.dp
                                ),
                            )
                        },
                        // The default headline uses headlineLarge — sized for a full-screen
                        // dialog — and doesn't respect a typography override at this token, so
                        // it's replaced outright with a smaller one, matched to our compact
                        // Surface (and in French, consistent with the rest of the app).
                        headline = {
                            val formatter = remember {
                                DateTimeFormatter.ofPattern(
                                    "d MMM yyyy",
                                    Locale.FRENCH
                                )
                            }
                            val startText = state.selectedStartDateMillis
                                ?.let { it.toLocalDateUtc().format(formatter) } ?: "Début"
                            val endText = state.selectedEndDateMillis
                                ?.let { it.toLocalDateUtc().format(formatter) } ?: "Fin"
                            Text(
                                text = "$startText – $endText",
                                fontSize = 16.sp,
                                modifier = Modifier.padding(
                                    start = 24.dp,
                                    end = 12.dp,
                                    bottom = 12.dp
                                ),
                            )
                        },
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = onDismiss) { Text("Annuler") }
                        TextButton(
                            onClick = {
                                val start = state.selectedStartDateMillis
                                val end = state.selectedEndDateMillis
                                if (start != null && end != null)
                                {
                                    // The calendar UI can't produce end < start on its own, but the
                                    // picker's text-input mode (pencil icon) types both fields
                                    // freely, so this is enforced explicitly rather than trusted.
                                    val from = minOf(start, end).toLocalDateUtc()
                                    val to = maxOf(start, end).toLocalDateUtc()
                                    onConfirm(from, to)
                                }
                            },
                            enabled = state.selectedStartDateMillis != null && state.selectedEndDateMillis != null,
                        ) { Text("OK") }
                    }
                }
            }
        }
    }
}

private fun LocalDate.toEpochMillisUtc(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toLocalDateUtc(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@Composable
internal fun StatsRow(
    palette: AccountsPalette,
    expenseCents: Long,
    incomeCents: Long,
    netCents: Long
)
{
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatPill(
            modifier = Modifier.weight(1f),
            label = stringResource(R.string.transactions_expense_label),
            amount = formatSignedEuroCents(-expenseCents),
            accent = palette.heroExpenseAccent,
            palette = palette,
        )
        StatPill(
            modifier = Modifier.weight(1f),
            label = stringResource(R.string.transactions_income_label),
            amount = formatSignedEuroCents(incomeCents),
            accent = palette.heroIncomeAccent,
            palette = palette,
        )
        StatPill(
            modifier = Modifier.weight(1f),
            label = stringResource(R.string.transactions_net_label),
            amount = formatSignedEuroCents(netCents),
            accent = palette.statusActiveColor,
            palette = palette,
        )
    }
}

@Composable
private fun StatPill(
    modifier: Modifier,
    label: String,
    amount: String,
    accent: Color,
    palette: AccountsPalette
)
{
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(palette.surface)
            .padding(12.dp),
    ) {
        Text(text = label, color = accent, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(4.dp))
        Text(
            text = amount,
            color = palette.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
internal fun SearchField(palette: AccountsPalette, query: String, onQueryChange: (String) -> Unit)
{
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(palette.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "🔍", fontSize = 16.sp)
        Spacer(Modifier.width(8.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty())
            {
                Text(
                    text = stringResource(R.string.transactions_search_placeholder),
                    color = palette.textMuted,
                    fontSize = 14.sp,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = palette.textPrimary, fontSize = 14.sp),
                cursorBrush = SolidColor(palette.textPrimary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
internal fun PeriodFilterRow(
    palette: AccountsPalette,
    selected: TransactionsPeriod,
    label: String,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onSelectPreset: (TransactionsPeriod) -> Unit,
    onSelectCustom: () -> Unit,
)
{
    Column {
        DropdownPill(
            label = "📅 $label",
            palette = palette,
            modifier = Modifier.clickable(onClick = onToggleExpanded),
        )
        if (expanded)
        {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(palette.surface),
            ) {
                listOf(
                    TransactionsPeriod.LAST_7_DAYS,
                    TransactionsPeriod.LAST_30_DAYS,
                    TransactionsPeriod.ALL_TIME
                )
                    .forEach { period ->
                        SelectableOptionRow(
                            label = stringResource(period.labelRes),
                            selected = period == selected,
                            palette = palette,
                            onClick = { onSelectPreset(period) },
                        )
                    }
                SelectableOptionRow(
                    label = stringResource(R.string.transactions_period_custom),
                    selected = selected == TransactionsPeriod.CUSTOM,
                    palette = palette,
                    onClick = onSelectCustom,
                )
            }
        }
    }
}

@Composable
private fun AccountFilterRow(
    palette: AccountsPalette,
    accounts: List<Account>,
    selectedAccountId: AccountId?,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onSelect: (AccountId?) -> Unit,
    movementsCount: Int,
)
{
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val selectedLabel = accounts.firstOrNull { it.id == selectedAccountId }?.name?.value
                ?: stringResource(R.string.transactions_all_accounts)
            DropdownPill(
                label = "💳 $selectedLabel",
                palette = palette,
                modifier = Modifier.clickable(onClick = onToggleExpanded),
            )
            Text(
                text = movementsCountLabel(movementsCount),
                color = palette.textMuted,
                fontSize = 13.sp,
            )
        }
        if (expanded)
        {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(palette.surface),
            ) {
                SelectableOptionRow(
                    label = stringResource(R.string.transactions_all_accounts),
                    selected = selectedAccountId == null,
                    palette = palette,
                    onClick = { onSelect(null) },
                )
                accounts.forEach { account ->
                    SelectableOptionRow(
                        label = account.name.value,
                        selected = selectedAccountId == account.id,
                        palette = palette,
                        onClick = { onSelect(account.id) },
                    )
                }
            }
        }
    }
}

/** A pill showing [label] with a hand-drawn chevron — not a "▾"/"⌄" glyph, whose vertical metrics
 *  vary across fonts and don't sit level with the label text (see EyeToggleIcon for the same
 *  reasoning). */
@Composable
private fun DropdownPill(label: String, palette: AccountsPalette, modifier: Modifier = Modifier)
{
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(palette.surface)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = palette.textSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.width(6.dp))
        ChevronDownIcon(tint = palette.textSecondary, modifier = Modifier.size(12.dp))
    }
}

@Composable
private fun ChevronDownIcon(tint: Color, modifier: Modifier = Modifier)
{
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.15f, h * 0.35f)
            lineTo(w * 0.5f, h * 0.75f)
            lineTo(w * 0.85f, h * 0.35f)
        }
        drawPath(
            path,
            color = tint,
            style = Stroke(width = w * 0.18f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

/** Same treatment as the subcategory chips: a solid fill, not just a text color change, so the
 *  current choice in the period/account dropdowns is unambiguous at a glance. */
@Composable
private fun SelectableOptionRow(
    label: String,
    selected: Boolean,
    palette: AccountsPalette,
    onClick: () -> Unit
)
{
    Text(
        text = label,
        color = if (selected) palette.heroOnCardPrimary else palette.textPrimary,
        fontSize = 14.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) palette.iconToneGreen else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
internal fun SubcategoryChipsRow(
    palette: AccountsPalette,
    subcategories: List<TransactionSubcategory>,
    selected: TransactionSubcategory?,
    onSelect: (TransactionSubcategory?) -> Unit,
)
{
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SubcategoryChip(
            label = stringResource(R.string.transactions_filter_all),
            selected = selected == null,
            palette = palette,
            onClick = { onSelect(null) },
        )
        subcategories.forEach { subcategory ->
            SubcategoryChip(
                label = subcategoryLabel(subcategory),
                selected = selected == subcategory,
                palette = palette,
                onClick = { onSelect(subcategory) },
            )
        }
    }
}

@Composable
private fun SubcategoryChip(
    label: String,
    selected: Boolean,
    palette: AccountsPalette,
    onClick: () -> Unit
)
{
    // Not palette.primaryButtonBackground: in light mode that's an outlined white button, which
    // is indistinguishable from an unselected chip's own (also white) surface background.
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) palette.iconToneGreen else palette.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            color = if (selected) palette.heroOnCardPrimary else palette.textSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
internal fun DayGroup(
    palette: AccountsPalette,
    date: LocalDate,
    transactions: List<Transaction>,
    accountsById: Map<AccountId, Account>,
)
{
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = dayLabel(date),
                color = palette.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            val dayNetCents = transactions.sumOf { it.signedAmount }
            Text(
                text = formatSignedEuroCents(dayNetCents),
                color = palette.textMuted,
                fontSize = 13.sp
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(palette.surface),
        ) {
            transactions.forEachIndexed { index, transaction ->
                TransactionRow(palette, transaction, accountsById[transaction.accountId])
                if (index != transactions.lastIndex)
                {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(palette.divider),
                    )
                }
            }
        }
    }
}

@Composable
private fun TransactionRow(palette: AccountsPalette, transaction: Transaction, account: Account?)
{
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val emoji = transaction.subcategory?.let { subcategoryEmoji(it) } ?: categoryEmoji(
            transaction.category
        )
        val tone = when (transaction.category)
        {
            TransactionCategory.EXPENSE, TransactionCategory.TRANSFER_OUT -> IconTone.Gold
            TransactionCategory.INCOME, TransactionCategory.TRANSFER_IN   -> IconTone.Green
            TransactionCategory.INITIAL_DEPOSIT                           -> IconTone.Mint
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(toneBackground(tone, palette)),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = emoji, fontSize = 16.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = transaction.title.value,
                color = palette.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            val subtitle = listOfNotNull(
                transaction.subcategory?.let { subcategoryLabel(it) },
                account?.name?.value,
            ).joinToString(" • ")
            if (subtitle.isNotEmpty())
            {
                Text(text = subtitle, color = palette.textMuted, fontSize = 12.sp)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = formatSignedEuroCents(transaction.signedAmount),
                color = if (transaction.signedAmount >= 0) palette.heroIncomeAccent else palette.heroExpenseAccent,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(text = timeLabel(transaction.date), color = palette.textMuted, fontSize = 11.sp)
        }
    }
}

internal fun groupByDay(transactions: List<Transaction>): List<Pair<LocalDate, List<Transaction>>>
{
    val zone = ZoneId.systemDefault()
    return transactions
        .sortedByDescending { it.date }
        .groupBy { it.date.atZone(zone).toLocalDate() }
        .toList()
        .sortedByDescending { it.first }
}

@Composable
private fun dayLabel(date: LocalDate): String
{
    val today = LocalDate.now()
    return when (date)
    {
        today              -> stringResource(R.string.transactions_today)
        today.minusDays(1) -> stringResource(R.string.transactions_yesterday)
        else               -> formatDayHeader(date, today)
    }
}

/**
 * "d MMMM" for recent dates, "d MMMM yyyy" once the date is a year or more before [today]: an
 * account's full history can reach back far enough that "19 août" alone would be ambiguous.
 */
internal fun formatDayHeader(date: LocalDate, today: LocalDate): String
{
    val pattern = if (date.isAfter(today.minusYears(1))) "d MMMM" else "d MMMM yyyy"
    return date.format(DateTimeFormatter.ofPattern(pattern, Locale.FRENCH))
}

private fun timeLabel(date: Instant): String =
    date.atZone(ZoneId.systemDefault()).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))

private fun subcategoryLabel(subcategory: TransactionSubcategory): String = when (subcategory)
{
    ExpenseSubcategory.GROCERIES   -> "Alimentation"
    ExpenseSubcategory.FUEL        -> "Transport"
    ExpenseSubcategory.HAIRDRESSER -> "Coiffeur"
    IncomeSubcategory.SALARY       -> "Salaire"
    IncomeSubcategory.GIFT         -> "Cadeau"
    IncomeSubcategory.REFUND       -> "Remboursement"
}

private fun subcategoryEmoji(subcategory: TransactionSubcategory): String = when (subcategory)
{
    ExpenseSubcategory.GROCERIES   -> "🛒"
    ExpenseSubcategory.FUEL        -> "⛽"
    ExpenseSubcategory.HAIRDRESSER -> "💇"
    IncomeSubcategory.SALARY       -> "💰"
    IncomeSubcategory.GIFT         -> "🎁"
    IncomeSubcategory.REFUND       -> "💸"
}

private fun categoryEmoji(category: TransactionCategory): String = when (category)
{
    TransactionCategory.INCOME          -> "💰"
    TransactionCategory.EXPENSE         -> "💳"
    TransactionCategory.INITIAL_DEPOSIT -> "🏦"
    TransactionCategory.TRANSFER_OUT    -> "📤"
    TransactionCategory.TRANSFER_IN     -> "📥"
}

internal fun movementsCountLabel(count: Int): String =
    "$count mouvement${if (count > 1) "s" else ""}"
