package com.kyovo.cents.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
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
import com.kyovo.cents.R
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.port.input.GetAccountBalanceUseCase
import com.kyovo.cents.domain.port.input.GetAccountUseCase
import com.kyovo.cents.domain.port.input.ListAccountsUseCase
import com.kyovo.cents.domain.port.input.ListTransactionsUseCase
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
    getAccount: GetAccountUseCase,
    getAccountBalance: GetAccountBalanceUseCase,
    listTransactions: ListTransactionsUseCase,
    modifier: Modifier = Modifier,
) {
    val palette = if (isSystemInDarkTheme()) DarkAccountsPalette else LightAccountsPalette
    val pagerState = rememberPagerState(pageCount = { HomeTab.entries.size })
    val coroutineScope = rememberCoroutineScope()
    // The opened account is kept as its UUID string: AccountId (a value class over kotlin.uuid.Uuid)
    // isn't Saveable, whereas a String is, so the details screen survives rotation.
    var openedAccountUuid by rememberSaveable { mutableStateOf<String?>(null) }
    val openedAccountId = openedAccountUuid?.let { AccountId(Uuid.parse(it)) }

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
            AccountDetailsScreen(
                accountId = openedAccountId,
                getAccount = getAccount,
                getAccountBalance = getAccountBalance,
                listTransactions = listTransactions,
                onBack = { openedAccountUuid = null },
                modifier = Modifier.weight(1f),
            )
        } else
        {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { page ->
                when (HomeTab.entries[page]) {
                    HomeTab.Accounts -> AccountsScreen(
                        listAccounts,
                        getAccountBalance,
                        listTransactions,
                        onAccountClick = { openedAccountUuid = it.value.toString() },
                    )
                    HomeTab.Transactions -> TransactionsScreen(listAccounts, listTransactions)
                }
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
