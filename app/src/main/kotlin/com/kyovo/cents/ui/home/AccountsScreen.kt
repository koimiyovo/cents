package com.kyovo.cents.ui.home

import androidx.compose.runtime.produceState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.res.Configuration
import androidx.compose.animation.core.animate
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyovo.cents.R
import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.AccountType
import com.kyovo.cents.domain.model.TransactionCategory
import com.kyovo.cents.domain.port.input.GetAccountBalanceUseCase
import com.kyovo.cents.domain.port.input.ListAccountsUseCase
import com.kyovo.cents.domain.port.input.ListArchivedAccountsUseCase
import com.kyovo.cents.domain.port.input.ListTransactionsUseCase
import com.kyovo.cents.ui.common.ChevronDownIcon
import com.kyovo.cents.ui.common.IconTone
import com.kyovo.cents.ui.common.formatEuroCents
import com.kyovo.cents.ui.common.formatSignedEuroCents
import java.time.LocalTime
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Bottom padding of the scrolling screens, so the last item can scroll clear of the floating "+" button. */
internal val FAB_CLEARANCE = 88.dp

@Composable
fun AccountsScreen(
    listAccounts: ListAccountsUseCase,
    listArchivedAccounts: ListArchivedAccountsUseCase,
    getAccountBalance: GetAccountBalanceUseCase,
    listTransactions: ListTransactionsUseCase,
    onAccountClick: (AccountId) -> Unit,
    onNewAccountClick: () -> Unit,
    onArchiveAccount: (AccountId) -> Unit,
    onUnarchiveAccount: (AccountId) -> Unit,
    onEditAccount: (Account) -> Unit,
    onDeleteAccount: (AccountId, Boolean) -> Unit,
    onReorderAccounts: (List<AccountId>) -> Unit,
    onOpenSettings: () -> Unit,
    revision: Int,
    modifier: Modifier = Modifier,
)
{
    val palette = if (isSystemInDarkTheme()) DarkAccountsPalette else LightAccountsPalette
    var balancesVisible by rememberSaveable { mutableStateOf(true) }
    // Landscape gives this screen roughly a third of the vertical space portrait does, so the
    // hero card trims its own padding/spacing/type scale to still clear the bottom nav without
    // needing a scroll just to see the balance.
    val isCompact = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    val accounts by remember { listAccounts.observe() }.collectAsStateWithLifecycle(initialValue = emptyList())
    // The order the user has just dropped the rows in, shown until the saved list catches up: the
    // list re-reads on the next revision, and showing the old order for that one frame would make
    // the dropped row flick back before jumping to its place.
    var droppedOrder by remember { mutableStateOf<List<AccountId>?>(null) }
    val displayedAccounts = remember(accounts, droppedOrder) {
        val order = droppedOrder
        if (order == null) accounts
        else accounts.sortedBy { account -> order.indexOf(account.id).takeIf { it >= 0 } ?: Int.MAX_VALUE }
    }
    val displayedIds = remember(displayedAccounts) { displayedAccounts.map { it.id } }
    // Whatever the saved list turns out to be (the new order, or unchanged if it was refused),
    // it is the truth from then on.
    LaunchedEffect(accounts) { droppedOrder = null }
    val onReorder: (List<AccountId>) -> Unit = { ids ->
        droppedOrder = ids
        onReorderAccounts(ids)
    }
    val scrollState = rememberScrollState()
    val reorder = remember { AccountReorderState() }
    val moveUpLabel = stringResource(R.string.account_move_up)
    val moveDownLabel = stringResource(R.string.account_move_down)
    // The account waiting for the user's yes/no after a swipe, kept as its UUID string (AccountId
    // isn't Saveable) so the question survives a rotation.
    var pendingArchiveId by rememberSaveable { mutableStateOf<String?>(null) }
    // The row whose "Modifier" button is showing (a left swipe leaves it open), by UUID string
    // for the same reason. One at a time: opening a row closes the previous one.
    var revealedId by rememberSaveable { mutableStateOf<String?>(null) }
    val pendingArchive = pendingArchiveId?.let { id -> accounts.firstOrNull { it.id.value.toString() == id } }
    val archivedAccounts by remember { listArchivedAccounts.observe() }.collectAsStateWithLifecycle(initialValue = emptyList())
    // The account whose deletion the user is being asked to confirm: from either list.
    var pendingDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    val pendingDelete = pendingDeleteId?.let { id ->
        (accounts + archivedAccounts).firstOrNull { it.id.value.toString() == id }
    }
    // A balance is computed from the transactions, which are not observed yet: recomputed (suspending)
    // when the accounts or the revision change, the previous figures staying up meanwhile.
    val balanceByAccountId by produceState(emptyMap<AccountId, Long>(), accounts, archivedAccounts, revision) {
        value = (accounts + archivedAccounts).associate { it.id to (getAccountBalance.getBalance(it.id)?.value ?: 0L) }
    }
    // The consolidated figures cover the active accounts only: an archived account is closed, so
    // it counts neither in the total nor in the income/expense lines below.
    val totalCents = remember(accounts, balanceByAccountId) {
        accounts.sumOf { balanceByAccountId[it.id] ?: 0L }
    }
    // The use case only filters by subcategory now, so category-level aggregates are computed
    // here from the full list rather than via a query parameter.
    val allTransactions = remember(revision, accounts) {
        val activeIds = accounts.map { it.id }.toSet()
        listTransactions.list().filter { it.accountId in activeIds }
    }
    val incomeCents = remember(allTransactions) {
        allTransactions.filter { it.category == TransactionCategory.INCOME }
            .sumOf { it.amount.value }
    }
    val expenseCents = remember(allTransactions) {
        allTransactions.filter { it.category == TransactionCategory.EXPENSE }
            .sumOf { it.amount.value }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            // The visible area, for scrolling the list while a row is carried past its edge.
            .onGloballyPositioned {
                val bounds = it.boundsInRoot()
                reorder.viewportTop = bounds.top
                reorder.viewportBottom = bounds.bottom
            }
            .background(palette.background)
            .verticalScroll(scrollState)
            .padding(start = 16.dp, end = 16.dp, top = if (isCompact) 10.dp else 16.dp, bottom = FAB_CLEARANCE),
        verticalArrangement = Arrangement.spacedBy(if (isCompact) 12.dp else 20.dp),
    ) {
        HomeTopBar(palette, stringResource(R.string.accounts_title), onOpenSettings)
        GreetingRow(palette)
        TotalBalanceCard(
            palette = palette,
            totalCents = totalCents,
            incomeCents = incomeCents,
            expenseCents = expenseCents,
            visible = balancesVisible,
            onToggleVisibility = { balancesVisible = !balancesVisible },
            compact = isCompact,
        )
        NewAccountButton(palette, onClick = onNewAccountClick)
        AccountsSectionHeader(palette, count = accounts.size)
        Column(verticalArrangement = Arrangement.spacedBy(ACCOUNT_ROW_SPACING)) {
            displayedAccounts.forEachIndexed { index, account ->
                // Keyed by account: a swipe state belongs to one account, not to a position in the list.
                key(account.id) {
                    val idText = account.id.value.toString()
                    ReorderableRow(
                        state = reorder,
                        ids = displayedIds,
                        index = index,
                        scrollState = scrollState,
                        spacing = ACCOUNT_ROW_SPACING,
                        onReorder = onReorder,
                        onDragStart = { revealedId = null },
                    ) {
                        AccountListRow(
                            account = account,
                            balanceCents = balanceByAccountId[account.id] ?: 0L,
                            visible = balancesVisible,
                            tone = IconTone.entries[index % IconTone.entries.size],
                            palette = palette,
                            archived = false,
                            revealed = revealedId == idText,
                            onRevealedChange = { open -> revealedId = revealedIdAfter(revealedId, idText, open) },
                            onOpen = {
                                // The finger lifting at the end of a drag must not open the account.
                                if (!reorder.clicksSuppressed)
                                {
                                    revealedId = null
                                    onAccountClick(account.id)
                                }
                            },
                            onSwipeRight = { pendingArchiveId = idText },
                            onEdit = { revealedId = null; onEditAccount(account) },
                            onDelete = { revealedId = null; pendingDeleteId = idText },
                            // A row can't be swiped sideways while it is being carried up or down.
                            swipeEnabled = reorder.draggedId == null,
                            // The drag has an accessible equivalent, like the swipes.
                            extraActions = buildList {
                                if (index > 0)
                                {
                                    add(CustomAccessibilityAction(moveUpLabel) {
                                        onReorder(moveItem(displayedIds, index, index - 1)); true
                                    })
                                }
                                if (index < displayedIds.lastIndex)
                                {
                                    add(CustomAccessibilityAction(moveDownLabel) {
                                        onReorder(moveItem(displayedIds, index, index + 1)); true
                                    })
                                }
                            },
                        )
                    }
                }
            }
        }
        if (archivedAccounts.isNotEmpty())
        {
            ArchivedAccountsSection(
                palette = palette,
                accounts = archivedAccounts,
                balanceByAccountId = balanceByAccountId,
                balancesVisible = balancesVisible,
                firstToneIndex = accounts.size,
                onAccountClick = onAccountClick,
                onUnarchiveAccount = onUnarchiveAccount,
                onEditAccount = onEditAccount,
                onDeleteRequest = { pendingDeleteId = it.value.toString() },
                revealedId = revealedId,
                onRevealedIdChange = { revealedId = it },
            )
        }
    }

    if (pendingArchive != null)
    {
        ArchiveConfirmationDialog(
            palette = palette,
            accountName = pendingArchive.name.value,
            onConfirm = {
                pendingArchiveId = null
                onArchiveAccount(pendingArchive.id)
            },
            onDismiss = { pendingArchiveId = null },
        )
    }

    if (pendingDelete != null)
    {
        // Counted when the question is asked (and again if the data changes underneath): it is what
        // the dialog tells the user they are about to erase.
        val transactionCount = remember(pendingDelete.id, revision) {
            listTransactions.list(accountId = pendingDelete.id).size
        }
        DeleteAccountDialog(
            palette = palette,
            accountName = pendingDelete.name.value,
            transactionCount = transactionCount,
            alreadyArchived = pendingDelete.archivedAt != null,
            onDelete = {
                pendingDeleteId = null
                onDeleteAccount(pendingDelete.id, deleteChoices(transactionCount, alreadyArchived = false).deletesTransactions)
            },
            onArchive = {
                pendingDeleteId = null
                onArchiveAccount(pendingDelete.id)
            },
            onDismiss = { pendingDeleteId = null },
        )
    }
}

/**
 * Shared by every Home tab: the "CENTS" kicker plus that tab's own title. The tabs also pass
 * [onSettingsClick] to show the settings button (a gear) at the right; a screen that has its own
 * actions leaves it out.
 */
@Composable
internal fun HomeTopBar(palette: AccountsPalette, title: String, onSettingsClick: (() -> Unit)? = null)
{
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.accounts_kicker),
                color = palette.kicker,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = title,
                color = palette.textPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        if (onSettingsClick != null)
        {
            val description = stringResource(R.string.settings_button_description)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(palette.surface)
                    .clickable(onClick = onSettingsClick)
                    .semantics { contentDescription = description },
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "\u2699\uFE0F", fontSize = 20.sp)
            }
        }
    }
}

/** Bonjour before 6 PM, Bonsoir from then on — computed once per composition, not live-updating. */
internal fun greetingStringRes(now: LocalTime): Int
{
    val hour = now.hour
    return if (hour in 6..17) R.string.accounts_greeting_day else R.string.accounts_greeting_evening
}

@Composable
private fun GreetingRow(palette: AccountsPalette)
{
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = stringResource(greetingStringRes(LocalTime.now())),
                color = palette.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.accounts_greeting_subtitle),
                color = palette.textMuted,
                fontSize = 13.sp,
            )
        }
        Pill(
            text = stringResource(R.string.accounts_vault_badge),
            background = palette.badgeBackground,
            content = palette.badgeContent,
        )
    }
}

@Composable
private fun TotalBalanceCard(
    palette: AccountsPalette,
    totalCents: Long,
    incomeCents: Long,
    expenseCents: Long,
    visible: Boolean,
    onToggleVisibility: () -> Unit,
    compact: Boolean,
)
{
    val hidden = stringResource(R.string.accounts_hidden_balance)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(palette.heroCardBackground)
            .padding(if (compact) 14.dp else 20.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.accounts_hero_label),
                color = palette.heroOnCardSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            EyeToggleIcon(
                visible = visible,
                tint = palette.heroOnCardSecondary,
                modifier = Modifier
                    .clickable(onClick = onToggleVisibility)
                    .padding(4.dp)
                    .size(20.dp),
            )
        }
        Text(
            text = if (visible) formatEuroCents(totalCents) else hidden,
            color = palette.heroOnCardPrimary,
            fontSize = if (compact) 24.sp else 34.sp,
            fontWeight = FontWeight.Bold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HeroStatPill(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.accounts_income_label),
                amount = if (visible) formatSignedEuroCents(incomeCents) else hidden,
                accent = palette.heroIncomeAccent,
                palette = palette,
                compact = compact,
            )
            HeroStatPill(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.accounts_expense_label),
                amount = if (visible) formatSignedEuroCents(-expenseCents) else hidden,
                accent = palette.heroExpenseAccent,
                palette = palette,
                compact = compact,
            )
        }
    }
}

/**
 * A simple hand-drawn eye glyph (almond outline + pupil), struck through when [visible] is false.
 * Drawn rather than using an emoji or a Material icon: there's no single reliable "eye with
 * slash" glyph across fonts, and this keeps the same custom-icon style as the coin mark.
 */
@Composable
private fun EyeToggleIcon(visible: Boolean, tint: Color, modifier: Modifier = Modifier)
{
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val strokeWidth = w * 0.09f
        val eyeOutline = Path().apply {
            moveTo(0f, h / 2f)
            quadraticTo(w / 2f, -h * 0.2f, w, h / 2f)
            quadraticTo(w / 2f, h * 1.2f, 0f, h / 2f)
            close()
        }
        drawPath(
            eyeOutline,
            color = tint,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
        drawCircle(color = tint, radius = h * 0.16f, center = Offset(w / 2f, h / 2f))
        if (!visible)
        {
            drawLine(
                color = tint,
                start = Offset(w * 0.05f, h * 0.95f),
                end = Offset(w * 0.95f, h * 0.05f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun HeroStatPill(
    label: String,
    amount: String,
    accent: Color,
    palette: AccountsPalette,
    compact: Boolean,
    modifier: Modifier = Modifier,
)
{
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(palette.heroPillBackground)
            .padding(if (compact) 8.dp else 12.dp),
    ) {
        Text(text = label, color = accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Text(
            text = amount,
            color = palette.heroOnCardPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun NewAccountButton(palette: AccountsPalette, onClick: () -> Unit)
{
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(palette.primaryButtonBackground)
            .border(1.dp, palette.primaryButtonBorder, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.accounts_new_account_button),
            color = palette.primaryButtonContent,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun AccountsSectionHeader(palette: AccountsPalette, count: Int)
{
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.accounts_section_title),
            color = palette.textPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(palette.badgeBackground)
                .padding(horizontal = 10.dp, vertical = 2.dp),
        ) {
            Text(
                text = count.toString(),
                color = palette.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private const val ACCOUNT_DESCRIPTION_MAX_LENGTH = 40

internal fun accountEmoji(type: AccountType): String = when (type)
{
    AccountType.CHECKING -> "🏦"
    AccountType.SAVINGS  -> "🐷"
    AccountType.CASH     -> "💵"
}

internal fun truncatedDescription(
    value: String,
    maxLength: Int = ACCOUNT_DESCRIPTION_MAX_LENGTH
): String
{
    if (value.length <= maxLength) return value
    return value.take(maxLength - 1).trimEnd() + "…"
}

@Composable
private fun AccountRow(
    account: Account,
    balanceCents: Long,
    visible: Boolean,
    tone: IconTone,
    palette: AccountsPalette,
    onClick: () -> Unit,
    archived: Boolean = false,
)
{
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Archived rows sit in their own collapsed section; toning them down says "closed"
            // without needing a status label on every row.
            .alpha(if (archived) 0.65f else 1f)
            .clip(RoundedCornerShape(18.dp))
            .background(palette.surface)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(toneBackground(tone, palette)),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = accountEmoji(account.type), fontSize = 18.sp)
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = account.name.value,
                color = palette.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            val description = account.description
            if (description != null)
            {
                Text(
                    text = truncatedDescription(description.value),
                    color = palette.textMuted,
                    fontSize = 12.sp,
                )
            }
        }
        val hidden = stringResource(R.string.accounts_hidden_balance)
        Text(
            text = if (visible) formatEuroCents(balanceCents) else hidden,
            color = palette.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** The id to keep as "the row showing its buttons" once [id]'s row asks to open or close. */
internal fun revealedIdAfter(current: String?, id: String, open: Boolean): String?
{
    return if (open) id else current.takeUnless { it == id }
}

/**
 * One account in a list: the row itself, pulled aside by a swipe. Right = [onSwipeRight] (archive
 * or unarchive), left = the "Modifier" and "Supprimer" buttons.
 */
@Composable
private fun AccountListRow(
    account: Account,
    balanceCents: Long,
    visible: Boolean,
    tone: IconTone,
    palette: AccountsPalette,
    archived: Boolean,
    revealed: Boolean,
    onRevealedChange: (Boolean) -> Unit,
    onOpen: () -> Unit,
    onSwipeRight: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    swipeEnabled: Boolean = true,
    extraActions: List<CustomAccessibilityAction> = emptyList(),
)
{
    SwipeableRow(
        palette = palette,
        revealed = revealed,
        onRevealedChange = onRevealedChange,
        rightLabel = stringResource(
            if (archived) R.string.account_details_unarchive_button else R.string.account_details_archive_button,
        ),
        rightEmoji = if (archived) "↩️" else "🗄️",
        onSwipeRight = onSwipeRight,
        onEdit = onEdit,
        onDelete = onDelete,
        swipeEnabled = swipeEnabled,
        extraActions = extraActions,
    ) {
        AccountRow(
            account = account,
            balanceCents = balanceCents,
            visible = visible,
            tone = tone,
            palette = palette,
            // A tap on a row that is showing its buttons only closes them.
            onClick = { if (revealed) onRevealedChange(false) else onOpen() },
            archived = archived,
        )
    }
}

/** The gap between the account rows: the drag arithmetic needs to know it. */
private val ACCOUNT_ROW_SPACING = 12.dp

/**
 * How far a row slides to show its buttons. Icons only ("Modifier", "Supprimer"), so the pair
 * takes little of the row; each label lives in the icon's content description instead.
 */
private val ACTION_BUTTON_WIDTH = 52.dp
private val ACTION_BUTTON_GAP = 16.dp
private val ACTIONS_PANEL_WIDTH = ACTION_BUTTON_WIDTH * 2 + ACTION_BUTTON_GAP
private val ACTIONS_PANEL_GAP = 8.dp

/**
 * A row that can be pulled sideways, in both directions:
 *
 *  - **right**: past a threshold, [onSwipeRight] is called and the row springs back — it never
 *    stays there (the action may open a confirmation, or move the row to the other list);
 *  - **left**: the row slides aside and *stays* open ([revealed]) on its "Modifier" and
 *    "Supprimer" buttons, until it is closed by a tap, by another swipe, or by opening another row.
 *
 * Material's `SwipeToDismissBox` only knows the first behaviour (it dismisses, it can't rest
 * half-open), so this is built on the plain `draggable` modifier: one offset, driven by the finger
 * and then animated to wherever it should rest. Views had `ItemTouchHelper` for the first kind and
 * a custom `ViewDragHelper` (or a library) for the second.
 *
 * A gesture can't be discovered by screen-reader users, hence the custom accessibility actions.
 */
@Composable
private fun SwipeableRow(
    palette: AccountsPalette,
    revealed: Boolean,
    onRevealedChange: (Boolean) -> Unit,
    rightLabel: String,
    rightEmoji: String,
    onSwipeRight: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    swipeEnabled: Boolean = true,
    extraActions: List<CustomAccessibilityAction> = emptyList(),
    content: @Composable () -> Unit,
)
{
    val density = LocalDensity.current
    val revealPx = with(density) { (ACTIONS_PANEL_WIDTH + ACTIONS_PANEL_GAP).toPx() }
    var rowWidthPx by remember { mutableIntStateOf(0) }
    var offsetPx by remember { mutableFloatStateOf(0f) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    // The drag callbacks and the accessibility actions must always call the *current* lambdas.
    val currentOnSwipeRight by rememberUpdatedState(onSwipeRight)
    val currentOnRevealedChange by rememberUpdatedState(onRevealedChange)
    val currentOnEdit by rememberUpdatedState(onEdit)
    val currentOnDelete by rememberUpdatedState(onDelete)

    fun settleTo(target: Float)
    {
        settleJob?.cancel()
        settleJob = scope.launch { animate(offsetPx, target) { value, _ -> offsetPx = value } }
    }

    // Follows the hoisted state when it changes from outside: another row opened, a tap closed it.
    LaunchedEffect(revealed) { settleTo(if (revealed) -revealPx else 0f) }

    val dragState = rememberDraggableState { delta ->
        offsetPx = (offsetPx + delta).coerceIn(-revealPx * 1.1f, rowWidthPx * 0.6f)
    }
    val editLabel = stringResource(R.string.account_edit_action)
    val deleteLabel = stringResource(R.string.account_delete_action)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { rowWidthPx = it.width }
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(rightLabel) { currentOnSwipeRight(); true },
                    CustomAccessibilityAction(editLabel) { currentOnEdit(); true },
                    CustomAccessibilityAction(deleteLabel) { currentOnDelete(); true },
                ) + extraActions
            },
    ) {
        // Behind the row, and only while it is pulled aside: at rest their colour would show
        // through the row's rounded corners.
        if (offsetPx > 0f)
        {
            Row(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(18.dp))
                    .background(palette.iconToneGreen)
                    .padding(start = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "$rightEmoji $rightLabel",
                    color = palette.heroOnCardPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        if (offsetPx < 0f)
        {
            Row(
                modifier = Modifier.matchParentSize(),
                horizontalArrangement = Arrangement.spacedBy(ACTION_BUTTON_GAP, Alignment.End),
            ) {
                SwipeActionButton(palette, Icons.Filled.Edit, editLabel, palette.textPrimary) { currentOnEdit() }
                SwipeActionButton(palette, Icons.Filled.Delete, deleteLabel, palette.error) { currentOnDelete() }
            }
        }
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetPx.roundToInt(), 0) }
                .draggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    enabled = swipeEnabled,
                    onDragStarted = { settleJob?.cancel() },
                    onDragStopped = { velocity ->
                        when
                        {
                            offsetPx > rowWidthPx * SWIPE_RIGHT_THRESHOLD ->
                            {
                                currentOnSwipeRight()
                                currentOnRevealedChange(false)
                                settleTo(0f)
                            }
                            // A quick flick counts as much as a long drag.
                            velocity > FLING_VELOCITY ->
                            {
                                currentOnRevealedChange(false)
                                settleTo(0f)
                            }
                            offsetPx < -revealPx / 2f || (velocity < -FLING_VELOCITY && offsetPx < 0f) ->
                            {
                                currentOnRevealedChange(true)
                                settleTo(-revealPx)
                            }
                            else ->
                            {
                                currentOnRevealedChange(false)
                                settleTo(0f)
                            }
                        }
                    },
                ),
        ) {
            content()
        }
    }
}

/**
 * An icon-only button of the row's left panel. The label is not shown but read out by screen
 * readers (the content description), and the delete one is in the error colour so that the two,
 * side by side, can't be mistaken for each other.
 */
@Composable
private fun SwipeActionButton(
    palette: AccountsPalette,
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
)
{
    Box(
        modifier = Modifier
            .width(ACTION_BUTTON_WIDTH)
            .fillMaxHeight()
            .clip(RoundedCornerShape(18.dp))
            .background(palette.badgeBackground)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(24.dp))
    }
}

/** Share of the row's width a right swipe must cross before it triggers its action. */
private const val SWIPE_RIGHT_THRESHOLD = 0.35f
private const val FLING_VELOCITY = 1200f

/**
 * A single quiet line, "Comptes archivés (N)", that unfolds the archived accounts in place. Kept
 * folded by default and absent when there are none: archived accounts are a rarely-visited
 * history, not something to keep on screen next to the live ones.
 */
@Composable
private fun ArchivedAccountsSection(
    palette: AccountsPalette,
    accounts: List<Account>,
    balanceByAccountId: Map<AccountId, Long>,
    balancesVisible: Boolean,
    firstToneIndex: Int,
    onAccountClick: (AccountId) -> Unit,
    onUnarchiveAccount: (AccountId) -> Unit,
    onEditAccount: (Account) -> Unit,
    onDeleteRequest: (AccountId) -> Unit,
    revealedId: String?,
    onRevealedIdChange: (String?) -> Unit,
)
{
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { expanded = !expanded }
                .padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.accounts_archived_section, accounts.size),
                color = palette.textSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            ChevronDownIcon(
                tint = palette.textSecondary,
                modifier = Modifier
                    .size(14.dp)
                    .rotate(if (expanded) 180f else 0f),
            )
        }
        if (expanded)
        {
            accounts.forEachIndexed { index, account ->
                // No confirmation, unlike archiving: nothing is lost, and archiving again undoes it.
                key(account.id) {
                    val idText = account.id.value.toString()
                    AccountListRow(
                        account = account,
                        balanceCents = balanceByAccountId[account.id] ?: 0L,
                        visible = balancesVisible,
                        tone = IconTone.entries[(firstToneIndex + index) % IconTone.entries.size],
                        palette = palette,
                        archived = true,
                        revealed = revealedId == idText,
                        onRevealedChange = { open -> onRevealedIdChange(revealedIdAfter(revealedId, idText, open)) },
                        onOpen = { onRevealedIdChange(null); onAccountClick(account.id) },
                        onSwipeRight = { onUnarchiveAccount(account.id) },
                        onEdit = { onRevealedIdChange(null); onEditAccount(account) },
                        onDelete = { onRevealedIdChange(null); onDeleteRequest(account.id) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun Pill(text: String, background: Color, content: Color, modifier: Modifier = Modifier)
{
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(text = text, color = content, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

internal fun toneBackground(tone: IconTone, palette: AccountsPalette): Color = when (tone)
{
    IconTone.Green -> palette.iconToneGreen
    IconTone.Gold  -> palette.iconToneGold
    IconTone.Mint  -> palette.iconToneMint
}
