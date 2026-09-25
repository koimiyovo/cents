package com.kyovo.cents.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyovo.cents.R
import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.AccountType
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.model.TransactionCategory
import com.kyovo.cents.domain.model.SubcategoryId
import com.kyovo.cents.domain.port.input.GetAccountBalanceUseCase
import com.kyovo.cents.domain.port.input.GetAccountUseCase
import com.kyovo.cents.domain.port.input.ListSubcategoriesUseCase
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
    listSubcategories: ListSubcategoriesUseCase,
    revision: Int,
    onBack: () -> Unit,
    onArchive: () -> Unit,
    onUnarchive: () -> Unit,
    onEdit: () -> Unit,
    onDelete: (deleteTransactions: Boolean) -> Unit,
    onTransactionClick: (Transaction) -> Unit,
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

    val balanceCents =
        remember(accountId, revision) { getAccountBalance.getBalance(accountId)?.value ?: 0L }
    val accountTransactions =
        remember(accountId, revision) { listTransactions.list(accountId = accountId) }
    // Already ordered by name by the use case.
    val subcategories = remember(revision) { listSubcategories.list() }
    val subcategoriesById = remember(subcategories) { subcategories.associateBy { it.id } }

    // Unlike the global tab (30 days by default), an account's page opens on its full history:
    // the opening deposit is often far in the past and would otherwise be hidden at first glance.
    var selectedPeriod by remember { mutableStateOf(TransactionsPeriod.ALL_TIME) }
    var customFrom by remember { mutableStateOf<LocalDate?>(null) }
    var customTo by remember { mutableStateOf<LocalDate?>(null) }
    var selectedSubcategory by remember { mutableStateOf<SubcategoryId?>(null) }
    var periodMenuExpanded by remember { mutableStateOf(false) }
    var showCustomRangePicker by remember { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showArchiveConfirmation by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }

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
    val subcategoriesInPeriod = remember(transactionsInPeriod, subcategories) {
        availableSubcategories(transactionsInPeriod, subcategories)
    }

    val filteredTransactions =
        remember(accountId, revision, selectedSubcategory, searchQuery, periodFrom, periodTo) {
            listTransactions.list(
                accountId = accountId,
                subcategoryId = selectedSubcategory,
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
            Box(modifier = Modifier.weight(1f)) {
                HomeTopBar(palette, account.name.value)
            }
            Spacer(Modifier.width(12.dp))
            // Top right of the screen, where the "more" menu of an app bar is expected.
            AccountActionsMenu(
                palette = palette,
                isArchived = account.archivedAt != null,
                onEdit = onEdit,
                onArchive = { showArchiveConfirmation = true },
                // No confirmation: unarchiving destroys nothing and is undone by archiving again.
                onUnarchive = onUnarchive,
                onDelete = { showDeleteDialog = true },
            )
        }
        AccountSummaryCard(palette = palette, account = account, balanceCents = balanceCents)
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
        SubcategoryFilter(
            palette = palette,
            subcategories = subcategoriesInPeriod,
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
                    DayGroup(
                        palette,
                        date,
                        dayTransactions,
                        accountsById = emptyMap(),
                        subcategoriesById = subcategoriesById,
                        onTransactionClick = onTransactionClick
                    )
                }
            }
        }
    }

    if (showDeleteDialog)
    {
        val deletesTransactions =
            deleteChoices(accountTransactions.size, alreadyArchived = false).deletesTransactions
        DeleteAccountDialog(
            palette = palette,
            accountName = account.name.value,
            transactionCount = accountTransactions.size,
            alreadyArchived = account.archivedAt != null,
            onDelete = {
                showDeleteDialog = false
                onDelete(deletesTransactions)
            },
            // The same archiving as the card's own menu (and the same confirmation-free path the
            // list uses from here: the user has just been through a dialog about this account).
            onArchive = {
                showDeleteDialog = false
                onArchive()
            },
            onDismiss = { showDeleteDialog = false },
        )
    }

    if (showArchiveConfirmation)
    {
        ArchiveConfirmationDialog(
            palette = palette,
            accountName = account.name.value,
            onConfirm = {
                showArchiveConfirmation = false
                onArchive()
            },
            onDismiss = { showArchiveConfirmation = false },
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

@Composable
private fun AccountSummaryCard(
    palette: AccountsPalette,
    account: Account,
    balanceCents: Long,
)
{
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(palette.heroCardBackground)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val isArchived = account.archivedAt != null
        val typeLabel =
            "${accountEmoji(account.type)} ${stringResource(accountTypeLabelRes(account.type))}"
        Text(
            // No "active" badge — that is the normal state; only the exception is spelled out.
            text = if (isArchived) "$typeLabel · ${stringResource(R.string.accounts_status_archived)}" else typeLabel,
            color = palette.heroOnCardSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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

/**
 * The account's actions — edit, archive (or unarchive, for an archived account) and delete — behind
 * a round "three dots" button in the top right corner, next to the back button's twin on the left.
 * A popup menu rather than a row of buttons keeps the header to one line whatever the screen width,
 * and sets the destructive entry apart in the error colour.
 */
@Composable
private fun AccountActionsMenu(
    palette: AccountsPalette,
    isArchived: Boolean,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onUnarchive: () -> Unit,
    onDelete: () -> Unit,
)
{
    var expanded by remember { mutableStateOf(false) }
    val description = stringResource(R.string.account_actions_menu)
    Box {
        // Hand-drawn like the back button: three dots, one above the other.
        Canvas(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(palette.surface)
                .clickable { expanded = true }
                .semantics { contentDescription = description },
        ) {
            val radius = size.width * 0.06f
            listOf(0.3f, 0.5f, 0.7f).forEach { y ->
                drawCircle(palette.textPrimary, radius, Offset(size.width / 2, size.height * y))
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = palette.surface,
        ) {
            ActionMenuItem(stringResource(R.string.account_edit_action), palette.textPrimary) {
                expanded = false
                onEdit()
            }
            if (isArchived)
            {
                ActionMenuItem(
                    stringResource(R.string.account_details_unarchive_button),
                    palette.textPrimary
                ) {
                    expanded = false
                    onUnarchive()
                }
            } else
            {
                ActionMenuItem(
                    stringResource(R.string.account_details_archive_button),
                    palette.textPrimary
                ) {
                    expanded = false
                    onArchive()
                }
            }
            ActionMenuItem(stringResource(R.string.account_delete_action), palette.error) {
                expanded = false
                onDelete()
            }
        }
    }
}

@Composable
private fun ActionMenuItem(label: String, color: Color, onClick: () -> Unit)
{
    DropdownMenuItem(text = { Text(label, color = color, fontSize = 15.sp) }, onClick = onClick)
}

internal fun accountTypeLabelRes(type: AccountType): Int = when (type)
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

/** Explains why an account can't come back: its name is now taken by an active account. */
@Composable
internal fun UnarchiveBlockedDialog(
    palette: AccountsPalette,
    accountName: String,
    onDismiss: () -> Unit,
)
{
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.background,
        titleContentColor = palette.textPrimary,
        textContentColor = palette.textSecondary,
        title = { Text(stringResource(R.string.account_unarchive_error_title)) },
        text = { Text(stringResource(R.string.account_unarchive_error_body, accountName)) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.account_unarchive_error_ok),
                    color = palette.textSecondary,
                )
            }
        },
    )
}

/**
 * Asks before archiving: the app has no way to undo it yet, and the consequences (the account
 * leaves the list, takes no more transactions) deserve a sentence before they happen.
 */
@Composable
internal fun ArchiveConfirmationDialog(
    palette: AccountsPalette,
    accountName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
)
{
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.background,
        titleContentColor = palette.textPrimary,
        textContentColor = palette.textSecondary,
        title = { Text(stringResource(R.string.account_archive_dialog_title)) },
        text = { Text(stringResource(R.string.account_archive_dialog_body, accountName)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.account_archive_dialog_confirm),
                    color = palette.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.account_archive_dialog_cancel),
                    color = palette.textSecondary,
                )
            }
        },
    )
}
