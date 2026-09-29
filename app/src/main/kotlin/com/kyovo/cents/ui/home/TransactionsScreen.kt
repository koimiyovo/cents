package com.kyovo.cents.ui.home

import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ColorScheme
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
import androidx.compose.ui.graphics.SolidColor
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
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.Subcategory
import com.kyovo.cents.domain.model.SubcategoryId
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.model.TransactionCategory
import com.kyovo.cents.domain.port.input.ListAccountsUseCase
import com.kyovo.cents.domain.port.input.ListArchivedAccountsUseCase
import com.kyovo.cents.domain.port.input.ListSubcategoriesUseCase
import com.kyovo.cents.domain.port.input.ListTransactionsUseCase
import com.kyovo.cents.ui.common.DropdownPill
import com.kyovo.cents.ui.common.IconTone
import com.kyovo.cents.ui.common.SectionLabel
import com.kyovo.cents.ui.common.SelectDropdown
import com.kyovo.cents.ui.common.SelectOption
import com.kyovo.cents.ui.common.SelectableOptionRow
import com.kyovo.cents.ui.common.formatEuroCents
import com.kyovo.cents.ui.common.formatSignedEuroCents
import com.kyovo.cents.ui.transaction.reactsToTap
import java.time.DayOfWeek
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
    listArchivedAccounts: ListArchivedAccountsUseCase,
    listTransactions: ListTransactionsUseCase,
    listSubcategories: ListSubcategoriesUseCase,
    onTransactionClick: (Transaction) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
)
{
    val palette = if (isSystemInDarkTheme()) DarkAccountsPalette else LightAccountsPalette

    val accounts by remember { listAccounts.observe() }.collectAsStateWithLifecycle(initialValue = emptyList())
    // The account filter offers the active accounts only, but the list still shows the history of
    // archived ones: their names must resolve too, or those rows would lose their account.
    val archivedAccounts by remember { listArchivedAccounts.observe() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val accountsById =
        remember(accounts, archivedAccounts) { (accounts + archivedAccounts).associateBy { it.id } }
    val allTransactions by remember { listTransactions.observe() }.collectAsStateWithLifecycle(initialValue = emptyList())
    // Already ordered by name by the use case.
    val subcategories by remember { listSubcategories.observe() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val subcategoriesById = remember(subcategories) { subcategories.associateBy { it.id } }

    // Filter selection isn't saved across configuration changes: AccountId/SubcategoryId
    // aren't trivially Saveable, and losing a filter on rotation is a minor, acceptable trade-off.
    var selectedPeriod by remember { mutableStateOf(TransactionsPeriod.LAST_30_DAYS) }
    var customFrom by remember { mutableStateOf<LocalDate?>(null) }
    var customTo by remember { mutableStateOf<LocalDate?>(null) }
    var selectedAccountId by remember { mutableStateOf<AccountId?>(null) }
    var chosenSubcategory by remember { mutableStateOf<SubcategoryId?>(null) }
    // One deleted from the management screen meanwhile no longer filters: back to "all".
    val selectedSubcategory = validSubcategoryFilter(chosenSubcategory, subcategories)
    var periodMenuExpanded by remember { mutableStateOf(false) }
    var accountMenuExpanded by remember { mutableStateOf(false) }
    var showCustomRangePicker by remember { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    // Unlike the filters above, which page the user is on (Mouvements/Analyse) is worth keeping across
    // a rotation: being bounced back to Mouvements while looking at Analyse is jarring, not a minor loss.
    var selectedTab by rememberSaveable { mutableStateOf(TransactionsTab.HISTORY) }

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
    val subcategoriesInPeriod = remember(transactionsInPeriod, subcategories) {
        availableSubcategories(transactionsInPeriod, subcategories)
    }
    // The Analyse insights share the account/subcategory filters with Mouvements (moved above the tab
    // switch), so they answer "of what I'm looking at", not always "of everything".
    val topExpensesInPeriod = remember(transactionsInPeriod, selectedAccountId, selectedSubcategory) {
        topExpenses(filterByAccountAndSubcategory(transactionsInPeriod, selectedAccountId, selectedSubcategory))
    }
    // Always the current week, whatever the Mouvements period filter is set to: a recurring expense can
    // generate a transaction weeks or months ahead (see GenerateRecurringExpensesService's lookahead), and
    // a habit chart must not let one of those stand in for a day that hasn't happened yet.
    val (weekStart, weekEnd) = remember { currentWeekRange(LocalDate.now()) }
    val weekdaySpendingThisWeek = remember(allTransactions, weekStart, weekEnd, selectedAccountId, selectedSubcategory) {
        val zone = ZoneId.systemDefault()
        val from = weekStart.atStartOfDay(zone).toInstant()
        val to = weekEnd.plusDays(1).atStartOfDay(zone).toInstant().minusNanos(1)
        val inWeek = transactionsWithinRange(allTransactions, from, to)
        spendingByWeekday(filterByAccountAndSubcategory(inWeek, selectedAccountId, selectedSubcategory))
    }

    val filteredTransactions by remember(selectedAccountId, selectedSubcategory, searchQuery, periodFrom, periodTo) {
        listTransactions.observe(
            accountId = selectedAccountId,
            subcategoryId = selectedSubcategory,
            titleFilter = searchQuery,
            from = periodFrom,
            to = periodTo,
        )
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val groupedByDay = remember(filteredTransactions) { groupByDay(filteredTransactions) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = FAB_CLEARANCE),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HomeTopBar(palette, stringResource(R.string.transactions_title), onOpenSettings)
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp))
        {
            SubcategoryChip(stringResource(R.string.transactions_tab_history), selectedTab == TransactionsTab.HISTORY, palette) {
                selectedTab = TransactionsTab.HISTORY
            }
            SubcategoryChip(stringResource(R.string.transactions_tab_insights), selectedTab == TransactionsTab.INSIGHTS, palette) {
                selectedTab = TransactionsTab.INSIGHTS
            }
        }

        // Shared by both tabs: Analyse answers "of what I'm looking at" too, not always "of everything"
        // (see topExpensesInPeriod/weekdaySpendingThisWeek above).
        AccountFilterRow(
            palette = palette,
            accounts = accounts,
            selectedAccountId = selectedAccountId,
            expanded = accountMenuExpanded,
            onToggleExpanded = { accountMenuExpanded = !accountMenuExpanded },
            onSelect = { accountMenuExpanded = false; selectedAccountId = it },
            movementsCount = filteredTransactions.size,
        )
        SubcategoryFilter(
            palette = palette,
            subcategories = subcategoriesInPeriod,
            selected = selectedSubcategory,
            onSelect = { chosenSubcategory = it },
        )

        when (selectedTab)
        {
            TransactionsTab.HISTORY ->
            {
                StatsRow(
                    palette,
                    expenseCents = totalExpenseCents,
                    incomeCents = totalIncomeCents,
                    netCents = netCents
                )
                SearchField(palette, searchQuery) { searchQuery = it }
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
                            DayGroup(palette, date, dayTransactions, accountsById, subcategoriesById, onTransactionClick)
                        }
                    }
                }
            }

            TransactionsTab.INSIGHTS ->
            {
                if (topExpensesInPeriod.isEmpty() && weekdaySpendingThisWeek.all { it.total.isZero() })
                {
                    Text(
                        text = stringResource(R.string.transactions_insights_empty),
                        color = palette.textMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                } else
                {
                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        SpendingByWeekdaySection(palette, weekdaySpendingThisWeek, weekRangeLabel(weekStart, weekEnd))
                        TopExpensesSection(palette, topExpensesInPeriod, accountsById, subcategoriesById, onTransactionClick)
                    }
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
 * The screen's two tabs, sharing the same period/account/subcategory filters (the period and the tabs
 * above this switch, the account/subcategory filters below it): the raw history (the day-by-day list,
 * scoped to the selected period, never beyond today, plus a search field of its own), and the insights
 * that were crowding it — the biggest expenses (same scope as the history, but never a not-yet-due one;
 * see [topExpenses]) and the weekday pattern (always the current week regardless of the period, but
 * still narrowed by account/subcategory; see [SpendingByWeekdaySection]).
 */
private enum class TransactionsTab
{
    HISTORY,
    INSIGHTS,
}

/**
 * The from/to bounds for a period: preset periods count back from [now] and have no upper bound — a
 * recurring expense can generate a transaction weeks or months ahead (see GenerateRecurringExpensesService's
 * lookahead), and the list shows what is coming as well as what happened. CUSTOM uses the picked dates
 * (start of day to end of day, in the local zone; its picker still can't select a future one), and
 * ALL_TIME has no lower bound.
 */
internal fun periodRange(
    period: TransactionsPeriod,
    customFrom: LocalDate?,
    customTo: LocalDate?,
    now: Instant,
): Pair<Instant?, Instant?>
{
    val zone = ZoneId.systemDefault()
    if (period == TransactionsPeriod.CUSTOM)
    {
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

/** [transactions] narrowed to [accountId] and [subcategoryId] when set (either or both) — the same
 * account/subcategory filters Mouvements uses, shared with the Analyse tab's insights. */
internal fun filterByAccountAndSubcategory(
    transactions: List<Transaction>,
    accountId: AccountId?,
    subcategoryId: SubcategoryId?,
): List<Transaction> =
    transactions.filter {
        (accountId == null || it.accountId == accountId) &&
            (subcategoryId == null || it.subcategoryId == subcategoryId)
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
    val colorScheme = datePickerColorScheme(palette)
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

/**
 * Material 3 color scheme for the date pickers, built from [palette]. The "day in range" fill uses
 * secondaryContainer, not primaryContainer (M3's DatePickerColors default), so both need overriding
 * — otherwise the range highlight stays M3's baseline purple while only the start/end anchor
 * circles pick up the palette color.
 */
internal fun datePickerColorScheme(palette: AccountsPalette): ColorScheme
{
    return lightColorScheme(
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
}

internal fun LocalDate.toEpochMillisUtc(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

internal fun Long.toLocalDateUtc(): LocalDate =
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

/** Subcategory filter: a dropdown rather than chips, since the list grows with the categories in use. */
@Composable
internal fun SubcategoryFilter(
    palette: AccountsPalette,
    subcategories: List<Subcategory>,
    selected: SubcategoryId?,
    onSelect: (SubcategoryId?) -> Unit,
)
{
    val allLabel = stringResource(R.string.transactions_all_subcategories)
    val options = listOf(SelectOption<SubcategoryId?>(null, allLabel)) +
            subcategories.map { SelectOption<SubcategoryId?>(it.id, it.name.value) }
    SelectDropdown(
        palette = palette,
        options = options,
        selected = selected,
        onSelect = onSelect,
        labelPrefix = "🏷️ ",
    )
}

@Composable
internal fun SubcategoryChip(
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
    subcategoriesById: Map<SubcategoryId, Subcategory>,
    onTransactionClick: ((Transaction) -> Unit)? = null,
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
        TransactionListCard(palette, transactions, accountsById, subcategoriesById, onTransactionClick)
    }
}

/**
 * The period's biggest expenses, most expensive first — often what explains a period's total at a
 * glance. Scoped to the same account/subcategory filters as Mouvements, shared above the tab switch:
 * it answers "where did the big spending go" for whatever slice is currently being browsed. Unlike a
 * day's group, these can span many different days, so each row shows its day too, not only its time
 * (see [TransactionListCard]'s `showDate`). Nothing shown when there is no expense at all.
 */
@Composable
internal fun TopExpensesSection(
    palette: AccountsPalette,
    transactions: List<Transaction>,
    accountsById: Map<AccountId, Account>,
    subcategoriesById: Map<SubcategoryId, Subcategory>,
    onTransactionClick: ((Transaction) -> Unit)? = null,
)
{
    if (transactions.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(palette, stringResource(R.string.transactions_top_expenses_title))
        TransactionListCard(palette, transactions, accountsById, subcategoriesById, onTransactionClick, showDate = true)
    }
}

/**
 * Where the money went by day of the week, over the current week (Monday to Sunday) — always that week,
 * whatever the Mouvements period filter is set to: a recurring expense can generate a transaction weeks or
 * months ahead, and a habit chart must not include a day that hasn't happened yet. The busiest day is in
 * the accent colour, the rest a muted context. Nothing shown when the week has no expense at all.
 */
@Composable
internal fun SpendingByWeekdaySection(palette: AccountsPalette, weekdays: List<WeekdaySpending>, weekLabel: String)
{
    if (weekdays.all { it.total.isZero() }) return

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(palette, stringResource(R.string.transactions_weekday_title))
        Text(text = weekLabel, color = palette.textMuted, fontSize = 12.sp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            weekdays.forEach { day -> WeekdayBar(palette, day, Modifier.weight(1f).fillMaxHeight()) }
        }
    }
}

@Composable
private fun WeekdayBar(palette: AccountsPalette, day: WeekdaySpending, modifier: Modifier)
{
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally)
    {
        Text(
            text = formatEuroCents(day.total.value),
            color = if (day.isHighest) palette.textPrimary else palette.textMuted,
            fontSize = 10.sp,
            fontWeight = if (day.isHighest) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
        )
        Spacer(Modifier.height(4.dp))
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter)
        {
            val fraction = day.barFraction.coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    // A day with nothing spent gets a fixed, visible tick rather than a proportional
                    // sliver: next to a much busier day, a couple of percent tall would vanish.
                    .then(if (fraction <= 0f) Modifier.height(4.dp) else Modifier.fillMaxHeight(fraction))
                    .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                    .background(if (day.isHighest) palette.iconToneGreen else palette.divider),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(text = day.label, color = palette.textMuted, fontSize = 11.sp)
    }
}

/**
 * A rounded card of [transactions], one [TransactionRow] each with a hairline divider between them —
 * shared by a day's group (its own header already says which day, so [showDate] stays false there) and
 * [TopExpensesSection] (whose rows can span many different days, so each needs its own, [showDate] true).
 */
@Composable
private fun TransactionListCard(
    palette: AccountsPalette,
    transactions: List<Transaction>,
    accountsById: Map<AccountId, Account>,
    subcategoriesById: Map<SubcategoryId, Subcategory>,
    onTransactionClick: ((Transaction) -> Unit)?,
    showDate: Boolean = false,
)
{
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(palette.surface),
    ) {
        transactions.forEachIndexed { index, transaction ->
            TransactionRow(
                palette = palette,
                transaction = transaction,
                account = accountsById[transaction.accountId],
                subcategory = transaction.subcategoryId?.let(subcategoriesById::get),
                // Only what can be edited reacts to a tap: a transfer doesn't. An opening deposit
                // does, but only for its amount (it opens its own, one-field form).
                onClick = onTransactionClick
                    ?.takeIf { transaction.reactsToTap() }
                    ?.let { open -> { open(transaction) } },
                showDate = showDate,
            )
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

@Composable
private fun TransactionRow(
    palette: AccountsPalette,
    transaction: Transaction,
    account: Account?,
    subcategory: Subcategory?,
    onClick: (() -> Unit)?,
    showDate: Boolean = false,
)
{
    val editLabel = stringResource(R.string.account_edit_action)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(
                    onClickLabel = editLabel,
                    onClick = onClick
                ) else Modifier
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val emoji = subcategory?.emoji?.value ?: categoryEmoji(transaction.category)
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
                subcategory?.name?.value,
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
            Text(
                text = when
                {
                    showDate                                      -> dateTimeLabel(transaction.date)
                    isUpcoming(transaction.date, LocalDate.now()) -> stringResource(R.string.transactions_upcoming)
                    else                                          -> timeLabel(transaction.date)
                },
                color = palette.textMuted,
                fontSize = 11.sp,
                maxLines = 1,
            )
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

/**
 * The [limit] biggest expenses among [transactions], most expensive first — incomes, transfers and the
 * opening deposit don't count as an expense one could overspend on. One dated in the future (a recurring
 * expense generated ahead, see GenerateRecurringExpensesService's lookahead) counts like any other: the
 * insights answer "of what I'm looking at", and the list shows those.
 */
internal fun topExpenses(transactions: List<Transaction>, limit: Int = 5): List<Transaction> =
    transactions
        .filter { it.category == TransactionCategory.EXPENSE }
        .sortedByDescending { it.amount.value }
        .take(limit)

/**
 * One bar of the weekday chart: a day of the week's total expenses, sized as a fraction of the busiest
 * day (1 for it, 0 when nothing was spent on any day). [isHighest] marks that busiest day — the one
 * bar worth calling out; ties keep whichever day comes first, Monday to Sunday.
 */
data class WeekdaySpending(
    val dayOfWeek: DayOfWeek,
    val label: String,
    val total: Money,
    val barFraction: Float,
    val isHighest: Boolean,
)

/** The Monday-to-Sunday week containing [today]. */
internal fun currentWeekRange(today: LocalDate): Pair<LocalDate, LocalDate>
{
    val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    return monday to monday.plusDays(6)
}

/** "22 – 28 sept." (or "28 sept. – 4 oct." across a month boundary), the same "d MMM" shorthand as
 * [periodLabel]'s custom range. */
internal fun weekRangeLabel(monday: LocalDate, sunday: LocalDate): String
{
    val formatter = DateTimeFormatter.ofPattern("d MMM", Locale.FRENCH)
    val start = if (monday.month == sunday.month) monday.dayOfMonth.toString() else monday.format(formatter)
    return "$start – ${sunday.format(formatter)}"
}

/**
 * Expenses grouped by day of the week (Monday first), always all seven — a day with nothing spent is a
 * zero, not an absence. Reveals a habit a plain list doesn't: spending more on weekends, say.
 */
internal fun spendingByWeekday(transactions: List<Transaction>): List<WeekdaySpending>
{
    val zone = ZoneId.systemDefault()
    val totalsByDay = transactions
        .filter { it.category == TransactionCategory.EXPENSE }
        .groupBy { it.date.atZone(zone).dayOfWeek }
        .mapValues { (_, expenses) -> expenses.sumOf { it.amount.value } }

    val max = totalsByDay.values.maxOrNull() ?: 0L
    var highestAlreadyMarked = false

    return DayOfWeek.values().map { day ->
        val cents = totalsByDay[day] ?: 0L
        val isHighest = max > 0L && cents == max && !highestAlreadyMarked
        if (isHighest) highestAlreadyMarked = true

        WeekdaySpending(
            dayOfWeek = day,
            label = day.getDisplayName(java.time.format.TextStyle.SHORT, Locale.FRENCH),
            total = Money(cents),
            barFraction = if (max == 0L) 0f else (cents.toDouble() / max).toFloat(),
            isHighest = isHighest,
        )
    }
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

/**
 * Whether [date] falls on a day after [today] (in [zone]) — a transaction the list marks as "à venir", such
 * as a recurring expense generated ahead (see GenerateRecurringExpensesService's lookahead). By the day, not
 * by the exact instant: one dated later today is due today, and must not read as upcoming until it has gone by.
 */
internal fun isUpcoming(date: Instant, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Boolean =
    date.atZone(zone).toLocalDate().isAfter(today)

/** [dayLabel] and [timeLabel] together — for a row whose neighbours aren't necessarily the same day
 * (the biggest-expenses list), unlike a day's own group where the header already says which day it is. */
@Composable
private fun dateTimeLabel(date: Instant): String
{
    val day = date.atZone(ZoneId.systemDefault()).toLocalDate()
    // An upcoming one says so instead of an hour: a recurring expense generated ahead carries a placeholder
    // time (noon), which would read as a real one.
    val timePart = if (isUpcoming(date, LocalDate.now())) stringResource(R.string.transactions_upcoming) else timeLabel(date)
    return "${dayLabel(day)} · $timePart"
}

private fun timeLabel(date: Instant): String =
    date.atZone(ZoneId.systemDefault()).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))

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
