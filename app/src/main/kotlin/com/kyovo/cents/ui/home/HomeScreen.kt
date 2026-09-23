package com.kyovo.cents.ui.home

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyovo.cents.R
import com.kyovo.cents.domain.port.input.GetAccountBalanceUseCase
import com.kyovo.cents.domain.port.input.ListAccountsUseCase
import com.kyovo.cents.domain.port.input.ListTransactionsUseCase
import kotlinx.coroutines.launch

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
    getAccountBalance: GetAccountBalanceUseCase,
    listTransactions: ListTransactionsUseCase,
    modifier: Modifier = Modifier,
) {
    val palette = if (isSystemInDarkTheme()) DarkAccountsPalette else LightAccountsPalette
    val pagerState = rememberPagerState(pageCount = { HomeTab.entries.size })
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
        ) { page ->
            when (HomeTab.entries[page]) {
                HomeTab.Accounts -> AccountsScreen(listAccounts, getAccountBalance, listTransactions)
                HomeTab.Transactions -> TransactionsPlaceholder(palette)
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

@Composable
private fun TransactionsPlaceholder(palette: AccountsPalette) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = stringResource(R.string.transactions_placeholder), color = palette.textMuted)
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
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        BottomNavItem(
            emoji = "💳",
            label = stringResource(R.string.nav_accounts),
            selected = selected == HomeTab.Accounts,
            palette = palette,
            onClick = { onSelect(HomeTab.Accounts) },
        )
        BottomNavItem(
            emoji = "🧾",
            label = stringResource(R.string.nav_transactions),
            selected = selected == HomeTab.Transactions,
            palette = palette,
            onClick = { onSelect(HomeTab.Transactions) },
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
) {
    val tint = if (selected) palette.statusActiveColor else palette.textMuted
    Column(
        modifier = Modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = emoji, fontSize = 20.sp)
        Text(text = label, color = tint, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}
