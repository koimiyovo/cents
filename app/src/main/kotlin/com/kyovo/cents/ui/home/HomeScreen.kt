package com.kyovo.cents.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyovo.cents.R
import com.kyovo.cents.data.DataRevision
import com.kyovo.cents.domain.exception.AccountAlreadyArchivedException
import com.kyovo.cents.domain.exception.AccountNotFoundException
import com.kyovo.cents.domain.exception.CannotDeleteAccountWithTransactionsException
import com.kyovo.cents.domain.exception.AccountNotArchivedException
import com.kyovo.cents.domain.exception.DuplicateAccountNameException
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.port.input.ArchiveAccountUseCase
import com.kyovo.cents.domain.port.input.DeleteAccountUseCase
import com.kyovo.cents.domain.port.input.GetAccountBalanceUseCase
import com.kyovo.cents.domain.port.input.GetAccountUseCase
import com.kyovo.cents.domain.port.input.ListAccountsUseCase
import com.kyovo.cents.domain.port.input.ListArchivedAccountsUseCase
import com.kyovo.cents.domain.port.input.ListTransactionsUseCase
import com.kyovo.cents.domain.port.input.ReorderAccountsUseCase
import com.kyovo.cents.domain.port.input.UnarchiveAccountUseCase
import com.kyovo.cents.ui.account.AccountFormSheet
import com.kyovo.cents.ui.account.AccountFormViewModel
import com.kyovo.cents.ui.transaction.AddTransactionFab
import com.kyovo.cents.ui.transaction.DeleteTransactionDialog
import com.kyovo.cents.ui.transaction.InitialDepositFormSheet
import com.kyovo.cents.ui.transaction.InitialDepositFormViewModel
import com.kyovo.cents.ui.transaction.TransactionFormSheet
import com.kyovo.cents.ui.transaction.TransactionFormViewModel
import com.kyovo.cents.ui.transaction.canEditInitialDeposit
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

private enum class HomeTab { Accounts, Transactions }

/**
 * Hosts the app's two bottom-nav destinations. Owns the safe-drawing insets for both: the tab
 * content only needs to worry about being inside this frame, not the status/gesture bars.
 * A HorizontalPager backs the tabs so they can be reached either by tapping the nav bar or by
 * swiping, the same way the onboarding carousel works.
 */
@Composable
fun HomeScreen(
    listAccounts: ListAccountsUseCase,
    listArchivedAccounts: ListArchivedAccountsUseCase,
    archiveAccount: ArchiveAccountUseCase,
    unarchiveAccount: UnarchiveAccountUseCase,
    deleteAccount: DeleteAccountUseCase,
    reorderAccounts: ReorderAccountsUseCase,
    getAccount: GetAccountUseCase,
    getAccountBalance: GetAccountBalanceUseCase,
    listTransactions: ListTransactionsUseCase,
    dataRevision: DataRevision,
    formViewModel: TransactionFormViewModel,
    initialDepositFormViewModel: InitialDepositFormViewModel,
    accountFormViewModel: AccountFormViewModel,
    modifier: Modifier = Modifier,
) {
    val palette = if (isSystemInDarkTheme()) DarkAccountsPalette else LightAccountsPalette
    val pagerState = rememberPagerState(pageCount = { HomeTab.entries.size })
    val coroutineScope = rememberCoroutineScope()
    // Bumped after every write: re-reading on change is how the lists notice a new transaction.
    val revision by dataRevision.value.collectAsStateWithLifecycle()
    val formState by formViewModel.uiState.collectAsStateWithLifecycle()
    val initialDepositFormState by initialDepositFormViewModel.uiState.collectAsStateWithLifecycle()
    val accountFormState by accountFormViewModel.uiState.collectAsStateWithLifecycle()
    val accounts = remember(revision) { listAccounts.list() }
    val archivedAccounts = remember(revision) { listArchivedAccounts.list() }
    // The opened account is kept as its UUID string: AccountId (a value class over kotlin.uuid.Uuid)
    // isn't Saveable, whereas a String is, so the details screen survives rotation.
    var openedAccountUuid by rememberSaveable { mutableStateOf<String?>(null) }
    val openedAccountId = openedAccountUuid?.let { AccountId(Uuid.parse(it)) }

    // A tap on a row goes to the form that fits it: an opening deposit only has its amount to
    // correct, an income or an expense has the full form (a transfer leg never reaches here).
    val openTransaction: (Transaction) -> Unit = { transaction ->
        if (canEditInitialDeposit(transaction)) initialDepositFormViewModel.openForEdit(transaction)
        else formViewModel.openForEdit(transaction)
    }

    // Archiving and unarchiving are idempotent from the user's side: a gesture that fires twice,
    // or on an account that already is in the wanted state, must not crash the app — the wanted
    // state is reached either way, so there is nothing to report.
    val archive: (AccountId) -> Unit = { id ->
        try
        {
            archiveAccount.archive(id)
        } catch (e: AccountAlreadyArchivedException)
        {
            // already archived: nothing to do
        }
        dataRevision.bump()
    }
    val reorder: (List<AccountId>) -> Unit = { ids ->
        try
        {
            reorderAccounts.reorder(ids)
        } catch (e: AccountNotFoundException)
        {
            // An account vanished since the list was read: nothing to reorder, just refresh.
        }
        dataRevision.bump()
    }
    val delete: (AccountId, Boolean) -> Unit = { id, deleteTransactions ->
        try
        {
            deleteAccount.delete(id, deleteTransactions)
        } catch (e: CannotDeleteAccountWithTransactionsException)
        {
            // Transactions appeared after the dialog was built (it said there were none): refuse
            // rather than erase what the user was never told about. The refreshed screens show
            // the account as it is now.
        }
        dataRevision.bump()
    }
    // The name of the account whose unarchiving was refused, while its dialog is up. Refusals
    // come from one rule only: an active account took the name in the meantime. Shared by the
    // details button and the list swipe, hence held here rather than in either screen.
    var unarchiveBlockedName by rememberSaveable { mutableStateOf<String?>(null) }
    val unarchive: (AccountId) -> Unit = { id ->
        try
        {
            unarchiveAccount.unarchive(id)
        } catch (e: AccountNotArchivedException)
        {
            // already active: nothing to do
        } catch (e: DuplicateAccountNameException)
        {
            unarchiveBlockedName = getAccount.get(id)?.name?.value
        }
        dataRevision.bump()
    }

    // Back closes the details screen instead of leaving the app. In the old View system this was
    // onBackPressed() overridden in the Activity; here it's declarative and only active while
    // there is something to close.
    BackHandler(enabled = openedAccountId != null) { openedAccountUuid = null }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        // A simple state-driven switch rather than a Navigation library: there is a single
        // drill-down so far. Worth revisiting (Navigation Compose) once there are more destinations.
        if (openedAccountId != null)
        {
            // An archived account can't receive transactions (domain rule): no button rather than
            // a button leading to an error.
            val canAddTransaction = remember(openedAccountId, revision) {
                getAccount.get(openedAccountId)?.let { it.archivedAt == null } ?: false
            }
            Box(modifier = Modifier.weight(1f)) {
                AccountDetailsScreen(
                    accountId = openedAccountId,
                    getAccount = getAccount,
                    getAccountBalance = getAccountBalance,
                    listTransactions = listTransactions,
                    revision = revision,
                    onBack = { openedAccountUuid = null },
                    // Back to the list afterwards: that is where the account has just moved
                    // (into "Comptes archivés"), and an archived account gets no "+" button.
                    onArchive = {
                        archive(openedAccountId)
                        openedAccountUuid = null
                    },
                    // Stays on the page: the account is active again, so the "+" button reappears.
                    onUnarchive = { unarchive(openedAccountId) },
                    onEdit = { getAccount.get(openedAccountId)?.let(accountFormViewModel::openForEdit) },
                    // The account is gone: back to the list.
                    onDelete = { deleteTransactions ->
                        delete(openedAccountId, deleteTransactions)
                        openedAccountUuid = null
                    },
                    onTransactionClick = openTransaction,
                    modifier = Modifier.fillMaxSize(),
                )
                if (canAddTransaction)
                {
                    AddTransactionFab(
                        onClick = { formViewModel.open(accounts, openedAccountId) },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                    )
                }
            }
        } else
        {
            Box(modifier = Modifier.weight(1f)) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    when (HomeTab.entries[page]) {
                        HomeTab.Accounts -> AccountsScreen(
                            listAccounts,
                            listArchivedAccounts,
                            getAccountBalance,
                            listTransactions,
                            onAccountClick = { openedAccountUuid = it.value.toString() },
                            onNewAccountClick = accountFormViewModel::open,
                            onArchiveAccount = archive,
                            onUnarchiveAccount = unarchive,
                            onEditAccount = accountFormViewModel::openForEdit,
                            onReorderAccounts = reorder,
                            onDeleteAccount = delete,
                            revision = revision,
                        )
                        HomeTab.Transactions ->
                            TransactionsScreen(
                                listAccounts,
                                listArchivedAccounts,
                                listTransactions,
                                onTransactionClick = openTransaction,
                                revision = revision,
                            )
                    }
                }
                AddTransactionFab(
                    onClick = { formViewModel.open(accounts, preselectedAccountId = null) },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                )
            }
            BottomNavBar(
                palette = palette,
                selected = HomeTab.entries[pagerState.currentPage],
                onSelect = { tab ->
                    coroutineScope.launch { pagerState.animateScrollToPage(tab.ordinal) }
                },
            )
        }
    }

    unarchiveBlockedName?.let { name ->
        UnarchiveBlockedDialog(palette = palette, accountName = name, onDismiss = { unarchiveBlockedName = null })
    }

    // Outside the Column: the sheet floats over whichever screen (tabs or account details) is shown.
    formState.form?.let { form ->
        TransactionFormSheet(
            // Archived ones too: an edited transaction may sit on one (the form decides which are offered).
            accounts = accounts + archivedAccounts,
            form = form,
            showErrors = formState.showErrors,
            failure = formState.failure,
            onFormChange = formViewModel::update,
            onSubmit = formViewModel::submit,
            onDelete = formViewModel::askToDelete,
            onDismiss = formViewModel::close,
        )
    }
    formState.confirmingDelete?.let { transaction ->
        DeleteTransactionDialog(
            palette = palette,
            transaction = transaction,
            onConfirm = formViewModel::confirmDelete,
            onDismiss = formViewModel::dismissDeleteConfirmation,
        )
    }
    initialDepositFormState.form?.let { form ->
        InitialDepositFormSheet(
            form = form,
            showErrors = initialDepositFormState.showErrors,
            failure = initialDepositFormState.failure,
            onFormChange = initialDepositFormViewModel::update,
            onSubmit = initialDepositFormViewModel::submit,
            onDismiss = initialDepositFormViewModel::close,
        )
    }
    accountFormState.form?.let { form ->
        AccountFormSheet(
            form = form,
            showErrors = accountFormState.showErrors,
            failure = accountFormState.failure,
            onFormChange = accountFormViewModel::update,
            onSubmit = accountFormViewModel::submit,
            onDismiss = accountFormViewModel::close,
        )
    }
}

@Composable
private fun BottomNavBar(
    palette: AccountsPalette,
    selected: HomeTab,
    onSelect: (HomeTab) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.surface)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BottomNavItem(
            emoji = "💳",
            label = stringResource(R.string.nav_accounts),
            selected = selected == HomeTab.Accounts,
            palette = palette,
            onClick = { onSelect(HomeTab.Accounts) },
            modifier = Modifier.weight(1f),
        )
        BottomNavItem(
            emoji = "🧾",
            label = stringResource(R.string.nav_transactions),
            selected = selected == HomeTab.Transactions,
            palette = palette,
            onClick = { onSelect(HomeTab.Transactions) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun BottomNavItem(
    emoji: String,
    label: String,
    selected: Boolean,
    palette: AccountsPalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Same solid-fill treatment as SubcategoryChip/SelectableOptionRow, so the current tab is
    // unambiguous rather than relying only on a label color change. The clickable area spans the
    // whole item (via the caller's weight(1f)), not just the tight bounds of the emoji + label.
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) palette.iconToneGreen else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = emoji, fontSize = 20.sp)
        Text(
            text = label,
            color = if (selected) palette.heroOnCardPrimary else palette.textMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
