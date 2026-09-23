package com.kyovo.cents.ui.home

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyovo.cents.R
import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.AccountType
import com.kyovo.cents.domain.model.TransactionCategory
import com.kyovo.cents.domain.model.TransactionSubcategory
import com.kyovo.cents.domain.port.input.GetAccountBalanceUseCase
import com.kyovo.cents.domain.port.input.GetAccountUseCase
import com.kyovo.cents.domain.port.input.ListTransactionsUseCase
import com.kyovo.cents.ui.common.formatEuroCents
import java.time.Instant
import java.time.LocalDate

/**
 * Details of a single account: its balance and description, then that account's own transaction
 * history with the same period / subcategory / title-search filters as the global Transactions tab
 * (whose composables are reused here rather than duplicated).
 */
@Composable
fun AccountDetailsScreen(
    accountId: AccountId,
    getAccount: GetAccountUseCase,
    getAccountBalance: GetAccountBalanceUseCase,
    listTransactions: ListTransactionsUseCase,
    revision: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
)
{
    val palette = if (isSystemInDarkTheme()) DarkAccountsPalette else LightAccountsPalette
    val account = remember(accountId, revision) { getAccount.get(accountId) }

    if (account == null)
    {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(palette.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BackButton(palette, onBack)
            Text(
                text = stringResource(R.string.account_details_not_found),
                color = palette.textMuted,
                fontSize = 13.sp,
            )
        }
        return
    }

    val balanceCents = remember(accountId, revision) { getAccountBalance.getBalance(accountId)?.value ?: 0L }
    val accountTransactions = remember(accountId, revision) { listTransactions.list(accountId = accountId) }

    // Unlike the global tab (30 days by default), an account's page opens on its full history:
    // the opening deposit is often far in the past and would otherwise be hidden at first glance.
    var selectedPeriod by remember { mutableStateOf(TransactionsPeriod.ALL_TIME) }
    var customFrom by remember { mutableStateOf<LocalDate?>(null) }
    var customTo by remember { mutableStateOf<LocalDate?>(null) }
    var selectedSubcategory by remember { mutableStateOf<TransactionSubcategory?>(null) }
    var periodMenuExpanded by remember { mutableStateOf(false) }
    var showCustomRangePicker by remember { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    val (periodFrom, periodTo) = remember(selectedPeriod, customFrom, customTo) {
        periodRange(selectedPeriod, customFrom, customTo, Instant.now())
    }
    val transactionsInPeriod = remember(accountTransactions, periodFrom, periodTo) {
        transactionsWithinRange(accountTransactions, periodFrom, periodTo)
    }
    val totalIncomeCents = remember(transactionsInPeriod) {
        transactionsInPeriod.filter { it.category == TransactionCategory.INCOME }
            .sumOf { it.amount.value }
    }
    val totalExpenseCents = remember(transactionsInPeriod) {
        transactionsInPeriod.filter { it.category == TransactionCategory.EXPENSE }
            .sumOf { it.amount.value }
    }
    // Chips only offer subcategories that exist in the period, so a chip never yields an empty list.
    val availableSubcategories = remember(transactionsInPeriod) {
        transactionsInPeriod.mapNotNull { it.subcategory }.distinct()
    }

    val filteredTransactions =
        remember(accountId, revision, selectedSubcategory, searchQuery, periodFrom, periodTo) {
            listTransactions.list(
                accountId = accountId,
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
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = FAB_CLEARANCE),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackButton(palette, onBack)
            Spacer(Modifier.width(12.dp))
            HomeTopBar(palette, account.name.value)
        }
        AccountSummaryCard(palette, account, balanceCents)
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
            netCents = totalIncomeCents - totalExpenseCents,
        )
        SearchField(palette, searchQuery) { searchQuery = it }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.account_details_transactions_title),
                color = palette.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = movementsCountLabel(filteredTransactions.size),
                color = palette.textMuted,
                fontSize = 13.sp,
            )
        }
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
                    // No account lookup: every row is on this account, so repeating its name in
                    // each row's subtitle would just be noise.
                    DayGroup(palette, date, dayTransactions, accountsById = emptyMap())
                }
            }
        }
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

@Composable
private fun AccountSummaryCard(palette: AccountsPalette, account: Account, balanceCents: Long)
{
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(palette.heroCardBackground)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${accountEmoji(account.type)} ${stringResource(accountTypeLabelRes(account.type))}",
                color = palette.heroOnCardSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            val isActive = account.archivedAt == null
            Text(
                text = stringResource(if (isActive) R.string.accounts_status_active else R.string.accounts_status_archived),
                color = if (isActive) palette.heroIncomeAccent else palette.heroOnCardSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Column {
            Text(
                text = stringResource(R.string.account_details_balance_label),
                color = palette.heroOnCardSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = formatEuroCents(balanceCents),
                color = palette.heroOnCardPrimary,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        account.description?.let {
            Text(text = it.value, color = palette.heroOnCardSecondary, fontSize = 13.sp)
        }
    }
}

private fun accountTypeLabelRes(type: AccountType): Int = when (type)
{
    AccountType.CHECKING -> R.string.account_details_type_checking
    AccountType.SAVINGS  -> R.string.account_details_type_savings
    AccountType.CASH     -> R.string.account_details_type_cash
}

/** Round surface button with a hand-drawn left chevron (same reasoning as the dropdown chevron: glyph arrows don't align across fonts). */
@Composable
private fun BackButton(palette: AccountsPalette, onClick: () -> Unit)
{
    val description = stringResource(R.string.account_details_back)
    Canvas(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(palette.surface)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description }
            .padding(12.dp),
    ) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.65f, h * 0.15f)
            lineTo(w * 0.3f, h * 0.5f)
            lineTo(w * 0.65f, h * 0.85f)
        }
        drawPath(
            path,
            color = palette.textPrimary,
            style = Stroke(width = w * 0.16f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}
