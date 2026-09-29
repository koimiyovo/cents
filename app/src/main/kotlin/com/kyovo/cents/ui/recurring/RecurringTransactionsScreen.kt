package com.kyovo.cents.ui.recurring

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyovo.cents.R
import com.kyovo.cents.domain.port.input.ListAccountsUseCase
import com.kyovo.cents.domain.port.input.ListArchivedAccountsUseCase
import com.kyovo.cents.domain.port.input.ListRecurringTransactionsUseCase
import com.kyovo.cents.domain.port.input.ListSubcategoriesUseCase
import com.kyovo.cents.ui.common.formatEuroCents
import com.kyovo.cents.ui.home.AccountsPalette
import com.kyovo.cents.ui.home.BackButton
import com.kyovo.cents.ui.home.DarkAccountsPalette
import com.kyovo.cents.ui.home.HomeTopBar
import com.kyovo.cents.ui.home.LightAccountsPalette

/**
 * Where the user manages their recurring expenses (rent, subscriptions...): one row per rule with
 * its amount, account and pace ("Tous les mois"...). Touching a row opens its edit form, where it can
 * also be deleted. Archived accounts are still resolved here for the account name — a rule on a
 * closed account is still listed (only generation itself skips it).
 */
@Composable
fun RecurringTransactionsScreen(
    listRecurringTransactions: ListRecurringTransactionsUseCase,
    listAccounts: ListAccountsUseCase,
    listArchivedAccounts: ListArchivedAccountsUseCase,
    listSubcategories: ListSubcategoriesUseCase,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onEdit: (RecurringTransactionRow) -> Unit,
    modifier: Modifier = Modifier,
)
{
    val palette = if (isSystemInDarkTheme()) DarkAccountsPalette else LightAccountsPalette
    val recurringTransactions by remember { listRecurringTransactions.observe() }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val accounts by remember { listAccounts.observe() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val archivedAccounts by remember { listArchivedAccounts.observe() }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val subcategories by remember { listSubcategories.observe() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val rows = remember(recurringTransactions, accounts, archivedAccounts, subcategories) {
        recurringTransactionRows(recurringTransactions, accounts + archivedAccounts, subcategories)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackButton(palette, onBack)
            Spacer(Modifier.width(12.dp))
            HomeTopBar(palette, stringResource(R.string.recurring_transactions_title))
        }
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = stringResource(R.string.recurring_transactions_intro),
                color = palette.textMuted,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            val newDescription = stringResource(R.string.recurring_transactions_new)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(palette.surface)
                    .clickable(onClick = onCreate)
                    .semantics { contentDescription = newDescription },
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "+", color = palette.kicker, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(palette.surface),
        ) {
            rows.forEachIndexed { index, row ->
                RecurringTransactionRowItem(palette, row, onClick = { onEdit(row) })
                if (index != rows.lastIndex)
                {
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(palette.divider))
                }
            }
            if (rows.isEmpty())
            {
                Text(
                    text = stringResource(R.string.recurring_transactions_empty),
                    color = palette.textMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                )
            }
        }
    }
}

@Composable
private fun RecurringTransactionRowItem(palette: AccountsPalette, row: RecurringTransactionRow, onClick: () -> Unit)
{
    val editLabel = stringResource(R.string.recurring_transactions_edit_action)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = editLabel, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(palette.background),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "🔁", fontSize = 18.sp)
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.recurringTransaction.title.value,
                color = palette.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            val pace = recurrenceSummary(row.recurringTransaction.frequency, row.recurringTransaction.interval)
            Text(
                text = "${row.accountName} · $pace",
                color = palette.textMuted,
                fontSize = 13.sp,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = formatEuroCents(row.recurringTransaction.amount.value),
            color = palette.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
